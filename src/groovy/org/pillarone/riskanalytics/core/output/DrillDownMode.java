package org.pillarone.riskanalytics.core.output;

import java.util.ArrayList;
import java.util.List;

public enum DrillDownMode {
    BY_SOURCE, BY_PERIOD, BY_TYPE, BY_UPDATEDATE;

    public static List<DrillDownMode> getDrillDownModesBySource() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>();
        ((ArrayList<DrillDownMode>) drillDownModes).add(DrillDownMode.BY_SOURCE);
        return drillDownModes;
    }

    public static List<DrillDownMode> getDrillDownModesByPeriod() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>();
        ((ArrayList<DrillDownMode>) drillDownModes).add(DrillDownMode.BY_PERIOD);
        return drillDownModes;
    }

    public static List<DrillDownMode> getDrillDownModesByType() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>();
        ((ArrayList<DrillDownMode>) drillDownModes).add(DrillDownMode.BY_TYPE);
        return drillDownModes;
    }

    public static List<DrillDownMode> getDrillDownModesByUpdateDate() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>();
        ((ArrayList<DrillDownMode>) drillDownModes).add(DrillDownMode.BY_UPDATEDATE);
        return drillDownModes;
    }

    public static final String fromPastName = "From_Past";
    public static final String fromFutureName = "From_Future";
}
