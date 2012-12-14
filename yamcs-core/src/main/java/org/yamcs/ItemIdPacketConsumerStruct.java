package org.yamcs;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.SequenceContainer;

/**
 * Keeps triplets (ItemIdentification,PacketConsumer,PacketDefinition). We need these pairs because each consumer wants 
 *  to receive back packets together with the original itemidentification he has used for subscription 
 *  (for example one can subscribe to a packet based on opsname and another can subscribe to the same packet 
 *    based on pathname).
 * @author mache
 *
 */
public class ItemIdPacketConsumerStruct {
	public NamedObjectId id;
	public PacketConsumer consumer;
	public SequenceContainer def;
	public ItemIdPacketConsumerStruct(PacketConsumer consumer, NamedObjectId id, SequenceContainer def) {
		this.id=id;
		this.consumer=consumer;
		this.def=def;
	}
	public String toString() {
		return "(consumer="+consumer+", id="+id;
	}
}
