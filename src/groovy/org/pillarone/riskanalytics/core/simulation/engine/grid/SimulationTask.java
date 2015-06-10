package org.pillarone.riskanalytics.core.simulation.engine.grid;

import grails.plugin.springsecurity.SpringSecurityUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.log4j.MDC;
import org.jetbrains.annotations.Nullable;
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


public class SimulationTask extends ComputeTaskAdapter<SimulationConfiguration, Boolean> {

    public static final String DATA_SEND_TOPIC = "dataSendTopic";
    private static Log LOG = LogFactory.getLog(SimulationTask.class);

    public static final int SIMULATION_BLOCK_SIZE = 1000;
    public static final int MESSAGE_TIMEOUT = 60000;

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
     * This method is called to map or split grid task into multiple grid jobs. This is the
     * first method that gets called when task execution starts.
     *
     * @param simulationConfiguration Task execution argument. Can be {@code null}. This is the same argument
     *      as the one passed into {@code Grid#execute(...)} methods.
     * @param subgrid Nodes available for this task execution. Note that order of nodes is
     *      guaranteed to be randomized by container. This ensures that every time
     *      you simply iterate through grid nodes, the order of nodes will be random which
     *      over time should result into all nodes being used equally.
     * @return Map of grid jobs assigned to subgrid node. Unless {@link org.apache.ignite.compute.ComputeTaskContinuousMapper} is
     *      injected into task, if {@code null} or empty map is returned, exception will be thrown.
     * @throws IgniteException If mapping could not complete successfully. This exception will be
     *      thrown out of {@link org.apache.ignite.compute.ComputeTaskFuture#get()} method.
     */
    @Nullable
    @Override
    public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, SimulationConfiguration simulationConfiguration) throws IgniteException {

        if (subgrid == null || subgrid.isEmpty()) {
            throw new IllegalStateException("No grid gain nodes found! Contact support.");
        }
        LOG.info("Splitting work amongst " + subgrid.size() + " nodes in cluster.");
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

            final Ignite ignite = GridHelper.getGrid();
            final UUID headNodeId = ignite.cluster().localNode().id();

            List<SimulationBlock> simulationBlocks = generateBlocks(simulationConfiguration.getSimulation().getNumberOfIterations());

            LOG.info("Generated " + simulationBlocks.size() + " blocks; Sim=" + simulationConfiguration.getSimulation().getName());

            List<Resource> allResources = ParameterizationHelper.collectUsedResources(simulationConfiguration.getSimulation().getRuntimeParameters());
            allResources.addAll(ParameterizationHelper.collectUsedResources(simulationConfiguration.getSimulation().getParameterization().getParameters()));

            for (Resource resource : allResources) {
                resource.load();
            }

            Map<SimulationJob, ClusterNode> jobToNode = new HashMap<SimulationJob, ClusterNode>();
            Map<ClusterNode, Integer> nodeToJobCount = new HashMap<ClusterNode, Integer>();

            for (int i = 0; i < simulationBlocks.size(); i++) {
                SimulationConfiguration newConfiguration = simulationConfiguration.clone();
                newConfiguration.addSimulationBlock(simulationBlocks.get(i));

                UUID jobId = UUID.randomUUID();
                SimulationJob job = new SimulationJob(newConfiguration, jobId, headNodeId);
                job.setAggregatorMap(PacketAggregatorRegistry.getAllAggregators());
                job.setLoadedResources(allResources);
                final int nodeNumber = i % subgrid.size();
                final ClusterNode node = subgrid.get(nodeNumber);
                jobToNode.put(job, node);
                if(nodeToJobCount.containsKey(node)) {
                    final Integer jobCount = nodeToJobCount.get(node);
                    nodeToJobCount.put(node, jobCount + 1);
                } else {
                    nodeToJobCount.put(node, 1);
                }
                jobIds.add(jobId);
                totalJobs++;
                LOG.info("Created a new job with block count " + newConfiguration.getSimulationBlocks().size());
            }
            for (Map.Entry<SimulationJob, ClusterNode> simulationJobClusterNodeEntry : jobToNode.entrySet()) {
                final Integer jobCount = nodeToJobCount.get(simulationJobClusterNodeEntry.getValue());
                simulationJobClusterNodeEntry.getKey().setJobCount(jobCount);
            }

            resultWriter = new ResultWriter(simulationConfiguration.getSimulation().getId());
            resultTransferListener = new ResultTransferListener(this);
            IgniteMessaging message = ignite.message();
            message.localListen(DATA_SEND_TOPIC, resultTransferListener);

            if (!cancelled) {
                setSimulationState(SimulationState.RUNNING);
            }

            simulationConfiguration.getSimulation().save();

            return jobToNode;
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

    /**
     * Reduces (or aggregates) results received so far into one compound result to be returned to
     * caller via {@link org.apache.ignite.compute.ComputeTaskFuture#get()} method.
     * <p>
     * Note, that if some jobs did not succeed and could not be failed over then the list of
     * results passed into this method will include the failed results. Otherwise, failed
     * results will not be in the list.
     *
     * @param gridJobResults Received results of broadcasted remote executions. Note that if task class has
     *      {@link org.apache.ignite.compute.ComputeTaskNoResultCache} annotation, then this list will be empty.
     * @return Grid job result constructed from results of remote executions.
     * @throws IgniteException If reduction or results caused an error. This exception will
     *      be thrown out of {@link org.apache.ignite.compute.ComputeTaskFuture#get()} method.
     */
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
                    LOG.info("got new message. messageCount: " + messageCount.get() + "/totalMessageCount: " + totalMessageCount);
                    if (System.currentTimeMillis() - timeout > MESSAGE_TIMEOUT) {
                        error = true;
                        simulationErrors.add(new TimeoutException("Not all messages received - timeout reached"));
                        break;
                    }
                }
            }
            resultWriter.close();

            Ignite ignite = GridHelper.getGrid();
            IgniteMessaging message = ignite.message();
            message.stopLocalListen(DATA_SEND_TOPIC, resultTransferListener);

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

    protected List<SimulationBlock> generateBlocks(final int iterations) {
        final int numberOfFullBlocks = iterations / SIMULATION_BLOCK_SIZE;
        final int rest = iterations % SIMULATION_BLOCK_SIZE;
        List<SimulationBlock> simBlocks = new ArrayList<SimulationBlock>();
        int streamOffset = 0;
        int iterationOffset = 0;

        for (int i = 0; i < numberOfFullBlocks; i++) {
            simBlocks.add(new SimulationBlock(iterationOffset, SIMULATION_BLOCK_SIZE, streamOffset));
            iterationOffset += SIMULATION_BLOCK_SIZE;
            streamOffset = nextStreamOffset(streamOffset);
        }
        if (rest != 0) {
            simBlocks.add(new SimulationBlock(iterationOffset, rest, streamOffset));
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
