package org.yamcs;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.SequenceContainer;

/**
 * Keeps triplets (NamedObjectId,PacketConsumer,SequenceContainer). We need
 * these pairs because each consumer wants to receive back containers together
 * with the original itemidentification he has used for subscription (for
 * example one can subscribe to a container based on opsname and another can
 * subscribe to the same container based on pathname).
 * 
 * @author mache
 */
public class ItemIdPacketConsumerStruct {
    public NamedObjectId id;
    public PacketConsumer consumer;
    public SequenceContainer def;
    
    public long generationTime;
    public long acquisitionTime;

    public ItemIdPacketConsumerStruct(PacketConsumer consumer, NamedObjectId id, SequenceContainer def, long acquisitionTime, long generationTime) {
        this.id = id;
        this.consumer = consumer;
        this.def = def;
        this.acquisitionTime = acquisitionTime;
        this.generationTime = generationTime;
    }
    
    public ItemIdPacketConsumerStruct(PacketConsumer consumer, NamedObjectId id, SequenceContainer def) {
        this.id = id;
        this.consumer = consumer;
        this.def = def;
    }

    @Override
    public String toString() {
        return String.format("(consumer=%s, id=%s, def=%s, acq=%s, gen=%s)", consumer, id, def, acquisitionTime, generationTime);
    }
}
