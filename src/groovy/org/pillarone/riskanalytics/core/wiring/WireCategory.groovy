package org.pillarone.riskanalytics.core.wiring

import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.pillarone.riskanalytics.core.components.Component
import org.pillarone.riskanalytics.core.packets.PacketList
import org.pillarone.riskanalytics.core.util.GroovyUtils

class WireCategory {

    static final Logger LOG = Logger.getLogger(PortReplicatorCategory)

    private static ThreadLocal<IPacketListener> packetListener = new ThreadLocal<IPacketListener>() {

        @Override
        protected IPacketListener initialValue() {
            return null;
        }

    };

    public static void setPacketListener(IPacketListener packetListener){
        this.packetListener.set(packetListener);
    }

    static void doSetProperty(Component target, String targetPropertyName, Object sender) {
        // guarded clause to check that only input input channels are wired
        if (!targetPropertyName.startsWith("in")) {
            target."${GrailsClassUtils.getSetterName(targetPropertyName)}"(sender)
            return
        }
        assert sender in LinkedProperty, "${WireCategory.class.simpleName}: Only objects of class LinkedProperty can be wired but was: " + sender.dump()

        def source = ((LinkedProperty) sender).source
        if(source == null){
            throw new IllegalArgumentException("source should not be null")
        }
        def sourcePropertyName = ((LinkedProperty) sender).name
        try {
            PacketList sourceProperty = GroovyUtils.getProperties(source)[sourcePropertyName] //2014-04-22 NPE occurred here during cancellation of sim
            PacketList targetProperty = GroovyUtils.getProperties(target)[targetPropertyName]
            if (!targetProperty.isCompatibleTo(sourceProperty)) {
                throw new IllegalArgumentException("Wiring only allowed with same types for input and output $sender -> $target ($targetPropertyName)")
            }
            Transmitter transmitter = createTransmitter(sourceProperty, source, targetProperty, target)
            if (packetListener.get()!=null){
                transmitter=new TraceableTransmitter(transmitter,packetListener.get());
            }
            target.allInputTransmitter << transmitter
            source.allOutputTransmitter << transmitter
        } catch (Throwable t) {
            throw new WiringException("doSetProperty failed, sourcePropertyName: " + sourcePropertyName + ", target: " + target + ", targetPropertyName: " + targetPropertyName + ", msg: " + t.getMessage(), t);
        }
    }

    protected static Transmitter createTransmitter(PacketList sourceProperty, Component source, PacketList targetProperty, Component target) {
        return new Transmitter(source, sourceProperty, target, targetProperty)
    }

    static doGetProperty(Component self, String name) {
        if (!name.startsWith("out")) {
            try { return self."${GrailsClassUtils.getGetterName(name)}"() }
            catch (e) {
                LOG.debug "resolving $name via propertyMissing"
                return self.propertyMissing(name)
            }
        }
        return new LinkedProperty(source: self, name: name)
    }
}
