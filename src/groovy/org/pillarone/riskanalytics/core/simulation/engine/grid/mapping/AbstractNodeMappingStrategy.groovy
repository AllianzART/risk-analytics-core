package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import grails.util.Holders
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.ignite.Ignite
import org.apache.ignite.cluster.ClusterNode
import org.pillarone.riskanalytics.core.simulation.engine.grid.GridHelper

@CompileStatic
abstract class AbstractNodeMappingStrategy implements INodeMappingStrategy {

    public static final String STRATEGY_CLASS_SYSTEM_PROPERTY = "nodeMappingStrategy"
    public static final String STRATEGY_CLASS_KEY = STRATEGY_CLASS_SYSTEM_PROPERTY

    protected Ignite grid

    private static final Log LOG = LogFactory.getLog(AbstractNodeMappingStrategy)

    AbstractNodeMappingStrategy() {
        this.grid = GridHelper.getGrid()
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

    public static INodeMappingStrategy getStrategy() {
        try {
            Class strategy = (Class) Holders.config.get(STRATEGY_CLASS_KEY)
            if (!strategy) {
                LOG.warn("no strategy set in config -> fallback to LocalNodesStrategy")
                return new LocalNodesStrategy()
            }

            if (System.getProperty(STRATEGY_CLASS_SYSTEM_PROPERTY) != null) {
                String mappingClass = System.getProperty(STRATEGY_CLASS_SYSTEM_PROPERTY)
                strategy = Thread.currentThread().contextClassLoader.loadClass(mappingClass)
            }
            return (INodeMappingStrategy) strategy.newInstance()
        } catch (Exception e) {
            LOG.error("failed to find strategy. Switch to LocalNodeStrategy", e)
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
