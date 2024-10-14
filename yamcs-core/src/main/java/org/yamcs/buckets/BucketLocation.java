package org.yamcs.buckets;

public record BucketLocation(String name, String description) {

    @Override
    public final String toString() {
        return name;
    }
}
