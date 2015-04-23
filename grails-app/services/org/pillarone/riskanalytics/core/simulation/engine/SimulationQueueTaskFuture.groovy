package org.pillarone.riskanalytics.core.simulation.engine

import org.apache.ignite.compute.ComputeTaskFuture
import org.apache.ignite.internal.util.typedef.CI1
import org.pillarone.riskanalytics.core.queue.IQueueTaskFuture
import org.pillarone.riskanalytics.core.queue.IQueueTaskListener

class SimulationQueueTaskFuture implements IQueueTaskFuture {

    Map<IQueueTaskListener, CI1<ComputeTaskFuture>> gridListeners = [:]
    private final ComputeTaskFuture gridTaskFuture
    private final SimulationQueueTaskContext context

    SimulationQueueTaskFuture(ComputeTaskFuture gridTaskFuture, SimulationQueueTaskContext context) {
        this.context = context
        this.gridTaskFuture = gridTaskFuture
    }

    @Override
    void stopListen(IQueueTaskListener taskListener) {
        gridListeners.remove(taskListener)
    }

    @Override
    void listen(IQueueTaskListener uploadTaskListener) {
        TaskListener taskListener = new TaskListener(uploadTaskListener, this)
        gridListeners.put(uploadTaskListener, taskListener)
        gridTaskFuture.listen(taskListener)
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

    private static class TaskListener implements CI1<ComputeTaskFuture> {
        IQueueTaskListener taskListener
        IQueueTaskFuture queueTaskFuture

        TaskListener(IQueueTaskListener taskListener, IQueueTaskFuture future) {
            this.taskListener = taskListener
            this.queueTaskFuture = future
        }

        @Override
        void apply(ComputeTaskFuture future) {
            taskListener.apply(queueTaskFuture)
        }
    }
}
