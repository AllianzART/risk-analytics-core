package org.pillarone.riskanalytics.core.parameterization

import org.pillarone.riskanalytics.core.parameterization.IParameterObject

/**
 * Note: all implementations must provide a static method valueOf(String)
 *
 * FR - Doh.. Why then is valueOf() not specified in the interface?!
 */
interface IParameterObjectClassifier extends Serializable {

    List getParameterNames()

    List<IParameterObjectClassifier> getClassifiers()

    def getType(parameterName)

    IParameterObject getParameterObject(Map parameters)

    public String getConstructionString(Map parameters)
}