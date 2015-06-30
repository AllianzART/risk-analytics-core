package org.pillarone.riskanalytics.core.simulation.engine;

import org.joda.time.DateTime;
import org.joda.time.Months;
import org.pillarone.riskanalytics.core.packets.Packet;
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

        public int getDeltaMonths() {
            return 12;
        }

        @Override
        public Map<Integer, List<DateTime>> getReportingDatesByPeriod(PeriodScope periodScope) {

            ILimitedPeriodCounter variableLengthPeriodCounter = (ILimitedPeriodCounter) periodScope.getPeriodCounter();
            int periods = variableLengthPeriodCounter.periodCount();

            Map<Integer, List<DateTime>> periodReportDates = new TreeMap<Integer, List<DateTime>>();

            for (int period = 1; period <= periods; period++) {
            // this thing just ASSUMES periods are annual!!!!! And it has checks for non-annual length periods elsewhere...
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

    },QUARTERLY {

        public int getDeltaMonths() {
            return 3;
        }

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

        public int getDeltaMonths() {
            return 1;
        }

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
    //toDo: turn this into a concrete method that calls the abstract method getReportinDatesForPeriod, which contains all the item-specific logic
    // ... Would have done this in this commit but I want the team (including me) to test my code and see it reproduces the results of Simon's
    //
    //Other note: return type should probably be different:
    //Since the Map keys are integers, spanning over the full [0, n] interval, an ArrayList would probably make more sense.
    //I also think that Iterators over DateTime, rather than precalculated Lists, might be a good choice for the value type...
    //If java had a "yield return" construct the code would be almost ready - need to check for syntactic sugar over iterators

    public List<DateTime> getReportingDatesForCurrentPeriod(PeriodScope periodScope) {

        return getReportingDatesForPeriod(periodScope.getCurrentPeriodStartDate(), periodScope.getNextPeriodStartDate());
    }


    public abstract int getDeltaMonths();


    public int getNumberofReportsInPeriod(DateTime periodStart, DateTime periodEnd) {

        final int delta = this.getDeltaMonths();

        int result = Months.monthsBetween(periodStart, periodEnd).getMonths();

        if (periodStart.plusMonths(result).isBefore(periodEnd)) ++result;

        return result;

    }

    public List<DateTime> getReportingDatesForPeriod(DateTime periodStart, DateTime periodEnd) {

        ArrayList<DateTime> outputList = new ArrayList<DateTime>(getNumberofReportsInPeriod(periodStart, periodEnd));

        final int delta = this.getDeltaMonths();

        periodEnd = periodEnd.minusMillis(1); //little trick to ensure this is always in with the minimum number of checks
        periodStart = periodStart.minusMillis(1);
        int i = 1; //see comment to the step increment below. Maybe there's a nicer idiom for this...
        for (DateTime reportingDate = periodStart.plusMonths(delta);
             reportingDate.isBefore(periodEnd);
             reportingDate = periodStart.plusMonths(++i*delta)) { //dateTime.plusMonths(2) is not always the same as dateTime.plusMonths(1).plusMonths(1). Hence the awkward use of i.
            outputList.add(reportingDate);
        }


        outputList.add(periodEnd);

        //this should be exactly the same loop that had been implemented more verbosely and with more checks by  Simon

        return outputList;

    }

    int reportingDateIndex(DateTime date, DateTime periodStart) {
        return Months.monthsBetween(periodStart,date).getMonths() / getDeltaMonths();
    }

    public List<List<Packet>> PartitionByReportingDate(List<Packet> packetList, DateTime periodStart, DateTime periodEnd) {

        final int numberOfReportingDates = getNumberofReportsInPeriod(periodStart, periodEnd);
        List<List<Packet>> result = new ArrayList<List<Packet>>(numberOfReportingDates);

        //Pre-initialize the lists... Is all this pre-allocation overkill?

        final int preallocationSize = 2 * packetList.size() / numberOfReportingDates;
        // the margin should reduce the number of list reallocations...

        for (int i = 0; i<numberOfReportingDates; ++i) {
            result.add(new ArrayList<Packet>(preallocationSize));
        }

        //now allocate the packets

        for (Packet packet: packetList) {
            try {
                result.get(reportingDateIndex(packet.getDate(),periodStart)).add(packet);
            } catch (IndexOutOfBoundsException e) {
                //Just bury it for the time being - would like to log and go on but it's slightly involved this time of the night
                //
                //Idea here is run
                if (reportingDateIndex(packet.getDate(),periodStart) > 0) { //if out of right bound add to last
                    result.get(result.size() - 1).add(packet); //add to last
                } else { //else add to first
                    result.get(0).add(packet);
                }
            }
        }

        return result;

    }



    public abstract boolean isFirstReportDateInPeriod(Integer period, DateTime reportingDate, PeriodScope periodScope);
}
