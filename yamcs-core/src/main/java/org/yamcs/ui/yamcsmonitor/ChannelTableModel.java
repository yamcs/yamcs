package org.yamcs.ui.yamcsmonitor;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.YamcsManagement.ChannelInfo;

class ChannelTableModel extends AbstractTableModel {
	private ArrayList<ChannelInfo> channels=new ArrayList<ChannelInfo>();

	public void removeChannel(String instance, String name) {
		for ( int i = 0; i < channels.size(); ++i ) {
			ChannelInfo ci = channels.get(i);
			if(ci.getInstance().equals(instance) && ci.getName().equals(name)) {
				channels.remove(i);
				fireTableRowsDeleted(i, i);
				break;
			}
		}
	}
	
	//inserts or updates a channel
	//returns true if the channel was not in the table before
	public boolean upsertChannel(ChannelInfo ci) {
	    boolean found=false;
	    for ( int i = 0; i < channels.size(); ++i ) {
            ChannelInfo ci1 = channels.get(i);
            if(ci.getInstance().equals(ci1.getInstance())&& ci.getName().equals(ci1.getName())) {
                channels.set(i, ci);
                fireTableRowsUpdated(i, i);
                found=true;
                break;
            }
        }
	    if(!found) {
	        channels.add(ci);
	        int n=channels.size();
	        fireTableRowsInserted(n-1, n-1);
	    }
	    return !found;
    }
	public ChannelInfo getChannelInfo(int index) {
		return ((index >= 0) && (index < channels.size())) ? channels.get(index) : null;
	}

	public void clear() {
		int len = channels.size();
		if (len > 0) {
			channels.clear();
			fireTableRowsDeleted(0, len - 1);
		}
	}

	private static final long serialVersionUID = 4531138066222987136L;
	private static final String[] columnNames = {"Name", "Type", "Creator"};
	public String getColumnName(int col) { return columnNames[col]; }
	public int getRowCount() { return channels.size(); }
	public int getColumnCount() { return columnNames.length; }
	public Object getValueAt(int row, int col) {
		ChannelInfo c = channels.get(row);
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