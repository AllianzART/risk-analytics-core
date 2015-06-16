package org.pillarone.riskanalytics.core.output

public enum DrillDownMode {
    BY_SOURCE,
    BY_PERIOD,
    BY_TYPE,
    BY_UPDATEDATE {
        public static final String FROM_PAST = "From_Past";
        public static final String FROM_FUTURE = "From_Future";
    }

    static List<DrillDownMode> getDrillDownModesBySource() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>()
        drillDownModes.add(DrillDownMode.BY_SOURCE)
        return drillDownModes
    }

    static List<DrillDownMode> getDrillDownModesByPeriod() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>()
        drillDownModes.add(DrillDownMode.BY_PERIOD)
        return drillDownModes
    }

    static List<DrillDownMode> getDrillDownModesByType() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>()
        drillDownModes.add(DrillDownMode.BY_TYPE)
        return drillDownModes
    }
    static List<DrillDownMode> getDrillDownModesByUpdateDate() {
        List<DrillDownMode> drillDownModes = new ArrayList<DrillDownMode>()
        drillDownModes.add(DrillDownMode.BY_UPDATEDATE)
        return drillDownModes
    }
}