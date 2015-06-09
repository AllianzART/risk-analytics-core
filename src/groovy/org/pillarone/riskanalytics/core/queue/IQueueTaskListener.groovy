package org.pillarone.riskanalytics.core.queue

interface IQueueTaskListener {
    void apply(IQueueTaskFuture future)
    void deactivate() // We do this once listener should be removed from notifier but can't as no api in ignite
}