package org.pillarone.riskanalytics.core.simulation.engine;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.simulation.ILimitedPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.NotInProjectionHorizon;
import org.pillarone.riskanalytics.core.simulation.SimulationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: sparten
 * Date: 10/26/11
 * Time: 11:25 AM
 * To change this template use File | Settings | File Templates.
 */
public enum GlobalReportingFrequency {

    ANNUALLY {
        @Override
        public Map<Integer, List<DateTime>> getReportingDatesByPeriod(PeriodScope periodScope) {

            ILimitedPeriodCounter variableLengthPeriodCounter = (ILimitedPeriodCounter) periodScope.getPeriodCounter();
            int periods = variableLengthPeriodCounter.periodCount();

            Map<Integer, List<DateTime>> periodReportDates = new TreeMap<Integer, List<DateTime>>();

            for (int period = 1; period <= periods; period++) {
                ArrayList<DateTime> reportingDates = new ArrayList<DateTime>();
                DateTime reportingDate;
                try {
                    reportingDate = variableLengthPeriodCounter.startOfPeriod(period).minusMillis(1);
                } catch (NotInProjectionHorizon ex) {
                    throw new SimulationException("Failed to instantiate reporting dates", ex);
                }
                reportingDates.add(reportingDate);
                periodReportDates.put(period - 1, reportingDates);
            }

            return periodReportDates;

        }

        @Override
        public boolean isFirstReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope) {
            return true;
        }

    },
    DEFAULT {
        @Override
        public Map<Integer, List<DateTime>> getReportingDatesByPeriod(PeriodScope periodScope) {
            throw new SimulationException("Not implemented, reporting frequency default choice should never be selected");
        }

        @Override
        public boolean isFirstReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope) {
            throw new SimulationException("Not implemented, reporting frequency default choice should never be selected");
        }

    }, QUARTERLY {
        @Override
        public Map<Integer, List<DateTime>> getReportingDatesByPeriod(PeriodScope periodScope) {
                        ILimitedPeriodCounter variableLengthPeriodCounter = (ILimitedPeriodCounter) periodScope.getPeriodCounter();
            int periods = variableLengthPeriodCounter.periodCount();

            Map<Integer, List<DateTime>> periodReportDates = new TreeMap<Integer, List<DateTime>>();

            for (int period = 0; period <= periods - 1; period++) {
                ArrayList<DateTime> reportingDates = new ArrayList<DateTime>();
                DateTime periodEnd;
                DateTime periodStart;
                try {
                    periodStart = variableLengthPeriodCounter.startOfPeriod(period);
                    periodEnd = variableLengthPeriodCounter.startOfPeriod(period + 1);
                } catch (NotInProjectionHorizon ex) {
                    throw new IllegalArgumentException("Failed to instantiate reporting dates");
                }

                DateTime reportingDate = periodStart;
                int i = 1;
                while (periodStart.plusMonths(3*i).minusMillis(1).isBefore(periodEnd)) {
                    reportingDate = periodStart.plusMonths(3*i).minusMillis(1);
                    reportingDates.add(reportingDate);
                    i++;
                }
//                Make sure the final reporting date is the final day of the period.
                if(!reportingDate.isEqual(periodEnd.minusMillis(1))) {
                    reportingDates.add(periodEnd.minusMillis(1));
                }
                periodReportDates.put(period, reportingDates);
            }
            return periodReportDates;
        }

        @Override
        public boolean isFirstReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope) {
            try {
                return reportingDate.equals(periodScope.getPeriodCounter().startOfPeriod(period).plusMonths(3).minusMillis(1));
            } catch (NotInProjectionHorizon ex) {
                throw new SimulationException("Attemped to compare dates outside of simulation scope. Contact development", ex);
            }
        }
    }, MONTHLY {
        @Override
        public Map<Integer, List<DateTime>> getReportingDatesByPeriod(PeriodScope periodScope) {

            ILimitedPeriodCounter variableLengthPeriodCounter = (ILimitedPeriodCounter) periodScope.getPeriodCounter();
            int periods = variableLengthPeriodCounter.periodCount();

            Map<Integer, List<DateTime>> periodReportDates = new TreeMap<Integer, List<DateTime>>();

            for (int period = 0; period <= periods - 1; period++) {
                ArrayList<DateTime> reportingDates = new ArrayList<DateTime>();
                DateTime periodEnd;
                DateTime periodStart;
                try {
                    periodStart = variableLengthPeriodCounter.startOfPeriod(period);
                    periodEnd = variableLengthPeriodCounter.startOfPeriod(period + 1);
                } catch (NotInProjectionHorizon ex) {
                    throw new SimulationException("Failed to instantiate reporting dates", ex);
                }

                DateTime reportingDate = periodStart;
                int i = 1;
                while (periodStart.plusMonths(i).minusMillis(1).isBefore(periodEnd)) {
                    reportingDate = periodStart.plusMonths(i).minusMillis(1);
                    reportingDates.add(reportingDate);
                    i++;
                }
                //                Make sure the final reporting date is the final day of the period.
                if(!reportingDate.isEqual(periodEnd.minusMillis(1))) {
                    reportingDates.add(periodEnd.minusMillis(1));
                }
                periodReportDates.put(period, reportingDates);
            }
            return periodReportDates;
        }

        /**
         * Returns a boolean telling us whether or not this is the first reporting date in a model period.
         *
         * @param period - mode period
         * @param reportingDate - reprting date
         * @param periodScope -
         * @return boolean telling us whether or not this is the first reporting date in a model period.
         */

        @Override
        public boolean isFirstReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope) {
            try {
                return reportingDate.equals(periodScope.getPeriodCounter().startOfPeriod(period).plusMonths(1).minusMillis(1));
            } catch (NotInProjectionHorizon ex) {
                throw new SimulationException("Attemped to compare dates outside of simulation scope. Contact development", ex);
            }
        }
    };

    /**
     * Checks if the reporting date is the last reporting date in the period. In general the business logic may need to know this
     * in order to check whether or not to persist things in the period store
     *
     * @param period
     * @param reportingDate
     * @param periodScope
     * @return
     */
    public boolean isLastReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope) {
        try {
            return reportingDate.equals(periodScope.getPeriodCounter().endOfPeriod(period).minusMillis(1));
        } catch (NotInProjectionHorizon ex) {
            throw new SimulationException("Attemped to compare dates outside of simulation scope. Contact development", ex);
        }
    };

    /**
     * Returns a Map, keyed by model period, of reporting dates for each period. Each counter provides it's own dates.
     *
     * Each reporting period begins one millisecond period before the period starts, and ends one millisecond before the period end.
     * The annual reporting period for 2012 is therefore 2011.12.31 23.59.59.99 -> 2012.12.31 23.59.59.99
     *
     *
     * @param periodScope
     * @return Annually; last day of period. Quartely, startDate plus 3 months, then the last day... Monthly; first day of period plus months until there are no more months.
     */
    public abstract Map<Integer, List<DateTime>> getReportingDatesByPeriod(PeriodScope periodScope);

    public abstract boolean isFirstReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope);
}
