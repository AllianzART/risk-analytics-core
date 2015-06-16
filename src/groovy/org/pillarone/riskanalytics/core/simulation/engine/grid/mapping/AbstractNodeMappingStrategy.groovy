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
        Class strategyClass = null
        String nodeMappingStrategyClassName = null
        try {
            nodeMappingStrategyClassName = System.getProperty(STRATEGY_CLASS_KEY)
            if ( nodeMappingStrategyClassName ) {
                LOG.info("Found -D$STRATEGY_CLASS_KEY=$nodeMappingStrategyClassName")
                strategyClass = Thread.currentThread().contextClassLoader.loadClass(nodeMappingStrategyClassName)
            } else {
                LOG.info("No system property -D$STRATEGY_CLASS_KEY; checking config..")
                if( Holders.config.get(STRATEGY_CLASS_KEY) instanceof Class ){
                    strategyClass = (Class) Holders.config.get(STRATEGY_CLASS_KEY)
                    LOG.info("Found $STRATEGY_CLASS_KEY class in Config.groovy: ${strategyClass?.getSimpleName()}")
                } else if (Holders.config.get(STRATEGY_CLASS_KEY) instanceof String ){
                    nodeMappingStrategyClassName = Holders.config.get(STRATEGY_CLASS_KEY)
                    LOG.info("Found $STRATEGY_CLASS_KEY classname in external config: $nodeMappingStrategyClassName")
                    strategyClass = Thread.currentThread().contextClassLoader.loadClass(nodeMappingStrategyClassName)
                }
                if (!strategyClass) {
                    LOG.warn("No $STRATEGY_CLASS_KEY found in any config src - Fallback to LocalNodesExcludingHeadStrategy")
                    return new LocalNodesExcludingHeadStrategy()
                }
            }
            LOG.info("Using grid node mapping strategy class: $strategyClass")
            return (INodeMappingStrategy) strategyClass.newInstance()
        } catch (Exception e) {
            LOG.error("Failed to find class named $nodeMappingStrategyClassName. Fallback to LocalNodesExcludingHeadStrategy", e)
            return new LocalNodesExcludingHeadStrategy()
        }
    }

}
