package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import groovy.transform.CompileStatic
import org.apache.ignite.Ignite
import org.apache.ignite.cluster.ClusterGroup

@CompileStatic
class AllNodesStrategy extends AbstractNodeMappingStrategy {

    @Override
    ClusterGroup getUsableNodes(Ignite ignite) {
        return ignite.cluster()
    }
}
