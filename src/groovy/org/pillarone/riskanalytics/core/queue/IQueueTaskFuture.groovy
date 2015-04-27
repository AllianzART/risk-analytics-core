package org.pillarone.riskanalytics.core.queue

interface IQueueTaskFuture {
    void stopListen(IQueueTaskListener taskListener)

    void listen(IQueueTaskListener uploadTaskListener)

    void cancel()

}
