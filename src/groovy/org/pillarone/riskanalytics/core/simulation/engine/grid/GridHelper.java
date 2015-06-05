package org.pillarone.riskanalytics.core.simulation.engine.grid;

import grails.util.Holders;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.pillarone.riskanalytics.core.FileConstants;

import java.io.File;
import java.util.List;

public class GridHelper {

    private static Log LOG = LogFactory.getLog(GridHelper.class);

    public static Ignite getGrid() {
        try {
            // Probably too long winded, but will do for starters..
            //
            Ignite grid = Holders.getGrailsApplication().getMainContext().getBean(Ignite.class);
            if( grid != null ){
                LOG.info("Found Ignite bean");
                return grid;
            }
            throw new IllegalStateException("not found in spring context");
        } catch (Exception e) {
            LOG.warn("Failed to lookup Ignite bean ("+e.getMessage()+"), -> Try get default grid");
            try{
                return Ignition.ignite(); // never returns null
            }catch(Exception e2){
                LOG.warn("Failed to lookup default grid ("+e2.getMessage()+"), -> try first of 'allGrids'");
                List<Ignite> allGrids = Ignition.allGrids();
                if(allGrids.isEmpty()){
                    throw new IllegalStateException("No grid found: Ignition.allGrids() is empty");
                } else {
                    if( allGrids.size()>1){
                        LOG.warn("Multiple ("+allGrids.size()+") grids listed in Ignition.. Hope we get the right one...");
                    }
                    // Should we go for the first - or last ?
                    //
                    Ignite first = allGrids.get(0);
                    String name = first.name();
                    LOG.info("Returning first grid in allGrids; name=" + name);
                    return first;
                }
            }
        }
    }

    public static String getResultLocation(long runId) {
        return FileConstants.EXTERNAL_DATABASE_DIRECTORY + File.separator + "simulations" + File.separator + runId;
    }

    public static String getResultPathLocation(long runId, long pathId, long fieldId, long collectorId, int period) {
        return getResultLocation(runId) + File.separator + pathId + "_" + period + "_" + fieldId + "_" + collectorId;
    }
}
