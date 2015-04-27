package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping
import org.apache.ignite.cluster.ClusterNode

public interface INodeMappingStrategy {

    int getTotalCpuCount(List<ClusterNode> usableNodes)

    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes)

}