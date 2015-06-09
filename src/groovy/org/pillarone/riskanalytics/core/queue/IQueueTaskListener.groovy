package org.pillarone.riskanalytics.core.queue

interface IQueueTaskListener {
    void apply(IQueueTaskFuture future)
    void stopListen() // Helps if listener cannot be unregistered in publisher (eg in ignite)
}