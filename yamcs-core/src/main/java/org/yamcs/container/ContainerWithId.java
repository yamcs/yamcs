package org.yamcs.container;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.SequenceContainer;

public class ContainerWithId {
    SequenceContainer def; // The definition of the container
    NamedObjectId id; // The id used by the subscriber for referring to this container
    public ContainerWithId(SequenceContainer def, NamedObjectId id) {
        this.def = def;
        this.id = id;
    }
    public Object getContainer() {      
        return def;
    }
    public NamedObjectId getId() {
        return id;
    }
}