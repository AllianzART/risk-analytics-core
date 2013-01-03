package org.pillarone.riskanalytics.core.output

import org.pillarone.riskanalytics.core.packets.PacketList

public interface ICollectingModeStrategy extends Cloneable {

  //do not use a grails domain class here (performance)
  List<SingleValueResultPOJO> collect(PacketList results, boolean crashSimulationOnError) throws Exception

  String getDisplayName(Locale locale)

  String getIdentifier()

  void setPacketCollector(PacketCollector packetCollector)

  boolean isCompatibleWith(Class packetClass)

}
