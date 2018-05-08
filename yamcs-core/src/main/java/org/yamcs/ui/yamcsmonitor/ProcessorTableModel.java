package org.yamcs.ui.yamcsmonitor;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;

@SuppressWarnings("serial")
public class ProcessorTableModel extends AbstractTableModel {

    private static final String[] columnNames = { "Name", "Type", "Creator" };

    private ArrayList<ProcessorInfo> processors = new ArrayList<>();

    public void removeProcessor(String instance, String name) {
        for (int i = 0; i < processors.size(); ++i) {
            ProcessorInfo ci = processors.get(i);
            if (ci.getInstance().equals(instance) && ci.getName().equals(name)) {
                processors.remove(i);
                fireTableRowsDeleted(i, i);
                break;
            }
        }
    }

    // inserts or updates a processor
    // returns true if the processor was not in the table before
    public boolean upsertProcessor(ProcessorInfo ci) {
        boolean found = false;
        for (int i = 0; i < processors.size(); ++i) {
            ProcessorInfo ci1 = processors.get(i);
            if (ci.getInstance().equals(ci1.getInstance()) && ci.getName().equals(ci1.getName())) {
                processors.set(i, ci);
                fireTableRowsUpdated(i, i);
                found = true;
                break;
            }
        }
        if (!found) {
            processors.add(ci);
            int n = processors.size();
            fireTableRowsInserted(n - 1, n - 1);
        }
        return !found;
    }

    public ProcessorInfo getProcessorInfo(int index) {
        return ((index >= 0) && (index < processors.size())) ? processors.get(index) : null;
    }

    public void clear() {
        int len = processors.size();
        if (len > 0) {
            processors.clear();
            fireTableRowsDeleted(0, len - 1);
        }
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public int getRowCount() {
        return processors.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int row, int col) {
        ProcessorInfo c = processors.get(row);
        Object o = null;
        switch (col) {
        case 0:
            o = c.getName();
            break;
        case 1:
            o = c.getType();
            break;
        case 2:
            o = c.getCreator();
            break;
        }
        return o;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return false;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
    }
}
