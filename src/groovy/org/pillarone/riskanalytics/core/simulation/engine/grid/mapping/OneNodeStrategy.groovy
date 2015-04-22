package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping
import groovy.transform.CompileStatic
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
class OneNodeStrategy extends AbstractNodeMappingStrategy {

    @Override
    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes) {
        Set<ClusterNode> result = new HashSet<ClusterNode>()
        ClusterNode localNode = grid.cluster().localNode()
        if (allNodes.contains(localNode)) {
            result.add(localNode)
        }
        return result
    }


}
