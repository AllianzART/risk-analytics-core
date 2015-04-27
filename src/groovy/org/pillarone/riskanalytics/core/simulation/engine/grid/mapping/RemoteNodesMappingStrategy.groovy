package org.pillarone.riskanalytics.core.simulation.engine.grid.mapping

import groovy.transform.CompileStatic
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
    Set<ClusterNode> filterNodes(List<ClusterNode> allNodes) {

        Set<ClusterNode> remoteNodes = new HashSet<ClusterNode>();

        ClusterNode localNode = grid.cluster().localNode();
        Collection<String> localAddresses = localNode.addresses();

        for (ClusterNode node in allNodes) {
            Collection<String> nodeAddresses = node.addresses()
            if (!nodeAddresses.any { localAddresses.contains(it) }) {
                remoteNodes.add(node);
            }
        }

        return remoteNodes;

    }
}
