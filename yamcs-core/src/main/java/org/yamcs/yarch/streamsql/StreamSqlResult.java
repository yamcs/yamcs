package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class StreamSqlResult {

    private String[] header;
    private List<Object[]> rows = new ArrayList<>(); // TODO should probably be smarter about types

    HashMap<String, Object> params;

    /**
     * Constructors for results with 0 parameters
     */
    public StreamSqlResult() {

    }

    /**
     * Constructor for results with one parameter
     * 
     * @param p1name
     * @param p1value
     */
    public StreamSqlResult(String p1name, Object p1value) {
        params = new HashMap<>();
        params.put(p1name, p1value);
    }

    public Object getParam(String p) {
        return params.get(p);
    }

    public void setHeader(String... header) {
        this.header = header;
    }

    public void addRow(Object... data) {
        String[] dataStrings = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            String stringValue = data[i] != null ? data[i].toString() : null;
            dataStrings[i] = stringValue;
        }
        rows.add(dataStrings);
    }

    @Override
    public String toString() {
        if (params != null) {
            StringBuffer sb = new StringBuffer();
            for (Entry<String, Object> entry : params.entrySet()) {
                sb.append(entry.getKey() + "=" + entry.getValue());
            }
            return sb.toString();
        } else if (header != null) {
            ResultSetPrinter printer = new ResultSetPrinter(header);
            rows.forEach(row -> printer.addRow(row));
            return printer.toString();
        } else {
            return null;
        }
    }
}
