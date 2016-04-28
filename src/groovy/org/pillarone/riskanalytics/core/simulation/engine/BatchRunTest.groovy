package org.pillarone.riskanalytics.core.simulation.engine

import grails.util.Holders
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.pillarone.riskanalytics.core.batch.BatchRunService
import org.pillarone.riskanalytics.core.queue.QueueListener
import org.pillarone.riskanalytics.core.simulation.SimulationState
import org.pillarone.riskanalytics.core.simulation.engine.grid.SimulationTask
import org.pillarone.riskanalytics.core.simulation.item.Batch

import java.util.concurrent.CountDownLatch

abstract class BatchRunTest extends ModelTest {

    MyListener listener

    @Before
    void addListener() {
        listener = new MyListener()
        simulationQueueService.addQueueListener(listener)
    }

    @After
    void removeListener() {
        simulationQueueService.removeQueueListener(listener)
        listener = null
    }

    @Test
    final void testBatchRun() {
        Batch batch = new Batch("testBatchRun")
        batch.simulationProfileName = simulationProfile.name
        batch.parameterizations = [run.parameterization]
        assert batch.save()
        batchRunService.offerOneByOne=false // makes it do it the old synchronous way
        batchRunService.runBatch(batch, null)
        assert batch.executed
        assert listener.offered.size() == 1
        SimulationQueueEntry entry = listener.offered.first()
        SimulationTask simulationTask = entry.context.simulationTask
        assert simulationTask.simulation.parameterization == run.parameterization
        //wait to finish simulation
        listener.waitUntilFinished()
        assert SimulationState.FINISHED == simulationTask.simulationState
    }

    static BatchRunService getBatchRunService() {
        Holders.grailsApplication.mainContext.getBean('batchRunService', BatchRunService)
    }

    static SimulationQueueService getSimulationQueueService() {
        Holders.grailsApplication.mainContext.getBean('simulationQueueService', SimulationQueueService)
    }


    static class MyListener implements QueueListener<SimulationQueueEntry> {
        CountDownLatch latch = new CountDownLatch(1)

        List<SimulationQueueEntry> offered = []

        void waitUntilFinished() {
            latch.await()
        }

        @Override
        void starting(SimulationQueueEntry entry) {}

        @Override
        void finished(SimulationQueueEntry entry) {
            latch.countDown()
        }

        @Override
        void removed(UUID id) {}

        @Override
        void offered(SimulationQueueEntry entry) {
            offered << entry
        }
    }
}
