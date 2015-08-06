package org.pillarone.riskanalytics.core.simulation.engine

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.model.Model
import org.pillarone.riskanalytics.core.model.ModelHelper
import org.pillarone.riskanalytics.core.output.*
import org.pillarone.riskanalytics.core.parameterization.ParameterApplicator
import org.pillarone.riskanalytics.core.simulation.engine.grid.SimulationBlock
import org.pillarone.riskanalytics.core.simulation.item.ModelStructure
import org.pillarone.riskanalytics.core.simulation.item.Parameterization
import org.pillarone.riskanalytics.core.simulation.item.ResultConfiguration
import org.pillarone.riskanalytics.core.simulation.item.Simulation
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolder
import org.pillarone.riskanalytics.core.util.PeriodLabelsUtil
import org.pillarone.riskanalytics.core.wiring.IPacketListener
import org.springframework.beans.factory.config.BeanDefinition
/**
 * The SimulationConfiguration is a descriptor for a runnable simulation. All runtime aspects e.g. numberOfIterations,
 * numberOfPeriods, the parameterization, etc are stored in the simulationRun. They have to be persistent.
 * The way how results get stored is given with the outputStrategy
 *
 * Use the SimulationConfiguration to configure a SimulationRunner instance.
 */
public class SimulationConfiguration implements Serializable, Cloneable {
    private static final Log LOG = LogFactory.getLog(SimulationConfiguration)

    Simulation simulation
    MappingCache mappingCache
    List<SimulationBlock> simulationBlocks = []
    IPacketListener packetListener;
    Map<String, BeanDefinition> beans = [:]
    ICollectorOutputStrategy outputStrategy
    ResultData resultDataSource
    //the user who offered this task to the queue.
    final String username


    SimulationConfiguration(Simulation simulation, ICollectorOutputStrategy outputStrategy, String username = null) {
        this.simulation = simulation
        this.outputStrategy = outputStrategy
        this.username = username
    }

    SimulationConfiguration(Simulation simulation, String username = null) {
        this(simulation, new DBOutput(), username)
    }
/**
 * This creates a new Simulation instance based on the existing one, which only contains the necessary info for the
 * simulation to make sure that this object can be serialized to the grid.
 */
    void prepareSimulationForGrid() {
        Simulation preparedSimulation = new Simulation(simulation.name)
        preparedSimulation.id = simulation.id
        preparedSimulation.numberOfIterations = simulation.numberOfIterations
        preparedSimulation.beginOfFirstPeriod = simulation.beginOfFirstPeriod
        preparedSimulation.randomSeed = simulation.randomSeed
        preparedSimulation.modelClass = simulation.modelClass
        preparedSimulation.periodCount = simulation.periodCount
        preparedSimulation.runtimeParameters = simulation.runtimeParameters.collect { (ParameterHolder) it.clone() }
        preparedSimulation.keyFiguresToPreCalculate = simulation.keyFiguresToPreCalculate

        preparedSimulation.parameterization = new Parameterization(simulation.parameterization.name, simulation.parameterization.modelClass)
        preparedSimulation.parameterization.periodCount = simulation.parameterization.periodCount
        preparedSimulation.parameterization.versionNumber = simulation.parameterization.versionNumber
        preparedSimulation.parameterization.modelVersionNumber = simulation.parameterization.modelVersionNumber
        preparedSimulation.parameterization.dealId = simulation.parameterization.dealId

        //clone parameters to make sure they don't have any model or component references
        preparedSimulation.parameterization.parameterHolders = simulation.parameterization.parameterHolders.collect {
            (ParameterHolder) it.clone()
        }
        simulation.parameterization.parameterHolders*.clearCachedValues()


        preparedSimulation.template = new ResultConfiguration(simulation.template.name, simulation.template.modelClass)
        preparedSimulation.template.versionNumber = simulation.template.versionNumber
        preparedSimulation.template.collectors = simulation.template.collectors

        preparedSimulation.structure = ModelStructure.getStructureForModel(simulation.modelClass)
        preparedSimulation.modelVersionNumber = simulation.modelVersionNumber
        preparedSimulation.simulationState = simulation.simulationState
        preparedSimulation.batch = simulation.batch

        this.simulation = preparedSimulation
    }

    SimulationConfiguration clone() {
        SimulationConfiguration configuration = (SimulationConfiguration) super.clone()
        configuration.simulationBlocks = []
        return configuration
    }

    void addSimulationBlock(SimulationBlock simulationBlock) {
        simulationBlocks << simulationBlock
    }

    /**
     * Determines all possible path & field values for this simulation and persists them if they do not exist yet, because we do not have any DB access
     * during a grid job.
     * @param simulationConfiguration the simulation details
     * @return a mapping cache filled with all necessary mappings for this simulation.
     */
    MappingCache createMappingCache(ResultConfiguration resultConfiguration) {
        SimulationRun.withTransaction {
            Model model = (Model) simulation.modelClass.newInstance()
            model.init()

            ParameterApplicator parameterApplicator = new ParameterApplicator(model: model, parameterization: simulation.parameterization)
            parameterApplicator.init()
            parameterApplicator.applyParameterForPeriod(0)

            SimulationRunner runner = SimulationRunner.createRunner()
            CollectorFactory collectorFactory = runner.currentScope.collectorFactory
            List<PacketCollector> drillDownCollectors = resultConfiguration.getResolvedCollectors(model, collectorFactory)
            List<String> drillDownPaths = getDrillDownPaths(drillDownCollectors, DrillDownMode.BY_SOURCE)
            Set paths = ModelHelper.getAllPossibleOutputPaths(model, drillDownPaths)
            Set<String> inceptionPeriodPaths = getSplitByInceptionDateDrillDownPaths(drillDownCollectors, model)
            paths.addAll(inceptionPeriodPaths)
            Set<String> pastVsFuturePaths = getSplitByPastVsFutureDrillDownPaths(drillDownCollectors, model)
            paths.addAll(pastVsFuturePaths)
            Set<String> calendarYearPaths = getSplitByCalendarYear(drillDownCollectors, model)
            paths.addAll(calendarYearPaths)
            Set<String> typeDrillDownPaths = getPotentialTypeDrillDowns(drillDownCollectors)
            paths.addAll(typeDrillDownPaths)

            Set fields = ModelHelper.getAllPossibleFields(model, !inceptionPeriodPaths.empty)
            MappingCache cache = MappingCache.instance

            for (String path in paths) {
                cache.lookupPath(path)
            }

            for (String field in fields) {
                cache.lookupField(field)
            }

            this.mappingCache = cache
        }
    }

    Set<String> getPotentialTypeDrillDowns(List<PacketCollector> collectors) {
        List<String> splitByTypePaths = getDrillDownPaths(collectors, DrillDownMode.BY_TYPE)
        Set<String> typelabels = hardcodedTypeSplitEnumRegistry()
        return ModelHelper.pathsExtendedWithType(splitByTypePaths, typelabels)
    }

    private List<String> getDrillDownPaths(List<PacketCollector> collectors, DrillDownMode mode) {
        List<String> paths = []
        for (ICollectingModeStrategy strategy : CollectingModeFactory.getDrillDownStrategies(mode)) {
            addMatchingCollector(strategy, collectors, paths)
        }
        return paths
    }

    private addMatchingCollector(ICollectingModeStrategy collectorModeStrategy, List<PacketCollector> collectors, List<String> paths) {
        if (collectorModeStrategy != null) {
            for (PacketCollector collector : collectors) {
                if (collector.mode.class.equals(collectorModeStrategy.class)) {
                    paths << collector.path
                }
            }
        }
    }

    private Set<String> getSplitByInceptionDateDrillDownPaths(List<PacketCollector> collectors, Model model) {
        List<String> splitByInceptionDatePaths = getDrillDownPaths(collectors, DrillDownMode.BY_PERIOD)
        Set<String> periodLabels = model.periodLabelsBeforeProjectionStart()
        periodLabels.addAll PeriodLabelsUtil.getPeriodLabels(simulation, model)
        return ModelHelper.pathsExtendedWithPeriod(splitByInceptionDatePaths, periodLabels.toList()) //AR-111
    }

    /* fugly */ /*I do certainly agree. */

    /* AR-111 - In a way this duplicates the ugly thing above. Should think about refactoring */
    private Set<String> getSplitByPastVsFutureDrillDownPaths(List<PacketCollector> collectors, Model model) {
        List<String> splitByPastVsFuturePaths = getDrillDownPaths(collectors, DrillDownMode.BY_UPDATEDATE)
        //Set<String> periodLabels = model.periodLabelsBeforeProjectionStart()   //not needed. Might need something similar
        //periodLabels.addAll PeriodLabelsUtil.getPeriodLabels(simulation, model)//if we want to include the update date in the paths...
        return ModelHelper.pathsExtendedWithPeriod(splitByPastVsFuturePaths, [DrillDownMode.fromPastName, DrillDownMode.fromFutureName]) //AR-111
    }

    /* AR-111 - Want something like ['2014','2015','2016'] */
    private Set<String> getSplitByCalendarYear(List<PacketCollector> collectors, Model model) {
        List<String> basePaths = getDrillDownPaths(collectors, DrillDownMode.BY_CALENDARYEAR)
        //Set<String> periodLabels = model.periodLabelsBeforeProjectionStart()   //not needed. Might need something similar
        //periodLabels.addAll PeriodLabelsUtil.getPeriodLabels(simulation, model)//if we want to include the update date in the paths...

        // Get hold of period labels from p14n and chop off the non year bits

        List<String> periodLabels = simulation?.parameterization.getPeriodLabels()
        List<String> calendarYears = new ArrayList<String>();
        for( String label : periodLabels  ){
            String year = label.substring(0,4)  // [begin,end)
            LOG.info("Adding Year : [$year]")
            assert year.length() == 4
            calendarYears.add(year)  // [begin,end)
        }

        if (!calendarYears.empty) {
            calendarYears.add( (calendarYears.last().toInteger() + 1).toString() )
        }
        //todo this could still leave gaps when the periods are not annual

        return ModelHelper.pathsExtendedWithCYofOccurrence(
                basePaths,
                new ArrayList<String>(calendarYears) //why copying it?
        )
    }


    /*AR-111 end*/
    private Set<String> hardcodedTypeSplitEnumRegistry() {
        return ["ncb", "premium", "loss", "term"]
    }
}