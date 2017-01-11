package org.yamcs.api;

/**
 * Syntactic sugar around a media type string. Contains predefined ones, but is
 * open-ended for when we support dynamic rest handlers better.
 */
public final class MediaType {
    
    public static final MediaType OCTET_STREAM = new MediaType("application/octet-stream");
    public static final MediaType CSV = new MediaType("text/csv");
    public static final MediaType JAVA_SERIALIZED_OBJECT = new MediaType("application/x-java-serialized-object");
    public static final MediaType JSON = new MediaType("application/json");
    public static final MediaType PROTOBUF = new MediaType("application/protobuf");
    public static final MediaType PLAIN_TEXT = new MediaType("plain/text");

    private final String typeString;
    
    private MediaType(String typeString) {
        if (typeString == null) {
            throw new NullPointerException("typeString must not be null");
        }
        this.typeString = typeString;
    }
    
    public boolean is(String typeString) {
        return typeString != null && this.typeString.equals(typeString);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof MediaType)) return false;
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
    
    public static MediaType from(String typeString) {
        return new MediaType(typeString);
    }
}
