package org.yamcs.alarms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The id of an event alarm is its (source,type)
 * <p>
 * This means if an alarm is active and an event is generated with the same (source,type) a new alarm will not be
 * created but the old one updated
 * 
 * @author nm
 */
public class EventId {

    private static final Pattern QNAME_PATTERN = Pattern.compile("(.+)\\/([^\\/]+)");
    public static final String DEFAULT_NAMESPACE = "/yamcs/event/";

    final String source;
    final String type;

    public EventId(String source, String type) {
        this.source = source;
        this.type = type;
    }

    public EventId(String qualifiedName) {
        Matcher matcher = QNAME_PATTERN.matcher(qualifiedName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid qualified name '" + qualifiedName + "'");
        }
        source = matcher.group(1).replace(DEFAULT_NAMESPACE, "");
        type = matcher.group(2);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((source == null) ? 0 : source.hashCode());
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

        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
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
            return source + "/" + type;
        } else {
            return DEFAULT_NAMESPACE + source + "/" + type;
        }
    }
}
