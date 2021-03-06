package org.pillarone.riskanalytics.core.simulation.engine

import org.gridgain.grid.GridTaskFuture
import org.gridgain.grid.typedef.CI1
import org.pillarone.riskanalytics.core.queue.IQueueTaskFuture
import org.pillarone.riskanalytics.core.queue.IQueueTaskListener

class SimulationQueueTaskFuture implements IQueueTaskFuture {

    Map<IQueueTaskListener, CI1<GridTaskFuture>> gridListeners = [:]
    private final GridTaskFuture gridTaskFuture
    private final SimulationQueueTaskContext context

    SimulationQueueTaskFuture(GridTaskFuture gridTaskFuture, SimulationQueueTaskContext context) {
        this.context = context
        this.gridTaskFuture = gridTaskFuture
    }

    @Override
    void stopListenAsync(IQueueTaskListener taskListener) {
        CI1<GridTaskFuture> gridListener = gridListeners.remove(taskListener)
        if (gridListener) {
            gridTaskFuture.stopListenAsync(gridListener)
        }
    }

    @Override
    void listenAsync(IQueueTaskListener uploadTaskListener) {
        TaskListener taskListener = new TaskListener(uploadTaskListener, this)
        gridListeners.put(uploadTaskListener, taskListener)
        gridTaskFuture.listenAsync(taskListener)
    }

    @Override
    void cancel() {
        context.simulationTask.cancel()
        apply()
        gridTaskFuture.cancel()
    }

    private apply() {
        gridListeners.keySet().each { it.apply(this) }
    }

    private static class TaskListener extends CI1<GridTaskFuture> {
        IQueueTaskListener taskListener
        IQueueTaskFuture queueTaskFuture

        TaskListener(IQueueTaskListener taskListener, IQueueTaskFuture future) {
            this.taskListener = taskListener
            this.queueTaskFuture = future
        }

        @Override
        void apply(GridTaskFuture future) {
            taskListener.apply(queueTaskFuture)
        }
    }
}
