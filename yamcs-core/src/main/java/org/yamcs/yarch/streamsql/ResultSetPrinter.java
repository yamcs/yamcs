package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for outputting records in columnar fashion without harcoding column widths
 */
public class ResultSetPrinter {

    private String[] header;
    private List<String[]> rows = new ArrayList<>();

    private int[] widths;

    public ResultSetPrinter(String... header) {
        this.header = header;
        widths = new int[header.length];
        for (int i = 0; i < header.length; i++) {
            widths[i] = header[i].length();
        }
    }

    public void addRow(Object... data) {
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
            fm.append("| ").append("%-").append(widths[i]).append("s").append(" ");
        }
        return fm.append("|").toString();
    }

    @Override
    public String toString() {
        String fm = buildStringFormat();

        StringBuilder buf = new StringBuilder();

        StringBuilder separatorb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            separatorb.append("+-");
            for (int j = 0; j < widths[i]; j++) {
                separatorb.append('-');
            }
            separatorb.append("-");
        }
        String separator = separatorb.append("+").toString();

        buf.append(separator);
        String hline = String.format(fm, (Object[]) header);
        buf.append("\n").append(hline);
        buf.append("\n").append(separator);

        for (String[] row : rows) {
            String line = String.format(fm, (Object[]) row);
            buf.append("\n").append(line);
        }
        buf.append("\n").append(separator);
        buf.append("\n").append(rows.size() + " rows");

        return buf.toString();
    }
}
