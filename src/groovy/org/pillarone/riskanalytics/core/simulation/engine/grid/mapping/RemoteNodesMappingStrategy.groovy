package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import groovy.transform.CompileStatic
import org.apache.ignite.Ignite
import org.apache.ignite.cluster.ClusterGroup
import org.apache.ignite.cluster.ClusterNode

/**
 * For use when no local gridnodes should be given work, only remote nodes.
 * Not much use unless we can minimise the classloading over the network.
 *
 * User: frahman
 * Date: 21.10.13
 * Time: 16:45
 */
@CompileStatic
class RemoteNodesMappingStrategy extends AbstractNodeMappingStrategy {

    @Override
    ClusterGroup getUsableNodes(Ignite ignite) {
        ClusterNode localNode = ignite.cluster().localNode()
        ClusterGroup localNodes = ignite.cluster().forHost(localNode)
        return ignite.cluster().forOthers(localNodes)
    }
}
