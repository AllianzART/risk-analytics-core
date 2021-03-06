package org.pillarone.riskanalytics.core

import groovy.transform.CompileStatic

/**
 * Used to indicate that retrieving results has failed for some reason
 */
@CompileStatic
class RiskAnalyticsResultAccessException extends RuntimeException {

    RiskAnalyticsResultAccessException(String message) {
        super(message)
    }

    RiskAnalyticsResultAccessException(String message, Throwable cause) {
        super(message, cause)
    }
}
