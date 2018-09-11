package org.yamcs.server.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for outputting records in columnar fashion without harcoding column widths
 */
public class TableStringBuilder {

    private String[] header;
    private List<String[]> rows = new ArrayList<>();

    private int[] widths;

    public TableStringBuilder(String... header) {
        this.header = header;
        widths = new int[header.length];
        for (int i = 0; i < header.length; i++) {
            widths[i] = header[i].length();
        }
    }

    public void addLine(Object... data) {
        String[] dataStrings = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            String stringValue = data[i] != null ? data[i].toString() : "";
            dataStrings[i] = stringValue;
            if (data[i] != null && stringValue.length() > widths[i]) {
                widths[i] = stringValue.length();
            }
        }
        rows.add(dataStrings);
    }

    private String buildStringFormat() {
        StringBuilder fm = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            fm.append("%-").append(widths[i]).append("s");
            if (i < widths.length - 1) {
                fm.append("  "); // 2 spaces so that it is larger than column name with spaces
            }
        }
        return fm.toString();
    }

    @Override
    public String toString() {
        String fm = buildStringFormat();

        String hline = String.format(fm, (Object[]) header);
        StringBuilder buf = new StringBuilder(hline);

        for (String[] row : rows) {
            String line = String.format(fm, (Object[]) row);
            buf.append("\n").append(line);
        }

        return buf.toString();
    }
}
