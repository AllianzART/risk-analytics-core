package org.pillarone.riskanalytics.core.simulation.engine.grid;

import org.gridgain.grid.GridTaskSplitAdapter;
import org.pillarone.riskanalytics.core.simulation.engine.SimulationConfiguration;
import org.gridgain.grid.GridJob;
import org.gridgain.grid.GridJobResult;

import org.gridgain.grid.GridMessageListener;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.gridgain.grid.Grid;
import org.gridgain.grid.GridNode;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultWriter;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultTransferObject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


public class SimulationTask extends GridTaskSplitAdapter<SimulationConfiguration, Object> implements GridMessageListener {

    private static Log LOG = LogFactory.getLog(SimulationTask.class);

    public static final int SIMULATION_BLOCK_SIZE = 1000;

    private int messageCount = 0;
    private ResultWriter resultWriter;

    protected Collection<? extends GridJob> split(int gridSize, SimulationConfiguration simulationConfiguration) {

        Grid grid = GridHelper.getGrid();
        int cpuCount = getTotalProcessorCount(grid);

        List<SimulationBlock> simulationBlocks = generateBlocks(SIMULATION_BLOCK_SIZE, simulationConfiguration.getSimulation().getNumberOfIterations());

        int maximumBlocksPerNode = new BigDecimal(simulationBlocks.size()).divide(new BigDecimal(cpuCount), RoundingMode.UP).intValue();
        List<SimulationJob> jobs = new ArrayList<SimulationJob>();

        int nextBlockIndex = 0;
        for (int i = 0; i < cpuCount; i++) {
            SimulationConfiguration newConfiguration = simulationConfiguration.clone();
            for (int j = 0; j < maximumBlocksPerNode; j++) {
                if (nextBlockIndex < simulationBlocks.size()) {
                    newConfiguration.addSimulationBlock(simulationBlocks.get(nextBlockIndex));
                    nextBlockIndex++;
                }
            }
            jobs.add(new SimulationJob(newConfiguration, grid.getLocalNode()));
        }

        resultWriter = new ResultWriter((Long) simulationConfiguration.getSimulation().id);
        grid.addMessageListener(this);

        return jobs;
    }

    public Object reduce(List<GridJobResult> gridJobResults) {
        List l = new ArrayList();
        for (GridJobResult res : gridJobResults) {
            l.add(res.getData());
        }
        LOG.info("Received " + messageCount + " messages");

        return l;
    }

    public void onMessage(UUID uuid, Serializable serializable) {
        messageCount++;
        resultWriter.writeResult((ResultTransferObject) serializable);
    }

    private List<SimulationBlock> generateBlocks(int blockSize, int iterations) {
        List<SimulationBlock> simBlocks = new ArrayList<SimulationBlock>();
        int iterationOffset = 0;
        int streamOffset = 0;
        for (int i = blockSize; i < iterations; i += blockSize) {
            simBlocks.add(new SimulationBlock(iterationOffset, blockSize, streamOffset++));
            iterationOffset += blockSize;
        }
        simBlocks.add(new SimulationBlock(iterationOffset, iterations - streamOffset * blockSize, streamOffset));
        return simBlocks;
    }

    private int getTotalProcessorCount(Grid grid) {
        Collection<GridNode> nodes = grid.getAllNodes();
        List<String> usedHosts = new ArrayList<String>();
        int processorCount = 0;
        for (GridNode node : nodes) {
            if (!usedHosts.contains(node.getPhysicalAddress())) {
                processorCount += node.getMetrics().getAvailableProcessors();
                usedHosts.add(node.getPhysicalAddress());
            }
        }
        LOG.info("Found " + processorCount + " CPUs on " + nodes.size() + " nodes");
        return processorCount;
    }
}