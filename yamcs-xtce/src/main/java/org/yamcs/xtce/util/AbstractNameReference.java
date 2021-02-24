package org.yamcs.xtce.util;


public abstract class AbstractNameReference implements NameReference {
    protected final String ref;
    protected final Type type;

    public AbstractNameReference(String ref, Type type) {
        this.ref = ref;
        this.type = type;
    }

    public String getReference() {
        return ref;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "name: " + ref + " type: " + type;
    }
}
