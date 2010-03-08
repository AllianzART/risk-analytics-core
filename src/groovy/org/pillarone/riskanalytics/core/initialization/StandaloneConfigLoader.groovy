package org.pillarone.riskanalytics.core.initialization;

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.logging.Log4jConfig
import org.apache.log4j.LogManager

class StandaloneConfigLoader {

    static void loadLog4JConfig(String environment) {
        Closure configuration = null

        Class configClass = StandaloneConfigLoader.class.getClassLoader().loadClass(GrailsApplication.CONFIG_CLASS)

        ConfigSlurper configSlurper = new ConfigSlurper(environment)
        ConfigObject configObject = configSlurper.parse(configClass)

        if (configObject.containsKey("log4j") && configObject["log4j"] instanceof Closure) {
            configuration = configObject["log4j"]
        }

        LogManager.resetConfiguration();
        if (configuration != null) {
            new Log4jConfig().configure((Closure) configuration);
        } else {
            new Log4jConfig().configure();
        }
    }
}
