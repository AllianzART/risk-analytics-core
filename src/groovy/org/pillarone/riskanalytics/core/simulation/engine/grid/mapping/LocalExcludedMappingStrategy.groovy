package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping
import groovy.transform.CompileStatic
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
class LocalExcludedMappingStrategy extends AbstractNodeMappingStrategy {

    @Override
    int getTotalCpuCount(List<ClusterNode> usableNodes) {
        return usableNodes.size() //exactly one job per external node
    }

    @Override
    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes) {
        return allNodes.findAll { !it.is(grid.cluster().localNode()) }.toSet()
    }
}
