package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping
import groovy.transform.CompileStatic
import org.apache.ignite.Ignite
import org.apache.ignite.cluster.ClusterGroup
import org.apache.ignite.cluster.ClusterNode

@CompileStatic
class LocalNodesExcludingHeadStrategy extends LocalNodesStrategy {


    @Override
    ClusterGroup getUsableNodes(Ignite ignite) {
        ClusterNode localNode = ignite.cluster().localNode()
        return ignite.cluster().forHost(localNode).forOthers(localNode)
    }
}
