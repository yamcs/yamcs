package org.yamcs.plists;

import static org.yamcs.plists.ParameterListDb.CNAME_DESCRIPTION;
import static org.yamcs.plists.ParameterListDb.CNAME_ID;
import static org.yamcs.plists.ParameterListDb.CNAME_NAME;
import static org.yamcs.plists.ParameterListDb.CNAME_PATTERNS;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class ParameterList implements Comparable<ParameterList> {

    private final UUID id;
    private String name;
    private String description;
    private List<String> patterns = new ArrayList<>();

    public ParameterList(UUID id, String name) {
        this.id = id;
        this.name = Objects.requireNonNull(name);
    }

    public ParameterList(Tuple tuple) {
        id = tuple.getColumn(CNAME_ID);
        name = tuple.getColumn(CNAME_NAME);
        description = tuple.getColumn(CNAME_DESCRIPTION);

        if (tuple.getColumn(CNAME_PATTERNS) != null) {
            patterns.addAll(tuple.getColumn(CNAME_PATTERNS));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public Tuple toTuple() {
        var tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addColumn(CNAME_NAME, name);
        tuple.addColumn(CNAME_DESCRIPTION, description);
        if (!patterns.isEmpty()) {
            tuple.addColumn(CNAME_PATTERNS, DataType.array(DataType.STRING), patterns);
        }
        return tuple;
    }

    @Override
    public int compareTo(ParameterList other) {
        return name.compareToIgnoreCase(other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
