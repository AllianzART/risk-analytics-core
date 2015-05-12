package org.pillarone.riskanalytics.core.simulation.engine.grid;

import org.apache.ignite.lang.IgniteBiPredicate;
import org.pillarone.riskanalytics.core.simulation.SimulationState;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultTransferObject;

import java.util.UUID;


public class ResultTransferListener implements IgniteBiPredicate<UUID, ResultTransferObject> {

    private SimulationTask simulationTask;

    public ResultTransferListener(SimulationTask simulationTask) {
        this.simulationTask = simulationTask;
    }

    @Override
    public boolean apply(UUID uuid, ResultTransferObject resultTransferObject) {
        try {
            simulationTask.writeResult(resultTransferObject);
        } catch (Exception e) {
            simulationTask.getSimulationErrors().add(e);
            simulationTask.setSimulationState(SimulationState.ERROR);
            throw new RuntimeException(e);
        }
        return false;
    }
}
