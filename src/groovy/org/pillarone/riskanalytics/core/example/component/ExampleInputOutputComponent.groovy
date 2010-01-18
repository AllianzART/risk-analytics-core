package org.pillarone.riskanalytics.core.example.component

import org.pillarone.riskanalytics.core.components.Component
import org.pillarone.riskanalytics.core.example.parameter.ExampleParameterObject
import org.pillarone.riskanalytics.core.example.parameter.ExampleParameterObjectClassifier
import org.pillarone.riskanalytics.core.packets.Packet
import org.pillarone.riskanalytics.core.packets.PacketList

class ExampleInputOutputComponent extends Component {

    PacketList<Packet> outValue = new PacketList<Packet>()
    ExampleParameterObject parmParameterObject = ExampleParameterObjectClassifier.TYPE0.getParameterObject(["a": 0d, "b": 1d])

    protected void doCalculation() {

    }


}