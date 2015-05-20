package org.pillarone.riskanalytics.core.dataaccess;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.RiskAnalyticsResultAccessException;
import org.pillarone.riskanalytics.core.output.CollectorInformation;
import org.pillarone.riskanalytics.core.output.SimulationRun;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.simulation.engine.grid.GridHelper;
import org.pillarone.riskanalytics.core.output.ResultConfigurationDAO;
import org.pillarone.riskanalytics.core.simulation.item.ResultConfiguration;


import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * author simon.parten @ art-allianz . com
 */
public class ExportResultAccessor {


    /**
     * This method is a fairly close clone of @link(org.pillarone.riskanalytics.core.dataaccess.ResultAccessor#getSingleValueResults(java.lang.String, java.lang.String, java.lang.String, org.pillarone.riskanalytics.core.output.SimulationRun))
     *
     * The differences are that this is written in java, it should thus be faster. It should handle errors more gracefully, and it
     * is far more strongly typed.
     *
     * @param collector used to lookup the field in question
     * @param path path in question
     * @param field field in question
     * @param run the simulation run
     * @return returns a list of @link{org.pillarone.riskanalytics.core.output.SingleValueResultPOJO}. Note that these objects have null
     * fields set for their collector, simulationRun, path and fields member variables. The reason is that these should certainly be known by the calling function.
     * Furthermore, they are grails domain objects, and grails will do funky stuff on their creation. We therefore return null values.
     * It is important the calling function handles this appropriately.
     *
     */
    public static List<SingleValueResultPOJO> getSingleValueResultsForExport(String collector, String path, String field, SimulationRun run ) {
        List<SingleValueResultPOJO> result = new ArrayList<SingleValueResultPOJO>();
        long pathId = ResultAccessor.getPathId(path);
        long fieldId = ResultAccessor.getFieldId(field);
        long collectorId = ResultAccessor.getCollectorId(collector);

/**AR-111 temporary block START
** This is a temporary workaround to allow us to access results on the file system coming from
**  collectors not marked as SINGLE (i.e. filename suffix != 2)
**
** It has some BUILD PROBLEMS due to (I guess, given the workaround) groovyc not having yet compiled a couple of
** classes this file now need when it's passing this one to javac. Namely:
**    Problem: javac cannot find symbol getCollectorInformation in ResultConfigurationDAO,
**    Workaround: Shelve the changeset that introduced this bit of code. Compile. Unshelve, and recompile WITHOUT
**               CLEANING FIRST. Now it will build.
**
** Here we are storing the collectorId's for the collectors present in the simulation.
**  These will be the ones to be tried out as an alternative to SINGLE, if SINGLE isn't present
**  (the only call to this method currently in the code passes a hardcoded SINGLE)
**/

        Set<Long> alternativeCollectorIds = new HashSet<Long>();

        // the getCollectorInformationBridge() method is probably the wrong direction to take.. instead we need to figure out
        //how to load the result config from the DB ie use the DAO approach... here is some vague starting sketch..
//        ResultConfigurationDAO dao = run.getResultConfiguration();
//        ResultConfiguration resultConfiguration = new ResultConfiguration(dao.getName(),dao.getModel().getClass() );
//        if( !resultConfiguration.isLoaded() ){
//            resultConfiguration.load();
//        }

        for (CollectorInformation ci: run.getResultConfiguration().getCollectorInformationBridge()) {
            alternativeCollectorIds.add(Long.valueOf(ResultAccessor.getCollectorId(ci.getCollectingStrategyIdentifier())));
        }
        alternativeCollectorIds.remove(4);
        alternativeCollectorIds.remove(collectorId);
/*AR-111 tmporary block PAUSE*/

        for (int i = 0; i < run.getPeriodCount(); i++) {
            File f = new File(GridHelper.getResultPathLocation(ResultAccessor.getRunIDFromSimulation(run) , pathId, fieldId, collectorId, i));
/*AR-111 temporary block RESTART - Same thing as the one above...*/
            if (!f.exists()) { // if there's no "SINGLE" result...
                for (long cId: alternativeCollectorIds){
                    f = new File(GridHelper.getResultPathLocation(ResultAccessor.getRunIDFromSimulation(run) , pathId, fieldId, cId, i));
                    if (f.exists()) break; //when we find a collector id that matches the filename, we're good!
                }
            }
/*AR-111 temporary block END*/
            IterationFileAccessor ifa = null;
            try {
                ifa = new IterationFileAccessor(f);
            } catch (Exception e) {
                if( ifa != null ){
                    ifa.close();
                }
                throw new RiskAnalyticsResultAccessException("Failed to find file : " + f.toString(), e);
            }
            try {
                while (ifa.fetchNext()) {
                    int iteration = ifa.getIteration();
                    List<DateTimeValuePair> values = ifa.getSingleValues();

                    for (DateTimeValuePair val : values) {
                        SingleValueResultPOJO resultWithNullFieldsCollectorsSimRunAndPath = new SingleValueResultPOJO();
                        resultWithNullFieldsCollectorsSimRunAndPath.setDate(new DateTime(val.getDateTime()));
                        resultWithNullFieldsCollectorsSimRunAndPath.setValue(val.getaDouble());
                        resultWithNullFieldsCollectorsSimRunAndPath.setIteration(iteration);
                        resultWithNullFieldsCollectorsSimRunAndPath.setPeriod(i);
                        result.add(resultWithNullFieldsCollectorsSimRunAndPath);
                    }
                }
            } catch (Exception e) {
                throw new RiskAnalyticsResultAccessException("Failed to get iteration : " + ifa.getIteration(), e);
            } finally {
                if( ifa != null ){
                    ifa.close();
                }
            }
        }
        return result;
    }

}
