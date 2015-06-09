package org.pillarone.riskanalytics.core.simulation.engine

import org.apache.ignite.compute.ComputeTaskFuture
import org.apache.ignite.lang.IgniteInClosure
import org.pillarone.riskanalytics.core.queue.IQueueTaskFuture
import org.pillarone.riskanalytics.core.queue.IQueueTaskListener

class SimulationQueueTaskFuture implements IQueueTaskFuture {

    Map<IQueueTaskListener, IgniteInClosure<ComputeTaskFuture<Boolean>>> gridListeners = [:]
    private final ComputeTaskFuture<Boolean> gridTaskFuture
    private final SimulationQueueTaskContext context

    SimulationQueueTaskFuture(ComputeTaskFuture<Boolean> gridTaskFuture, SimulationQueueTaskContext context) {
        this.context = context
        this.gridTaskFuture = gridTaskFuture
    }

    @Override
    void stopListen(IQueueTaskListener queueTaskListener) {
        gridListeners.remove(queueTaskListener)
        //NB there is no chance to do this anymore in ignite:
        //gridTaskFuture.stopListenAsync(gridListener)
        queueTaskListener.stopListen()
    }

    @Override
    void listen(IQueueTaskListener queueTaskListener) {
        TaskListener taskListener = new TaskListener(queueTaskListener, this)
        gridListeners.put(queueTaskListener, taskListener)
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

    private static class TaskListener implements IgniteInClosure<ComputeTaskFuture<Boolean>> {
        IQueueTaskListener taskListener
        IQueueTaskFuture queueTaskFuture

        TaskListener(IQueueTaskListener taskListener, IQueueTaskFuture future) {
            this.taskListener = taskListener
            this.queueTaskFuture = future
        }

        @Override
        void apply(ComputeTaskFuture<Boolean> booleanComputeTaskFuture) {
            taskListener.apply(queueTaskFuture)
        }
    }
}
