package org.pillarone.riskanalytics.core.output

import org.pillarone.riskanalytics.core.RiskAnalyticsResultAccessException

class CollectorMapping implements Serializable {

    String collectorName

    @Override
    String toString() {
        collectorName
    }

    static constraints = {
        collectorName(unique: true)
    }

    static CollectorMapping lookupCollector( long id ) {
        CollectorMapping collectorMapping = CollectorMapping.findById(id);
        return collectorMapping;
/*
        List<String> names = CollectorMapping.executeQuery(
                "SELECT x.collectorName " +
                        "  FROM org.pillarone.riskanalytics.core.output.CollectorMapping as x " +
                        " WHERE x.id = " + id
        );

        if(names == null){
            String w =  "Lookup problem with CollectorMapping id " + id;
            LOG.warn(w);
            throw new RiskAnalyticsResultAccessException(w);
        }

        if(names.isEmpty()){
            String w =  "No CollectorMapping found with id " + id;
            LOG.warn(w);
            throw new RiskAnalyticsResultAccessException(w);
        }

        return names.get(0);
*/
    }
}