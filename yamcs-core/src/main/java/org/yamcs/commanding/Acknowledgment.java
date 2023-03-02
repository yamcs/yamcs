package org.yamcs.commanding;

import java.util.Objects;

/**
 * Meta information for a globally available acknowledgment.
 */
public class Acknowledgment {

    private final String name;
    private final String description;

    public Acknowledgment(String name) {
        this(name, null);
    }

    public Acknowledgment(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Acknowledgment)) {
            return false;
        }
        var other = (Acknowledgment) obj;
        return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
