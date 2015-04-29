package org.yamcs.ui.yamcsmonitor;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.YamcsManagement.ClientInfo;

class ClientTableModel extends AbstractTableModel {
	private ArrayList<ClientInfo> clients=new ArrayList<ClientInfo>();

	public void clear() {
	    int len = clients.size();
	    
	    if(len>0) {
	        clients.clear();
	        fireTableRowsDeleted(0, len-1);
	    }
	}

	public void populate(List<ClientInfo> clientsInfo) {
		int len = clients.size();
		if ( len > 0 ) {
			clients.clear();
			fireTableRowsDeleted(0, len - 1);
		}

		for( ClientInfo ci:clientsInfo ) {
			clients.add(ci);
		}

		if ( clients.size() > 0 ) {
			fireTableRowsInserted(0, clients.size() - 1);
		}
	}
    /**
     * add or update a client to/in the table
     * @param clientInfo
     */
    public void updateClient(ClientInfo clientInfo) {
        boolean found=false;
        for ( int i = 0; i < clients.size(); ++i ) {
            ClientInfo ci = clients.get(i);
            if ( ci.getId() == clientInfo.getId() ) {
                clients.set(i, clientInfo);
                fireTableRowsUpdated(i, i);
                found=true;
                break;
            }
        }
        if(!found) {
            clients.add(clientInfo);
            int last = clients.size() - 1;
            fireTableRowsInserted(last, last);
        }
    }
    public ClientInfo get(int index) {
        return clients.get(index);
    }
    
	public void removeClient(int id) {
		for ( int i = 0; i < clients.size(); ++i ) {
			ClientInfo ci = clients.get(i);
			if ( ci.getId() == id ) {
				clients.remove(i);
				fireTableRowsDeleted(i, i);
				break;
			}
		}
	}

	private final String[] columnNames = {"Id", "User", "Application", "Instance", "Channel"};
	public String getColumnName(int col) { return columnNames[col]; }
	public int getRowCount() { return clients.size(); }
	public int getColumnCount() { return columnNames.length; }
	public Object getValueAt(int row, int col) {
		ClientInfo c=(ClientInfo) clients.get(row);
		Object o=null;
		switch (col) {
		case 0: o=c.getId(); break;
		case 1:	o=c.getUsername(); break;
		case 2:	o=c.getApplicationName(); break;
		case 3: o=c.getInstance(); break;
		case 4:	o=c.getProcessorName();
		}
		return o;
	}
	public boolean isCellEditable(int row, int col) { return false; }
	public void setValueAt(Object value, int row, int col) {}
}