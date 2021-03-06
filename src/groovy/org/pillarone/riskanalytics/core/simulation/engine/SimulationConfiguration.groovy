package org.pillarone.riskanalytics.core.simulation.engine

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.joda.time.DateTime
import org.pillarone.riskanalytics.core.model.Model
import org.pillarone.riskanalytics.core.model.ModelHelper
import org.pillarone.riskanalytics.core.output.*
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ParameterApplicator
import org.pillarone.riskanalytics.core.simulation.SimulationException
import org.pillarone.riskanalytics.core.simulation.engine.grid.SimulationBlock
import org.pillarone.riskanalytics.core.simulation.item.ModelStructure
import org.pillarone.riskanalytics.core.simulation.item.Parameterization
import org.pillarone.riskanalytics.core.simulation.item.ResultConfiguration
import org.pillarone.riskanalytics.core.simulation.item.Simulation
import org.pillarone.riskanalytics.core.simulation.item.parameter.MultiDimensionalParameterHolder
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolder
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterObjectParameterHolder
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

            List<String> bySourcePaths = getDrillDownPaths(drillDownCollectors, DrillDownMode.BY_SOURCE)
            Set paths = ModelHelper.getAllPossibleOutputPaths(model, bySourcePaths)

            Set<String> inceptionPeriodPaths = getSplitByInceptionDateDrillDownPaths(drillDownCollectors, model)
            paths.addAll(inceptionPeriodPaths)

            Set<String> pastVsFuturePaths = getSplitByPastVsFutureDrillDownPaths(drillDownCollectors, model)
            paths.addAll(pastVsFuturePaths)

            Set<String> calendarYearPaths = getSplitByCalendarYear(drillDownCollectors, model)
            paths.addAll(calendarYearPaths)

            Set<String> catTypePaths = getSplitByCatType(drillDownCollectors, model)
            paths.addAll(catTypePaths)

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
        return ModelHelper.pathsExtendedWithPeriod(splitByPastVsFuturePaths, [DrillDownMode.fromPastName, DrillDownMode.fromFutureName, DrillDownMode.fromNextName]) //AR-111
    }

    /* AR-111 - Want something like ['2014','2015','2016'] */
    private Set<String> getSplitByCalendarYear(List<PacketCollector> collectors, Model model) {
        List<String> basePaths = getDrillDownPaths(collectors, DrillDownMode.BY_CALENDARYEAR)
        if( basePaths == null || basePaths.size()==0){
            LOG.info("getSplitByCalendarYear(): nothing to do (no basePaths for DrillDownMode.BY_CALENDARYEAR)")
            return new HashSet<String>()
        }
        //Set<String> periodLabels = model.periodLabelsBeforeProjectionStart()   //not needed. Might need something similar
        //periodLabels.addAll PeriodLabelsUtil.getPeriodLabels(simulation, model)//if we want to include the update date in the paths...

        // For simple annual case can stick with "Get hold of period labels from p14n and chop off the non year bits"
        // Custom periods: obtain periods directly via: sim -> p14n -> coverage parameter -> ...
        //
        ArrayList<String> calendarYears = stringListOfCalendarYearsInCoverage()

        return ModelHelper.pathsExtendedWithCYofOccurrence(
                basePaths,
                new ArrayList<String>(calendarYears) //why copying it?
        )
    }

    private ArrayList<String> stringListOfFinancialQuartersInCoverage() {

        //conservatively adds all four quarters for every financial year in coverage

        ArrayList<String> coveredYears = stringListOfCalendarYearsInCoverage();

        ArrayList<String> output = new ArrayList<String>(coveredYears.size() * 4);

        for (String year in coveredYears) {
            for (int i = 1; i <= 4; ++i) {
                output.add(year/*.substring(year.length()-2)*/ + "Q" + i);
            }
        }

        return output;
    }

    private ArrayList<String> stringListOfCalendarYearsInCoverage() {
        Parameterization parameterization = simulation.parameterization
        List<ParameterHolder> coveragePeriodList = parameterization.parameterHolders.findAll {
            (!it.removed) &&
            (it.path=="globalParameters:parmCoveragePeriod")
        };

        // There must be exactly one coverage period ?
        // Except in tests doh...
        //
        if( coveragePeriodList.size() != 1 ){
            logAndThrowSimulationException(
                "P14n has: ${coveragePeriodList.size()} (not-deleted) coverage period parameters! (with path:'globalParameters:parmCoveragePeriod')"
            )
        }
        // And it must be a ParameterObjectParameterHolder..
        //
        if( ! (coveragePeriodList.first() instanceof ParameterObjectParameterHolder) ){
            logAndThrowSimulationException(
                "P14n's coverage period type ${coveragePeriodList.first().getClass().getName()} (path:'globalParameters:parmCoveragePeriod') wanted: ParameterObjectParameterHolder !"
            )
        }
        // OK ready to party perhaps.. debugger shows 'classifier' as CUSTOM for Chrysler vR11 (problem p14n)
        // TODO Maybe worth testing classifierParameters field instead.. as that holds the full periods list in Chrysler case..
        //
        List<String> calendarYears = new ArrayList<String>();

        ParameterObjectParameterHolder coverageParameterHolder = (ParameterObjectParameterHolder) coveragePeriodList.first();
        if( coverageParameterHolder.classifier.typeName == "CUSTOM"  ){
            // For Chryser, the CUSTOM classifier is an instance of com.allianz.art.riskanalytics.pc.global.PeriodStrategyType
            // But it doesnt contain ALL the period entries! Those are found instead (why?!) in the field classifierParameters..
            // Obtain the list of periods from the 'periods' entry of classifierParameters.
            //
            Map<String, ParameterHolder> classifierParameters = coverageParameterHolder.classifierParameters;
            if( classifierParameters.get("periods") == null ){
                logAndThrowSimulationException(
                    "No 'periods' entry in classifierParameters map in P14n coverage period (path:'globalParameters:parmCoveragePeriod')!"
                )
            }

            ConstrainedMultiDimensionalParameter periodStuff = // Might puke for some models if a different class is somehow used in it!
                (classifierParameters.get("periods") /*MultiDimensionalParameterHolder*/ ).value

            // Chrysler case has :
            // - titles field = array with entries 'Start Date', 'End Date'; and
            // - values field = array with a list for each of the titles
            //
            // Add some asserts to catch any variations when we run a batch, so we can understand this steamy pile better...
            //
            List<String> titles = periodStuff.titles
            assert titles.size() == 2
            assert titles.get(0) == "Start Date"
            assert titles.get(1) == "End Date"

            // Assert that there are two lists in the date stuff, and each has same number of entries
            // i.e.: there is an end date for each start date
            //
            List<List<DateTime>> dateStuff = periodStuff.values
            assert dateStuff.size()==2
            List<DateTime> startDates = dateStuff.get(0)
            List<DateTime> endDates   = dateStuff.get(1)
            assert startDates.size() == endDates.size()

            // Now we can walk the two lists in dateStuff and accumulate the year numbers
            // Use a Set to be clever like Paolo suggested
            //
            Set<String> distinctYears = new HashSet<String>()
            for( int i = 0; i<startDates.size(); ++i ){

                DateTime start = startDates.get(i)
                DateTime end   = endDates.get(i)

                LOG.info("Collecting years from custom period: '$start' to '$end' ")

                assert end.compareTo(start) > 0

                // Find each year within this range ??
                // Can't think of any cleverer way than just walking the months...
                // TODO but must check if that's correct...
                //
                // Approach I think Paolo uses - adding N months to start instead of chaining N increments of 1 month:
                //
                DateTime day = new DateTime(start)
                for( int d = 0; day.compareTo(end) < 0; ++d){ // ugly
                    if(d>1200){
                        logAndThrowSimulationException("Something's wrong or we exceeded 100-year contract in model '${parameterization.nameAndVersion}'")
                    }
                    day = new DateTime(start).plusMonths(d)   // Welcome to 'free' memory world of the jvm..
                    String year = "" + day.getYear()
                    if(!distinctYears.contains(year)){
                        LOG.info("Adding year: $year")
                        distinctYears.add(year)

                        // NOW I DON'T KNOW WHAT TO DO HERE IF THE CONFIGURED PERIODS HAVE GAPS IN THEM
                        // ...OVER TO YOU PAOLO...
                    }
                }
                // TODO (PAOLO TIP) - ADD ONE MORE MONTH AND CHECK THE YEAR
            } // for each period

            calendarYears = new ArrayList<String>(distinctYears)



        } else if( coverageParameterHolder.classifier.typeName == "ANNUAL" ){
            // Later: may make sense to ditch the period labels in favour of simpler direct iteration over annual periods?
            //
            List<String> periodLabels = simulation?.parameterization.getPeriodLabels()

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

        }
        calendarYears
    }

    /* AR-111 - Want Nat or Non-nat */
    //now adding quarters here...
    private Set<String> getSplitByCatType(List<PacketCollector> collectors, Model model) {
        List<String> basePaths = getDrillDownPaths(collectors, DrillDownMode.BY_CAT_TYPE)
        if( basePaths == null || basePaths.size()==0 ){
            LOG.info("getSplitByCatType(): nothing to do (no basePaths for DrillDownMode.BY_CAT_TYPE)")
            return new HashSet<String>()
        }

        ArrayList<String> baseList = [DrillDownMode.catType_Nat, DrillDownMode.catType_nonNat]

        ArrayList<String> extendedList = []

        for (String quarter in stringListOfFinancialQuartersInCoverage()) {
            for (String catType in baseList) {
                extendedList.add(quarter + "_" + catType)
            }
        }

        return ModelHelper.pathsExtendedWithCatType(basePaths, extendedList) //AR-111
    }

    private void logAndThrowSimulationException(String error) {
        LOG.error(error)
        throw new SimulationException(error)
    }

    /*AR-111 end*/
    private Set<String> hardcodedTypeSplitEnumRegistry() {
        return ["ncb", "premium", "loss", "term"]
    }
}