package org.pillarone.riskanalytics.core.dataaccess;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.RiskAnalyticsResultAccessException;
import org.pillarone.riskanalytics.core.output.*;
import org.pillarone.riskanalytics.core.simulation.engine.grid.GridHelper;
import org.pillarone.riskanalytics.core.simulation.item.ResultConfiguration;


import java.io.File;
import java.io.FileFilter;
import java.util.*;

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
        long foundCollectorId = collectorId;
        long runId =  ResultAccessor.getRunIDFromSimulation(run);
        String args = String.format(
                "sim=%s(id:%d),pathid:%d,fieldid:%d,collector=%s(id:%d)",
                run.getName(),runId,
                pathId,
                fieldId,
                collector,collectorId
        );
        LOG.info("Lookup "+run.getPeriodCount()+" result periods for: " + args);

        for (int periodIdx = 0; periodIdx < run.getPeriodCount(); periodIdx++) {
            LOG.info("Lookup period "+periodIdx);
            File f = new File(GridHelper.getResultPathLocation(runId, pathId, fieldId, collectorId, periodIdx));

            /*Initial wildcard-based solution*/
            if (!f.exists()) { // if there's no "SINGLE" result...
                String wildcard = pathId + "_" + periodIdx + "_" + fieldId + "_*";
                String wildDesc = String.format("path:%d='%s',period:%d,field:%d='%s'", pathId,path,periodIdx,fieldId,field);
                LOG.info("No SINGLE result, check '"+runId+"/"+wildcard+"' ("+wildDesc+")");
                File[] fileList = new File(GridHelper.getResultLocation(runId))
                                .listFiles((FileFilter) new WildcardFileFilter(wildcard));

                if (fileList == null) {
                    String e = "Either an I/O error occurred or following not valid dir/filter: '"+runId+"/"+wildcard+"'";
                    LOG.error(e);
                    throw new RiskAnalyticsResultAccessException(e);
                }
                // fileList not null
                if (fileList.length > 0){
                    f = fileList[0];
                    final String foundPathname = f.getAbsolutePath();
                    int lastUnderscore = foundPathname.lastIndexOf('_');
                    if(lastUnderscore <0){ //not found
                        String e = "No Underscore found in name of iteration data file! '"+foundPathname+"'";
                        LOG.error(e);
                        throw new RiskAnalyticsResultAccessException(e);
                    }
                    final String foundCollectorIdStr = foundPathname.substring(lastUnderscore+1);
                    try{
                        foundCollectorId = Long.parseLong(foundCollectorIdStr);
                    } catch(NumberFormatException nfe) {
                        String w = "Unable to convert found collector id to number! '"+foundCollectorIdStr+"'";
                        LOG.warn(w); // Not sure if RiskAnalyticsResultAccessException will get out or get swallowed
                        throw new RiskAnalyticsResultAccessException(w);
                    }
                    String foundCollectorName = CollectorMapping.lookupCollector(foundCollectorId).getCollectorName();
                    LOG.info("Non-SINGLE result file: (Found collector:"+ foundCollectorId+"="+foundCollectorName+")" + foundPathname);

                    if (fileList.length > 1){
                        String w = "(AR-111 non-SINGLE) '"+runId+"/"+wildcard+"' yields multiple files for "+path+" at period "+ periodIdx;
                        LOG.warn(w); // Not sure if RiskAnalyticsResultAccessException will get out or get swallowed
                        throw new RiskAnalyticsResultAccessException(w);
                    }
                } else { // empty list
                    LOG.info("Non-SINGLE result not found matching '"+runId+"/"+wildcard+"'");
                }
            }
            /*AR-111 temporary block END*/
            else{
                LOG.info("Found "+collector+" result file: " + f.getName());
            }

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
                    List<DateTimeValuePair> values = ifa.getSingleValues();  // BUT are they single values if not SINGLE collector ?
                    LOG.info(String.format(
                            "IFA iteration %d with %d %s values",
                            iteration,
                            values.size(),
                            (foundCollectorId == collectorId ? "SINGLE" : "'SINGLE'") // ..ie not SINGLE collector...
                    ));

                    for (DateTimeValuePair val : values) {
                        SingleValueResultPOJO resultWithNullFieldsCollectorsSimRunAndPath = new SingleValueResultPOJO();
                        resultWithNullFieldsCollectorsSimRunAndPath.setDate(new DateTime(val.getDateTime()));
                        resultWithNullFieldsCollectorsSimRunAndPath.setValue(val.getaDouble());
                        resultWithNullFieldsCollectorsSimRunAndPath.setIteration(iteration);
                        resultWithNullFieldsCollectorsSimRunAndPath.setPeriod(periodIdx);
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
