package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping
import groovy.transform.CompileStatic
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
class AllNodesStrategy extends AbstractNodeMappingStrategy {

    @Override
    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes) {
        return allNodes.toSet()
    }


}
