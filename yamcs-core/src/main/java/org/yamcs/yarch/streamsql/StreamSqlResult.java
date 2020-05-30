package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;

public class StreamSqlResult {

    private String[] header;
    private List<Value[]> rows = new ArrayList<>();

    HashMap<String, Object> params;

    /**
     * Constructor for results with 0 parameters
     */
    public StreamSqlResult() {
    }

    /**
     * Constructor for results with one parameter
     */
    public StreamSqlResult(String p1name, Object p1value) {
        params = new HashMap<>();
        params.put(p1name, p1value);
    }

    public Object getParam(String p) {
        return params.get(p);
    }

    public List<String> getHeader() {
        return Arrays.asList(header);
    }

    public Iterable<Value[]> iterateRows() {
        return () -> rows.iterator();
    }

    public void setHeader(String... header) {
        this.header = header;
    }

    public void addRow(Value... data) {
        rows.add(data);
    }
}
