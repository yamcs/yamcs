package org.yamcs.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Syntactic sugar around a media type string. Contains predefined ones, but is open-ended for when we support dynamic
 * rest handlers better.
 */
public final class MediaType {

    static final Map<String, MediaType> knownTypes = new HashMap<>();
    public static final MediaType OCTET_STREAM = new MediaType("application/octet-stream");
    public static final MediaType CSV = new MediaType("text/csv");
    public static final MediaType JSON = new MediaType("application/json");
    public static final MediaType PROTOBUF = new MediaType("application/protobuf");
    public static final MediaType PLAIN_TEXT = new MediaType("plain/text");
    public static final MediaType ANY = new MediaType("*/*");

    private final String typeString;

    private MediaType(String typeString) {
        this.typeString = Objects.requireNonNull(typeString);
        knownTypes.put(typeString, this);
    }

    public boolean is(String typeString) {
        return typeString != null && this.typeString.equals(typeString);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MediaType)) {
            return false;
        }
        return typeString.equals(((MediaType) obj).typeString);
    }

    @Override
    public int hashCode() {
        return typeString.hashCode();
    }

    @Override
    public String toString() {
        return typeString;
    }

    /**
     * Returns one of the static members (OCTET_STREAM, CSV, etc) if this is a known type or a new object if the type is
     * unknown.
     * 
     * @param typeString
     * @return the MediaType object corresponding to the type string
     */
    public static MediaType from(String typeString) {
        MediaType mt = knownTypes.get(typeString);
        if (mt != null) {
            return mt;
        }
        return new MediaType(typeString);
    }
}
