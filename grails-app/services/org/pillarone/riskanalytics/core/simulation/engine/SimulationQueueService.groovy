package org.pillarone.riskanalytics.core.simulation.engine

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.ignite.Ignite
import org.apache.ignite.IgniteCompute
import org.apache.ignite.cluster.ClusterGroup
import org.apache.ignite.compute.ComputeTaskFuture
import org.pillarone.riskanalytics.core.queue.AbstractQueueService
import org.pillarone.riskanalytics.core.queue.IQueueTaskFuture
import org.pillarone.riskanalytics.core.simulation.SimulationState
import org.pillarone.riskanalytics.core.simulation.engine.grid.mapping.AbstractNodeMappingStrategy

class SimulationQueueService extends AbstractQueueService<SimulationConfiguration, SimulationQueueEntry> {
    private static final Log LOG = LogFactory.getLog(SimulationQueueService)

    Ignite ignite

    @Override
    SimulationQueueEntry createQueueEntry(SimulationConfiguration configuration, int priority) {
        new SimulationQueueEntry(configuration, priority)
    }

    @Override
    SimulationQueueEntry createQueueEntry(UUID id) {
        new SimulationQueueEntry(id)
    }

    @Override
    void preConditionCheck(SimulationConfiguration configuration) {
        //TODO discuss, what has to be fulfilled
        Long id = configuration?.simulation?.id
        if (!id) {
            throw new IllegalStateException('simulation must be persistent before putting it on the queue')
        }
    }

    @Override
    IQueueTaskFuture doWork(SimulationQueueEntry entry, int priority) {
        SimulationQueueTaskContext context = entry.context
        ClusterGroup clusterGroup = AbstractNodeMappingStrategy.getStrategy().getUsableNodes(ignite)
        def numNodes = clusterGroup.nodes().size()
        LOG.info("Found $numNodes nodes in grid")
        if (numNodes < 1){
            LOG.error("No grid nodes available.")
            context.simulationTask.simulationErrors.add(new IllegalStateException("No grid nodes available. Please contact support."))
            context.simulationTask.simulationState = SimulationState.ERROR
        }
        IgniteCompute compute = ignite.compute(clusterGroup).withAsync()
        compute.execute(context.simulationTask, context.simulationTask.simulationConfiguration)
        ComputeTaskFuture future = compute.future()
        return new SimulationQueueTaskFuture(future, context)
    }

    @Override
    void handleEntry(SimulationQueueEntry entry) {
        SimulationQueueTaskContext context = entry.context
        SimulationState simulationState = context.simulationTask.simulationState
        switch (simulationState) {
            case SimulationState.FINISHED:
            case SimulationState.ERROR:
            case SimulationState.CANCELED:
                break
            case SimulationState.NOT_RUNNING:
                // TODO How can we learn that no grid nodes were available here ???
                log.error("Sim never started. Maybe no usable nodes in grid?")
            case SimulationState.INITIALIZING:
            case SimulationState.RUNNING:
            case SimulationState.SAVING_RESULTS:
            case SimulationState.POST_SIMULATION_CALCULATIONS:
            default:
                log.error("task has finished, but state was $simulationState. This is likely to an internal IGNITE error")
                context.simulationTask.simulationErrors.add(new Throwable("internal IGNITE error"))
                context.simulationTask.simulationState = SimulationState.ERROR
        }
    }
}
