package org.yamcs.alarms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The id of an event alarm is its (source,type)
 * <p>
 * This means if an alarm is active and an event is generated with the same (source,type) a new alarm will not be
 * created but the old one updated
 * 
 */
public class EventId {

    private static final Pattern QNAME_PATTERN = Pattern.compile("(.+)\\/([^\\/]+)");
    public static final String DEFAULT_NAMESPACE = "/yamcs/event/";

    private static final String CA_NAME = "/yamcs/event/CustomAlgorithm/";
    final String source;
    final String type;

    public EventId(String source, String type) {
        if (source == null) {
            throw new NullPointerException("Source cannot be null");
        }

        this.source = source;
        this.type = type;
    }

    public EventId(String qualifiedName) {
        if (qualifiedName.startsWith(CA_NAME)) {// FIXME: hack for events generated from custom algorithms
            this.source = "CustomAlgorithm";
            this.type = qualifiedName.substring(CA_NAME.length());
        } else if (qualifiedName.startsWith(DEFAULT_NAMESPACE)) {
            String withoutPrefix = qualifiedName.substring(DEFAULT_NAMESPACE.length());
            Matcher matcher = QNAME_PATTERN.matcher(withoutPrefix);
            if (matcher.matches()) {
                source = matcher.group(1);
                type = matcher.group(2);
            } else {
                source = withoutPrefix;
                type = null;
            }
        } else {
            Matcher matcher = QNAME_PATTERN.matcher(qualifiedName);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid qualified name '" + qualifiedName + "'");
            }
            source = matcher.group(1);
            type = matcher.group(2);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + source.hashCode();
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        EventId other = (EventId) obj;

        if (!source.equals(other.source)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (source.startsWith("/")) {
            return source + (type != null ? "/" + type : "");
        } else {
            return DEFAULT_NAMESPACE + source + (type != null ? "/" + type : "");
        }
    }
}
