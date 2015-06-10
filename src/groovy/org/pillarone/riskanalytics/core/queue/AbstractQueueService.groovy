package org.pillarone.riskanalytics.core.queue

import com.google.common.base.Preconditions
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

abstract class AbstractQueueService<K, Q extends IQueueEntry<K>> implements IQueueService<Q> {
    private static final Log LOG = LogFactory.getLog(AbstractQueueService)

    protected final PriorityQueue<Q> queue = new PriorityQueue<Q>()
    protected final Object lock = new Object()
    protected CurrentTask<Q> currentTask
    protected TaskListener taskListener
    protected boolean busy = false
    protected Timer pollingTimer

    @Delegate
    private QueueNotifyingSupport<Q> support = new QueueNotifyingSupport<Q>()

    @Override
    void removeQueueListener(QueueListener<Q> queueListener) {
        support.removeQueueListener(queueListener)
    }

    @Override
    void addQueueListener(QueueListener<Q> queueListener) {
        support.addQueueListener(queueListener)
    }

    @PostConstruct
    private void initialize() {
        taskListener = new TaskListener()
        pollingTimer = new Timer()
        pollingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            void run() {
                poll()
            }
        }, 1000, 500)
    }

    @PreDestroy
    void stopPollingTimer() {
        pollingTimer.cancel()
        pollingTimer = null
        taskListener = null
    }

    void offer(K configuration, int priority = 5) {
        try{
            preConditionCheck(configuration)
            synchronized (lock) {
                LOG.info( "Entered offer (got lock) in thread " + Thread.currentThread().getName() )
                Q queueEntry = createQueueEntry(configuration, priority)
                queue.offer(queueEntry)
                support.notifyOffered(queueEntry)
                LOG.info( "Leaving offer (dropping lock) in thread " + Thread.currentThread().getName() )
            }
        } catch(Throwable t){
            LOG.error("Unexpected exception seen: $t.message",t)
            throw t
        }

    }

    abstract Q createQueueEntry(K configuration, int priority)

    abstract Q createQueueEntry(UUID id)

    abstract void preConditionCheck(K configuration)

    void cancel(UUID uuid) {
        Preconditions.checkNotNull(uuid)
        synchronized (lock) {
            LOG.info( "Entered cancel (got lock) in thread " + Thread.currentThread().getName() )
            if (currentTask?.entry?.id == uuid) {
                currentTask.future.cancel()
                return
            }
            if (queue.remove(createQueueEntry(uuid))) {
                support.notifyRemoved(uuid)
            }
            LOG.info( "Leaving cancel (dropping lock) in thread " + Thread.currentThread().getName() )
        }
    }

    List<Q> getQueueEntries() {
        synchronized (lock) {
            LOG.info( "Entered/leaving getQueueEntries in thread " + Thread.currentThread().getName() )
            return queue.toArray().toList() as List<Q>
        }
    }

    List<Q> getQueueEntriesIncludingCurrentTask() {
        synchronized (lock) {
            LOG.info( "Entered getQueueEntriesIncludingCurrentTask (got lock) in thread " + Thread.currentThread().getName() )
            List<Q> allEntries = queue.toArray().toList() as List<Q>
            if (currentTask) {
                allEntries.add(0, currentTask.entry)
            }
            LOG.info( "Leaving getQueueEntriesIncludingCurrentTask (dropping lock) in thread " + Thread.currentThread().getName() )
            return allEntries
        }
    }

    private void poll() {

        synchronized (lock) {
//            LOG.info( "Entered poll (got lock) in thread " + Thread.currentThread().getName() )
            if (!busy) {
                if (currentTask) {
                    throw new IllegalStateException("Want to start new job. But there is still a running one")
                }
                Q queueEntry = queue.poll()
                if (queueEntry) {
                    busy = true
                    support.notifyStarting(queueEntry)
                    IQueueTaskFuture future = doWork(queueEntry, queueEntry.priority)
                    currentTask = new CurrentTask<Q>(future: future, entry: queueEntry)
                    future.listen(taskListener)
                }
            }
//            LOG.info( "Leaving poll (dropping lock) in thread " + Thread.currentThread().getName() )
        }
    }

    abstract IQueueTaskFuture doWork(Q entry, int priority)

    private void queueTaskFinished(IQueueTaskFuture future) {
        synchronized (lock) {
            LOG.info( "Entered queueTaskFinished (got lock) in thread " + Thread.currentThread().getName() )
            if (!currentTask) {
                throw new IllegalStateException('simulation ended, but there is no currentTask')
            }
            busy = false
            Q entry = currentTask.entry
            currentTask = null
            future.stopListen(taskListener)
            handleEntry(entry)
            support.notifyFinished(entry.id)
            LOG.info( "Leaving queueTaskFinished (dropping lock) in thread " + Thread.currentThread().getName() )
        }
    }

    abstract void handleEntry(Q entry)

    private static class CurrentTask<Q extends IQueueEntry> {
        IQueueTaskFuture future
        Q entry
    }

    private class TaskListener implements IQueueTaskListener {
        @Override
        void apply(IQueueTaskFuture future) {
            queueTaskFinished(future)
        }
    }

}




