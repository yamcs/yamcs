package org.yamcs.web.db;

import static org.yamcs.web.db.QueryDb.CNAME_ID;
import static org.yamcs.web.db.QueryDb.CNAME_NAME;
import static org.yamcs.web.db.QueryDb.CNAME_QUERY;
import static org.yamcs.web.db.QueryDb.CNAME_RESOURCE;
import static org.yamcs.web.db.QueryDb.CNAME_USER_ID;
import static org.yamcs.web.db.QueryDb.STRUCT_TYPE;

import java.util.UUID;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Struct;

public class Query implements Comparable<Query> {

    private final UUID id;
    private String resource;
    private String name;
    private Long userId;
    private Struct query;

    public Query(UUID id, String resource, String name, Long userId, Struct query) {
        this.id = id;
        this.resource = resource;
        this.name = name;
        this.userId = userId;
        this.query = query;
    }

    public Query(Tuple tuple) {
        id = tuple.getColumn(CNAME_ID);
        resource = tuple.getColumn(CNAME_RESOURCE);
        name = tuple.getColumn(CNAME_NAME);
        userId = tuple.getColumn(CNAME_USER_ID);
        query = tuple.getColumn(CNAME_QUERY);
    }

    public UUID getId() {
        return id;
    }

    public String getResource() {
        return resource;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Struct getQuery() {
        return query;
    }

    public void setQuery(Struct query) {
        this.query = query;
    }

    public Tuple toTuple() {
        var tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addColumn(CNAME_RESOURCE, resource);
        tuple.addColumn(CNAME_NAME, name);
        tuple.addColumn(CNAME_USER_ID, DataType.LONG, userId); // Nullable
        tuple.addColumn(CNAME_QUERY, STRUCT_TYPE, query);
        return tuple;
    }

    @Override
    public int compareTo(Query other) {
        return name.compareToIgnoreCase(other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
