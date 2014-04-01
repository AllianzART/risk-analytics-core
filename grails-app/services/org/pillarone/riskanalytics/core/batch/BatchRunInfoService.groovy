package org.pillarone.riskanalytics.core.batch

import grails.util.Holders
import groovy.transform.CompileStatic
import org.pillarone.riskanalytics.core.BatchRunSimulationRun
import org.pillarone.riskanalytics.core.output.SimulationRun
import org.pillarone.riskanalytics.core.simulation.SimulationState
import org.pillarone.riskanalytics.core.simulation.item.Simulation

import static org.pillarone.riskanalytics.core.simulation.SimulationState.NOT_RUNNING

class BatchRunInfoService {

    private final List<BatchRunSimulationRun> runningBatchSimulationRuns
    private final Object lock = new Object()

    BatchRunInfoService() {
        runningBatchSimulationRuns = []
    }

    @CompileStatic
    static BatchRunInfoService getService() {
        return Holders.grailsApplication.mainContext.getBean(BatchRunInfoService)
    }


    @CompileStatic
    void batchSimulationStateChanged(Simulation simulation, SimulationState simulationState) {
        synchronized (lock) {
            BatchRunSimulationRun batchRunSimulationRun = runningBatchSimulationRuns.find { BatchRunSimulationRun it ->
                (it.simulationRun.name == simulation.name) && (it.simulationRun.model == simulation.modelClass.name)
            }
            if (!batchRunSimulationRun) {
                return
            }
            batchRunSimulationRun.simulationState = simulationState
            update(simulation, simulationState)
        }
    }

    @CompileStatic
    SimulationState getSimulationState(BatchRunSimulationRun batchRunSimulationRun) {
        synchronized (lock) {
            def run = runningBatchSimulationRuns.find { BatchRunSimulationRun it -> it.id == batchRunSimulationRun.id }
            run ? run.simulationState : null
        }
    }

    @CompileStatic
    void batchSimulationStart(Simulation simulation) {
        synchronized (lock) {
            BatchRunSimulationRun batchRunSimulationRun = update(simulation, NOT_RUNNING)
            if (!batchRunSimulationRun) {
                log.warn("BatchRunSimulationRun with simulation $simulation does not exist")
            } else {
                addRunning batchRunSimulationRun
            }
        }
    }

    @CompileStatic
    private void addRunning(BatchRunSimulationRun batchRunSimulationRun) {
        runningBatchSimulationRuns.remove(runningBatchSimulationRuns.find { BatchRunSimulationRun it ->
            it.simulationRun.name == batchRunSimulationRun.simulationRun.name
        })
        runningBatchSimulationRuns << batchRunSimulationRun
    }

    private BatchRunSimulationRun update(Simulation simulation, SimulationState simulationState) {
        BatchRunSimulationRun batchRunSimulationRun = BatchRunSimulationRun.findBySimulationRun(SimulationRun.get(simulation.id as Long))
        if (!batchRunSimulationRun) {
            log.warn("BatchRunSimulationRun with simulation $simulation does not exist")
            null
        } else {
            batchRunSimulationRun.simulationState = simulationState
            batchRunSimulationRun.save()
        }
    }
}

