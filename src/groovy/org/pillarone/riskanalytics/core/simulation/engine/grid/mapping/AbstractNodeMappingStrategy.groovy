package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import grails.util.Holders
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

@CompileStatic
abstract class AbstractNodeMappingStrategy implements INodeMappingStrategy {

    private static final String STRATEGY_CLASS_KEY = "nodeMappingStrategy"

    private static final Log LOG = LogFactory.getLog(AbstractNodeMappingStrategy)

    // Was dumb order of checking.. -D system property should take precedence over Config
    public static INodeMappingStrategy getStrategy() {
        Class strategy = null
        try {
            String mappingClassSystemProperty = System.getProperty(STRATEGY_CLASS_KEY)
            if ( mappingClassSystemProperty ) {
                LOG.info("Found -D$STRATEGY_CLASS_KEY=$mappingClassSystemProperty")
                strategy = Thread.currentThread().contextClassLoader.loadClass(mappingClassSystemProperty)
            } else {
                String mappingClassInConfig = Holders.config.get(STRATEGY_CLASS_KEY)
                strategy = Thread.currentThread().contextClassLoader.loadClass(mappingClassInConfig)
                if (!strategy) {
                    LOG.warn("no $STRATEGY_CLASS_KEY set in config - defaulting to LocalNodesStrategy")
                    return new LocalNodesStrategy()
                }
            }
            LOG.info("Using grid node mapping strategy: $strategy")
            return (INodeMappingStrategy) strategy.newInstance()
        } catch (Exception e) {
            LOG.error("failed to find $strategy. Switch to LocalNodesStrategy", e)
            return new LocalNodesStrategy()
        }
    }

}
