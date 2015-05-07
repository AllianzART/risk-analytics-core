package org.pillarone.riskanalytics.core.simulation.engine.grid;

import grails.util.Holders;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.pillarone.riskanalytics.core.FileConstants;

import java.io.File;

public class GridHelper {

    public static Ignite getGrid() {
        try {
            return Holders.getGrailsApplication().getMainContext().getBean("ignite", Ignite.class);
        } catch (Exception e) {
            return Ignition.start();
        }
    }

    public static String getResultLocation(long runId) {
        return FileConstants.EXTERNAL_DATABASE_DIRECTORY + File.separator + "simulations" + File.separator + runId;
    }

    public static String getResultPathLocation(long runId, long pathId, long fieldId, long collectorId, int period) {
        return getResultLocation(runId) + File.separator + pathId + "_" + period + "_" + fieldId + "_" + collectorId;
    }
}
