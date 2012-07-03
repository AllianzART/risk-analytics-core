package org.pillarone.riskanalytics.core.output

import models.core.CoreModel
import org.pillarone.riskanalytics.core.ParameterizationDAO
import org.pillarone.riskanalytics.core.fileimport.ParameterizationImportService
import org.pillarone.riskanalytics.core.fileimport.ResultConfigurationImportService
import org.pillarone.riskanalytics.core.simulation.item.Simulation
import org.pillarone.riskanalytics.core.fileimport.ModelStructureImportService

class CalculatorTests extends GroovyTestCase {

    SimulationRun run

    void setUp() {
        assertNotNull(new CollectorMapping(collectorName: SingleValueCollectingModeStrategy.IDENTIFIER).save())

        new ParameterizationImportService().compareFilesAndWriteToDB(['CoreParameters'])
        new ResultConfigurationImportService().compareFilesAndWriteToDB(['CoreResultConfiguration'])
        new ModelStructureImportService().compareFilesAndWriteToDB(['CoreStructure'])
        run = new SimulationRun()
        run.name = 'testRun'
        run.parameterization = ParameterizationDAO.findByName('CoreParameters')
        run.resultConfiguration = ResultConfigurationDAO.findByName('CoreResultConfiguration')
        run.model = CoreModel.name
        run.iterations = 1
        run.periodCount = 2
        assertNotNull run.save()

        PathMapping path1 = new PathMapping(pathName: "path1").save()
        PathMapping path2 = new PathMapping(pathName: "path2").save()
        PathMapping path3 = new PathMapping(pathName: "path3").save()
        PathMapping path4 = new PathMapping(pathName: "path4").save()
        CollectorMapping collector = new CollectorMapping(collectorName: AggregatedCollectingModeStrategy.IDENTIFIER).save()
        FieldMapping field = new FieldMapping(fieldName: "field").save()

        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 0, value: 1, path: path1, collector: collector, field: field).save()
        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 0, value: 2, path: path2, collector: collector, field: field).save()
        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 0, value: 3, path: path3, collector: collector, field: field).save()

        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 1, value: 4, path: path1, collector: collector, field: field).save()
        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 1, value: 5, path: path2, collector: collector, field: field).save()
        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 1, value: 6, path: path3, collector: collector, field: field).save()

        //period 0 should be ignored, because path does not contain a result for all iterations
        assertNotNull new SingleValueResult(simulationRun: run, iteration: 1, period: 1, value: 7, path: path4, collector: collector, field: field,).save()

    }

    void testResults() {

        int initialRecordCount = PostSimulationCalculation.count()
        Simulation simulation = new Simulation(run.name)
        simulation.load()

        Calculator calculator = new Calculator(simulation)
        calculator.calculate()

        assertEquals initialRecordCount + 14, PostSimulationCalculation.count()

        assertEquals 7, PostSimulationCalculation.countByRunAndKeyFigure(run, PostSimulationCalculation.MEAN)
        assertEquals 7, PostSimulationCalculation.countByRunAndKeyFigure(run, PostSimulationCalculation.IS_STOCHASTIC)

    }

}