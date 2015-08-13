package org.pillarone.riskanalytics.core.dataaccess;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.RiskAnalyticsResultAccessException;
import org.pillarone.riskanalytics.core.output.CollectorInformation;
import org.pillarone.riskanalytics.core.output.SimulationRun;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.simulation.engine.grid.GridHelper;
import org.pillarone.riskanalytics.core.output.ResultConfigurationDAO;
import org.pillarone.riskanalytics.core.simulation.item.ResultConfiguration;


import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * author simon.parten @ art-allianz . com
 */
public class ExportResultAccessor {
    private static Log LOG = LogFactory.getLog(ExportResultAccessor.class);
    /**
     * This method is a fairly close clone of @link(..core.dataaccess.ResultAccessor#getSingleValueResults(String, String, String, SimulationRun))
     *                                                                ^^^^^^^^^^^^^^
     * The differences are that this is written in java, it should thus be faster.
     * It should handle errors more gracefully, and is more strongly typed.
     *
     * @param collector used to lookup the field in question
     * @param path path in question
     * @param field field in question
     * @param run the simulation run
     * @return list of @link{org.pillarone.riskanalytics.core.output.SingleValueResultPOJO}.
     * NOTE caller must be aware we return null for collector, simulationRun, path and fields; as :
     *  1) these should certainly be known by the calling function.
     *  2) as they are grails domain objects grails may do funky stuff with them. We avoid this with null values.
     *
     */
    public static List<SingleValueResultPOJO> getSingleValueResultsForExport(String collector, String path, String field, SimulationRun run ) {
        List<SingleValueResultPOJO> result = new ArrayList<SingleValueResultPOJO>();
        long pathId = ResultAccessor.getPathId(path);
        long fieldId = ResultAccessor.getFieldId(field);
        long collectorId = ResultAccessor.getCollectorId(collector);
        long runId =  ResultAccessor.getRunIDFromSimulation(run);

        for (int i = 0; i < run.getPeriodCount(); i++) {
            File f = new File(GridHelper.getResultPathLocation(runId , pathId, fieldId, collectorId, i));
            File[] fileList = {f};
            /*Initial wildcard-based solution*/
            if (!f.exists()) { // if there's no "SINGLE" result...
                File dir = new File(GridHelper.getResultLocation(runId));
                String wildcard = pathId + "_" + i + "_" + fieldId + "_*";
                FileFilter filter = new WildcardFileFilter(wildcard);
                fileList = dir.listFiles(filter);
                if (fileList != null) {
                    if (fileList.length > 0){
                        f = fileList[0];
                    }
                    if (fileList.length > 1){
                        String w = "(AR-111 non-SINGLE) '"+runId+"/"+wildcard+"' yields multiple files for "+path+" at period "+ i;
                        LOG.warn(w); // Not sure if RiskAnalyticsResultAccessException will get out or get swallowed
                        throw new RiskAnalyticsResultAccessException(w);
                    }
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
