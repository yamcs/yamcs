package org.yamcs.cli;

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

    public TableStringBuilder(int cols) {
        widths = new int[cols];
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

        String hline = header != null ? String.format(fm, (Object[]) header) + "\n" : "";
        StringBuilder buf = new StringBuilder(hline);
        boolean first = true;
        for (String[] row : rows) {
            if (!first) {
                buf.append("\n");
            }
            String line = String.format(fm, (Object[]) row);
            buf.append(line);
            first = false;
        }

        return buf.toString();
    }
}
