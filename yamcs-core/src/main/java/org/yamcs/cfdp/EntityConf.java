package org.yamcs.cfdp;

import org.yamcs.yarch.Bucket;

public class EntityConf {
    final long id;
    final Bucket bucket;
    final String name;

    public EntityConf(long id, String name, Bucket bucket) {
        this.id = id;
        this.name = name;
        this.bucket = bucket;
    }

    public String toString() {
        return name + " [id=" + id + "]";
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

}
