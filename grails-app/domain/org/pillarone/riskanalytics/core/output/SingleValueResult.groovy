package org.pillarone.riskanalytics.core.output

import org.hibernate.FetchMode
import org.pillarone.riskanalytics.core.simulation.engine.grid.output.ResultWriter

class SingleValueResult {

    SimulationRun simulationRun
    int period
    int iteration
    PathMapping path
    CollectorMapping collector
    FieldMapping field
    int valueIndex
    Double value

    static constraints = {
        period min: 0
        iteration min: 0
        path()
        value()
        collector nullable:true
        field nullable:true
    }

    static mapping = {
        id generator: 'identity'
        path lazy: false, fetchMode: FetchMode.JOIN
    }

    String toString() {
        "${path.pathName}, ${field.fieldName}, $value"
    }

}
