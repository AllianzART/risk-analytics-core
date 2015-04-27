package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping
import groovy.transform.CompileStatic
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
class LocalExcludeHeadNodeMappingStrategy extends LocalNodesStrategy {

    @Override
    int getTotalCpuCount(List<ClusterNode> usableNodes) {
        return usableNodes.size() //exactly one job per external node
    }

    @Override
    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes) {

        Set<ClusterNode> result = super.filterNodes(allNodes)

//        Remove the head node in the hopes that this preserves UI responsiveness.
        result.removeAll(allNodes.find { it.is(grid.cluster().localNode()) })
        return result
    }
}
