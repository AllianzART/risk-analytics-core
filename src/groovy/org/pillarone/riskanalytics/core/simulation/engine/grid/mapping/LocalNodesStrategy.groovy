package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import groovy.transform.CompileStatic
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
class LocalNodesStrategy extends AbstractNodeMappingStrategy {

    @Override
    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes) {
        Set<ClusterNode> result = new HashSet<ClusterNode>()
        ClusterNode localNode = grid.cluster().localNode()
        Collection<String> localAddresses = localNode.addresses()
        for (ClusterNode node in allNodes) {
            Collection<String> remoteAddresses = node.addresses()
            if (remoteAddresses.any { localAddresses.contains(it) }) {
                result.add(node)
            }
        }

        return result
    }
}
