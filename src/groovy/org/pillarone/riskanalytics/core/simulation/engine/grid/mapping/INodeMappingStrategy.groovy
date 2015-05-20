package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import org.apache.ignite.Ignite
import org.apache.ignite.cluster.ClusterGroup

public interface INodeMappingStrategy {

    ClusterGroup getUsableNodes(Ignite ignite)
}