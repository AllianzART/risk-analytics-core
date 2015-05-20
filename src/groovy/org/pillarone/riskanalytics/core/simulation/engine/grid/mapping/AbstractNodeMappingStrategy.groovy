package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import grails.util.Holders
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.ignite.Ignite
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
abstract class AbstractNodeMappingStrategy implements INodeMappingStrategy {

    private static final String STRATEGY_CLASS_KEY = "nodeMappingStrategy"

    protected Ignite grid

    private static final Log LOG = LogFactory.getLog(AbstractNodeMappingStrategy)

    AbstractNodeMappingStrategy() {
        this.grid = Holders.getGrailsApplication().getMainContext().getBean("ignite", Ignite.class)
    }

    @Override
    int getTotalCpuCount(List<ClusterNode> usableNodes) {
        List<String> usedHosts = new ArrayList<String>();
        int processorCount = 0;
        for (ClusterNode node : usableNodes) {
            String ip = getAddress(node);
            if (!usedHosts.contains(ip)) {
                processorCount += node.metrics().getTotalCpus();
                usedHosts.add(ip);
            }
        }
        LOG.info("Found " + processorCount + " CPUs on " + usableNodes.size() + " nodes");
        return processorCount;
    }

    // Was dumb order of checking.. -D system property should take precedence over Config
    //
    public static INodeMappingStrategy getStrategy() {
        Class strategy = null
        try {
            String mappingClass = System.getProperty(STRATEGY_CLASS_KEY)
            if ( mappingClass ) {
                LOG.info("Found -D$STRATEGY_CLASS_KEY=$mappingClass")
                strategy = Thread.currentThread().contextClassLoader.loadClass(mappingClass)
            } else {
                strategy = (Class) Holders.config.get(STRATEGY_CLASS_KEY)
                if (!strategy) {
                    LOG.warn("no $STRATEGY_CLASS_KEY set in config - defaulting to LocalNodesStrategy")
                    return new LocalNodesStrategy()
                }
            }

            return (INodeMappingStrategy) strategy.newInstance()
        } catch (Exception e) {
            LOG.error("failed to find $strategy. Switch to LocalNodeStrategy", e)
            return new LocalNodesStrategy()
        }
    }

    protected static String getAddress(ClusterNode node) {
        if (!node.addresses().empty) {
            return node.addresses().iterator().next();
        }
        throw new IllegalStateException("Grid node ${node} does not have a physical address.")
    }

}
