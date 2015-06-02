package org.yamcs.ui.yamcsmonitor;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;

class YProcTableModel extends AbstractTableModel {
    private ArrayList<ProcessorInfo> yprocs=new ArrayList<ProcessorInfo>();

    public void removeProcessor(String instance, String name) {
        for ( int i = 0; i < yprocs.size(); ++i ) {
            ProcessorInfo ci = yprocs.get(i);
            if(ci.getInstance().equals(instance) && ci.getName().equals(name)) {
                yprocs.remove(i);
                fireTableRowsDeleted(i, i);
                break;
            }
        }
    }

    //inserts or updates a yprocessor
    //returns true if the yprocessor was not in the table before
    public boolean upsertYProc(ProcessorInfo ci) {
        boolean found=false;
        for ( int i = 0; i < yprocs.size(); ++i ) {
            ProcessorInfo ci1 = yprocs.get(i);
            if(ci.getInstance().equals(ci1.getInstance())&& ci.getName().equals(ci1.getName())) {
                yprocs.set(i, ci);
                fireTableRowsUpdated(i, i);
                found=true;
                break;
            }
        }
        if(!found) {
            yprocs.add(ci);
            int n=yprocs.size();
            fireTableRowsInserted(n-1, n-1);
        }
        return !found;
    }
    public ProcessorInfo getYProcessorInfo(int index) {
        return ((index >= 0) && (index < yprocs.size())) ? yprocs.get(index) : null;
    }

    public void clear() {
        int len = yprocs.size();
        if (len > 0) {
            yprocs.clear();
            fireTableRowsDeleted(0, len - 1);
        }
    }

    private static final long serialVersionUID = 4531138066222987136L;
    private static final String[] columnNames = {"Name", "Type", "Creator"};
    public String getColumnName(int col) { return columnNames[col]; }
    public int getRowCount() { return yprocs.size(); }
    public int getColumnCount() { return columnNames.length; }
    public Object getValueAt(int row, int col) {
        ProcessorInfo c = yprocs.get(row);
        Object o=null;
        switch (col) {
        case 0: o=c.getName(); break;
        case 1: o=c.getType(); break;
        case 2:	o=c.getCreator(); break;
        }
        return o;
    }
    public boolean isCellEditable(int row, int col) { return false; }
    public void setValueAt(Object value, int row, int col) {}
}