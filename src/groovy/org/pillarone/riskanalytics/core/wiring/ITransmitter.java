package org.pillarone.riskanalytics.core.wiring;

import org.pillarone.riskanalytics.core.components.Component;
import org.pillarone.riskanalytics.core.packets.PacketList;

import java.io.Serializable;

public interface ITransmitter extends Serializable{

    public void transmit();

    public boolean isTransmitted();

    public void setTransmitted(boolean transmitted);

    public Component getSender();

    public Component getReceiver();

    public PacketList getTarget();

    public PacketList getSource();
}
