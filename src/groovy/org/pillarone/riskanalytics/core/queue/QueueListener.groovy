package org.pillarone.riskanalytics.core.queue

interface QueueListener<T extends IQueueEntry> {

    void starting(T entry)

    void finished(T entry)

    void removed(UUID id)

    void offered(T entry)

}
