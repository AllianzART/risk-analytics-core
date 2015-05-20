package org.pillarone.riskanalytics.core.simulation.engine.grid;

import org.apache.ignite.messaging.MessagingListenActor;
import org.pillarone.riskanalytics.core.simulation.SimulationState;
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultTransferObject;

import java.util.UUID;


public class ResultTransferListener extends MessagingListenActor<ResultTransferObject> {

    private SimulationTask simulationTask;

    public ResultTransferListener(SimulationTask simulationTask) {
        this.simulationTask = simulationTask;
    }

    @Override
    protected void receive(UUID nodeId, ResultTransferObject rcvMsg) throws Throwable {
        try {
            simulationTask.writeResult(rcvMsg);
        } catch (Exception e) {
            simulationTask.getSimulationErrors().add(e);
            simulationTask.setSimulationState(SimulationState.ERROR);
            throw new RuntimeException(e);
        }
    }
}
