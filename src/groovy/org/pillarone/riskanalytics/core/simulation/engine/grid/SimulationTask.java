package org.pillarone.riskanalytics.core.simulation.engine.grid;

import grails.plugin.springsecurity.SpringSecurityUtils;
import grails.util.Holders;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskSplitAdapter;
import org.apache.log4j.MDC;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.cli.ImportStructureInTransaction;
import org.pillarone.riskanalytics.core.components.DataSourceDefinition;
import org.pillarone.riskanalytics.core.output.Calculator;
import org.pillarone.riskanalytics.core.output.PathMapping;
import org.pillarone.riskanalytics.core.output.aggregation.PacketAggregatorRegistry;
import org.pillarone.riskanalytics.core.parameterization.ParameterizationHelper;
import org.pillarone.riskanalytics.core.simulation.SimulationState;
import org.pillarone.riskanalytics.core.simulation.engine.ResultData;
import org.pillarone.riskanalytics.core.simulation.engine.SimulationConfiguration;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.JobResult;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultDescriptor;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultTransferObject;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultWriter;
import org.pillarone.riskanalytics.core.simulation.item.Resource;
import org.pillarone.riskanalytics.core.simulation.item.Simulation;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;


public class SimulationTask extends ComputeTaskSplitAdapter<SimulationConfiguration, Boolean> {

    private static Log LOG = LogFactory.getLog(SimulationTask.class);

    public static final int MESSAGE_TIMEOUT = 80000;

    private AtomicInteger messageCount = new AtomicInteger(0);
    private ResultWriter resultWriter;

    private SimulationConfiguration simulationConfiguration;
    private SimulationState currentState = SimulationState.NOT_RUNNING;
    private List<Throwable> simulationErrors = new LinkedList<Throwable>();
    private Map<UUID, Integer> progress = new HashMap<UUID, Integer>();
    private Calculator calculator;

    private long time;
    private int totalJobs = 0;

    private boolean cancelled;

    private List<UUID> jobIds = new ArrayList<UUID>();
    private ResultTransferListener resultTransferListener;

    /**
     * Splits the received SimulationConfiguration in blocks, creates a child job for each blocks, and sends
     * these simulation blocks to other nodes for processing.
     *
     * @param clusterSize Number of available cluster nodes. Note that returned number of
     *      jobs can be less, equal or greater than this cluster size.
     * @param simulationConfiguration Task execution argument. Can be {@code null}.
     * @return The list of child jobs.
     */
    @Override
    protected Collection<? extends ComputeJob> split(int clusterSize, SimulationConfiguration simulationConfiguration) throws IgniteException {
        if (clusterSize < 1) {
            throw new IllegalStateException("No grid gain nodes found! Contact support.");
        }
        try {
            this.simulationConfiguration = simulationConfiguration;
            initMDCForLoggingAndLogInUser();

            if (!cancelled) {
                setSimulationState(SimulationState.INITIALIZING);
            }
            time = System.currentTimeMillis();
            DateTime start = new DateTime();

            //this was done originally before sending the configuration to the grid.
            //if something does not work, we can move it back or remove the comment
            ImportStructureInTransaction.importStructure(simulationConfiguration);
            simulationConfiguration.createMappingCache(simulationConfiguration.getSimulation().getTemplate());
            simulationConfiguration.prepareSimulationForGrid();
            simulationConfiguration.setBeans(SpringBeanDefinitionRegistry.getRequiredBeanDefinitions());
            simulationConfiguration.getSimulation().setStart(start);

            List<DataSourceDefinition> dataSourceDefinitions = ParameterizationHelper.collectDataSourceDefinitions(simulationConfiguration.getSimulation().getParameterization().getParameters());
            ResultData dataSource = new ResultData();
            dataSource.load(dataSourceDefinitions, simulationConfiguration.getSimulation());
            simulationConfiguration.setResultDataSource(dataSource);

            final Ignite ignite = Holders.getGrailsApplication().getMainContext().getBean("ignite", Ignite.class);
            final UUID headNodeId = ignite.cluster().localNode().id();

            List<SimulationBlock> simulationBlocks = generateBlocks(simulationConfiguration.getSimulation().getNumberOfIterations(), clusterSize);

            LOG.info("Generated " + simulationBlocks.size() + " blocks; Sim=" + simulationConfiguration.getSimulation().getName());
            List<SimulationJob> jobs = new ArrayList<SimulationJob>();

            List<Resource> allResources = ParameterizationHelper.collectUsedResources(simulationConfiguration.getSimulation().getRuntimeParameters());
            allResources.addAll(ParameterizationHelper.collectUsedResources(simulationConfiguration.getSimulation().getParameterization().getParameters()));

            for (Resource resource : allResources) {
                resource.load();
            }

            for (int i = 0; i < simulationBlocks.size(); i++) {
                SimulationConfiguration newConfiguration = simulationConfiguration.clone();
                newConfiguration.addSimulationBlock(simulationBlocks.get(i));

                UUID jobId = UUID.randomUUID();
                SimulationJob job = new SimulationJob(newConfiguration, jobId, headNodeId);
                job.setAggregatorMap(PacketAggregatorRegistry.getAllAggregators());
                job.setLoadedResources(allResources);
                jobIds.add(jobId);
                jobs.add(job);
                LOG.info("Created a new job with block count " + newConfiguration.getSimulationBlocks().size());
            }

            resultWriter = new ResultWriter(simulationConfiguration.getSimulation().getId());
            resultTransferListener = new ResultTransferListener(this);
            IgniteMessaging message = ignite.message();
            message.localListen("dataSendTopic", resultTransferListener);

            if (!cancelled) {
                setSimulationState(SimulationState.RUNNING);
            }
            totalJobs = jobs.size();

            simulationConfiguration.getSimulation().save();
            return jobs;
        } catch (Exception e) {
            getSimulation().delete();
            simulationErrors.add(e);
            if (!cancelled) {
                setSimulationState(SimulationState.ERROR);
            }
            LOG.error("Error setting up simulation task.", e);
            throw new RuntimeException(e);
        }
    }

    private void initMDCForLoggingAndLogInUser() {
        String username = simulationConfiguration.getUsername();
        if (username != null) {
            SpringSecurityUtils.reauthenticate(username, null);
            MDC.put("username", username);
        }
        MDC.put("simulation", simulationConfiguration.getSimulation().getParameterization().getNameAndVersion());
    }

    @Override
    public Boolean reduce(List<ComputeJobResult> gridJobResults) throws IgniteException {
        try {
            initMDCForLoggingAndLogInUser();
            int totalMessageCount = 0;
            int periodCount = 1;
            int completedIterations = 0;
            boolean error = false;
            for (ComputeJobResult res : gridJobResults) {
                JobResult jobResult = res.getData();
                periodCount = jobResult.getNumberOfSimulatedPeriods();
                totalMessageCount += jobResult.getTotalMessagesSent();
                completedIterations += jobResult.getCompletedIterations();

                LOG.info("Job " + jobResult.getNodeName() + " executed in " + (jobResult.getEnd().getTime() - jobResult.getStart().getTime()) + " ms");
                Throwable simulationException = jobResult.getSimulationException();
                if (simulationException != null) {
                    LOG.error("Error in job " + jobResult.getNodeName(), simulationException);
                    simulationErrors.add(simulationException);
                    error = true;
                }
            }
            Simulation simulation = simulationConfiguration.getSimulation();

            synchronized (this) {
                LOG.info("Waiting for " + totalMessageCount + " result messages.");
                while (messageCount.get() < totalMessageCount) {
                    long timeout = System.currentTimeMillis();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Not all messages received yet - waiting");
                    }
                    try {
                        wait(MESSAGE_TIMEOUT);
                    } catch (InterruptedException e) {
                        error = true;
                        simulationErrors.add(e);
                        // Restore the interrupted status - http://www.ibm.com/developerworks/library/j-jtp05236/
                        Thread.currentThread().interrupt();
                        break;
                    }
                    LOG.debug("got new message. messageCount: " + messageCount.get() + "/totalMessageCount: " + totalMessageCount);
                    if (System.currentTimeMillis() - timeout > MESSAGE_TIMEOUT) {
                        error = true;
                        simulationErrors.add(new TimeoutException("Not all messages received - timeout reached"));
                        break;
                    }
                }
            }
            resultWriter.close();

            Ignite ignite = Holders.getGrailsApplication().getMainContext().getBean("ignite", Ignite.class);
            IgniteMessaging message = ignite.message();
            message.stopLocalListen("dataSendTopic", resultTransferListener);

            if (error || cancelled) {
                simulation.delete();
                if (!cancelled) {
                    setSimulationState(SimulationState.ERROR);
                }
                return false;
            }
            LOG.info("Received " + messageCount + " messages. Sent " + totalMessageCount + " messages.");
            calculator = new Calculator(simulation);
            setSimulationState(SimulationState.POST_SIMULATION_CALCULATIONS);
            calculator.calculate();
            if (cancelled) {
                simulation.delete();
                return false;
            }
            setSimulationState(SimulationState.FINISHED);
            simulation.setEnd(new DateTime());
            simulation.setNumberOfIterations(completedIterations);
            simulation.setPeriodCount(periodCount);
            simulation.save();
            LOG.info("Task completed in " + (System.currentTimeMillis() - time) + "ms");
            return true;
        } catch (Exception e) {
            getSimulation().delete();
            simulationErrors.add(e);
            if (!cancelled) {
                setSimulationState(SimulationState.ERROR);
            }
            LOG.error("Error reducing simulation task.", e);
            throw new RuntimeException(e);
        }

    }

    public synchronized void writeResult(ResultTransferObject result) {
        LOG.debug("got result from resultTransferListener: " + result.getProgress() + "Will now write result ....");
        long before = System.currentTimeMillis();
        if (!jobIds.contains(result.getJobIdentifier())) {
            return;
        }
        messageCount.incrementAndGet();
        ResultDescriptor rd = result.getResultDescriptor();
        //TODO: should be done before simulation start
        PathMapping pm = simulationConfiguration.getMappingCache().lookupPath(rd.getPath());
        rd.setPathId(pm.pathID());
        resultWriter.writeResult(result);
        long diff = System.currentTimeMillis() - before;
        LOG.debug("wrote result in " + diff + " ms");
        progress.put(result.getJobIdentifier(), result.getProgress());
        notify();
    }

    public SimulationState getSimulationState() {
        return currentState;
    }

    public synchronized void setSimulationState(SimulationState simulationState) {
        this.currentState = simulationState;
        getSimulation().setSimulationState(currentState);
    }

    public Simulation getSimulation() {
        return simulationConfiguration.getSimulation();
    }

    public SimulationConfiguration getSimulationConfiguration() {
        return simulationConfiguration;
    }

    public synchronized List<Throwable> getSimulationErrors() {
        return simulationErrors;
    }

    public synchronized void cancel() {
        cancelled = true;
        if (currentState == SimulationState.POST_SIMULATION_CALCULATIONS) {
            calculator.setStopped(true);
        }
        setSimulationState(SimulationState.CANCELED);
    }

    public synchronized int getProgress() {
        if (!(currentState == SimulationState.POST_SIMULATION_CALCULATIONS)) {
            if (progress.isEmpty()) {
                return 0;
            }
            int sum = 0;
            for (Integer value : progress.values()) {
                sum += value;
            }
            return sum / totalJobs;
        } else {
            return calculator.getProgress();
        }
    }

    public synchronized DateTime getEstimatedSimulationEnd() {
        int progress = getProgress();
        if (progress > 0 && currentState == SimulationState.RUNNING) {
            long now = System.currentTimeMillis();
            long onePercentTime = (now - time) / progress;
            long estimatedEnd = now + (onePercentTime * (100 - progress));
            return new DateTime(estimatedEnd);
        } else if (currentState == SimulationState.POST_SIMULATION_CALCULATIONS) {
            return calculator.getEstimatedEnd();
        }
        return null;
    }

    protected List<SimulationBlock> generateBlocks(int iterations, int clusterSize) {
        int blockSize = iterations / clusterSize;
        int rest = iterations % clusterSize;
        List<SimulationBlock> simBlocks = new ArrayList<SimulationBlock>();
        int iterationOffset = 0;
        int streamOffset = 0;

        final int blockSizeAndRest = blockSize + rest;
        simBlocks.add(new SimulationBlock(iterationOffset, blockSizeAndRest, streamOffset));
        iterationOffset += blockSizeAndRest;
        streamOffset = nextStreamOffset(streamOffset);

        for (int i = blockSizeAndRest; i < iterations; i += blockSize) {
            simBlocks.add(new SimulationBlock(iterationOffset, blockSize, streamOffset));
            iterationOffset += blockSize;
            streamOffset = nextStreamOffset(streamOffset);
        }
        return simBlocks;
    }

    protected int nextStreamOffset(int currentOffset) {
        currentOffset++;
        //first ten of each block of hundred substreams are reserved for business logic
        while ((currentOffset >= 100 && currentOffset % 100 < 10)) {
            currentOffset++;
        }
        return currentOffset;
    }

}
