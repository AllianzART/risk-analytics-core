package org.pillarone.riskanalytics.core.simulation.engine.actions

import models.core.CoreModel
import org.junit.Test
import org.pillarone.riskanalytics.core.fileimport.ModelStructureImportService
import org.pillarone.riskanalytics.core.simulation.engine.SimulationScope
import org.pillarone.riskanalytics.core.simulation.item.ModelStructure
import org.pillarone.riskanalytics.core.simulation.item.Simulation

import static org.junit.Assert.*

class PrepareStructureInformationActionTests {

    @Test
    void testPerform() {

        new ModelStructureImportService().compareFilesAndWriteToDB(['Core'])

        CoreModel model = new CoreModel()
        model.init()
        model.injectComponentNames()

        SimulationScope simulationScope = new SimulationScope()
        simulationScope.model = model
        Simulation simulation = new Simulation("name")
        ModelStructure structure = ModelStructure.getStructureForModel(model.class)
        structure.load()
        simulation.structure = structure
        simulationScope.simulation = simulation

        assertNull simulationScope.structureInformation

        new PrepareStructureInformationAction(simulationScope: simulationScope).perform()

        assertNotNull simulationScope.structureInformation
    }
}