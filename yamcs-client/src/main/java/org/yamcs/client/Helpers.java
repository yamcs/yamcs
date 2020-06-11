package org.yamcs.client;

import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class Helpers {

    public static NamedObjectId toNamedObjectId(String name) {
        // Some API calls still require NamedObjectId objects, which are bothersome.
        // This method automatically generates them from a name which can either be the qualified name (preferred)
        // or some alias in the form NAMESPACE/NAME
        if (name.startsWith("/")) {
            return NamedObjectId.newBuilder().setName(name).build();
        } else {
            String[] parts = name.split("\\/", 1);
            if (parts.length < 2) {
                throw new IllegalArgumentException(String.format("'%s' is not a valid name."
                        + " Use fully-qualified names or, alternatively,"
                        + " an alias in the format NAMESPACE/NAME", name));
            }
            return NamedObjectId.newBuilder().setNamespace(parts[0]).setName(parts[1]).build();
        }
    }

    public static String toName(NamedObjectId id) {
        if (id.hasNamespace()) {
            return id.getNamespace() + "/" + id.getName();
        } else {
            return id.getName();
        }
    }
}
