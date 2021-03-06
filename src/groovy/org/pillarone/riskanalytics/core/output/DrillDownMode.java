package org.pillarone.riskanalytics.core.output;

import java.util.ArrayList;
import java.util.List;

public enum DrillDownMode {
    BY_SOURCE, BY_PERIOD, BY_TYPE, BY_UPDATEDATE, BY_CALENDARYEAR, BY_CAT_TYPE;

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

    public static List<DrillDownMode> getDrillDownModesByCalendarYear() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>();
        ((ArrayList<DrillDownMode>) drillDownModes).add(DrillDownMode.BY_CALENDARYEAR);
        return drillDownModes;
    }

    // Really belong in BY_UPDATEDATE
    //
    public static final String fromPastName = "From_Past";
    public static final String fromNextName = "From_Next_Year";
    public static final String fromFutureName = "From_Future";

    // Really belong in BY_CAT_TYPE
    //
    public static final String catType_Nat    = "Nat";
    public static final String catType_nonNat = "NonNat";
}
