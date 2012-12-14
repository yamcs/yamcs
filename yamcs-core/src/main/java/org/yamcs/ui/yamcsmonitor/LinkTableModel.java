package org.yamcs.ui.yamcsmonitor;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;

class LinkTableModel extends AbstractTableModel {
    private ArrayList<LinkInfo> links=new ArrayList<LinkInfo>();
    private ArrayList<Long> lastDataCountIncrease=new ArrayList<Long>();
    private ArrayList<ScheduledFuture<?>> schduledFutures=new ArrayList<ScheduledFuture<?>>();
    
    ScheduledThreadPoolExecutor timer;
    public LinkTableModel(ScheduledThreadPoolExecutor timer) {
    	this.timer=timer;
    }
    
    
    public void update(LinkInfo uli) {
        boolean found=false;
        for ( int i = 0; i < links.size(); ++i ) {
            LinkInfo li = links.get(i);
            if(li.getName().equals(uli.getName())) {
                if(uli.getDataCount()>li.getDataCount()) {
                	lastDataCountIncrease.set(i, System.currentTimeMillis());
                	scheduleFireTableRowsUpdated(i);
                }
                links.set(i, uli);
                fireTableRowsUpdated(i, i);
                found=true;
                break;
            }
        }
        if(!found) {
            links.add(uli);
            lastDataCountIncrease.add(0L);
            schduledFutures.add(null);
            fireTableRowsInserted(links.size()-1, links.size()-1);
        }
    }
    
    /**
     * schedule a fire rows updated , to change the color of the line if no data has been received in the last two seconds
     * @param row
     */
    private void scheduleFireTableRowsUpdated(final int row) {
    	ScheduledFuture<?> future=schduledFutures.get(row);
    	if(future!=null)future.cancel(false);
    	future=timer.schedule(new Runnable() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						fireTableRowsUpdated(row, row);
					}
				});
				
			}
		}, 2, TimeUnit.SECONDS);
    	schduledFutures.set(row, future);
    }
    
    public boolean isDataCountIncreasing(int index) {
        return (System.currentTimeMillis()-lastDataCountIncrease.get(index))<1500;
    }
    
    public LinkInfo getLinkInfo(int index) {
        return ((index >= 0) && (index < links.size())) ? links.get(index) : null;
    }

    public void clear() {
        int len = links.size();
        if (len > 0) {
            links.clear();
            fireTableRowsDeleted(0, len - 1);
        }
    }

    private static final long serialVersionUID = 4531138066222987136L;
    private static final String[] columnNames = {"Name", "Type", "Spec", "Stream", "Status", "Data Count"};
    @Override
    public String getColumnName(int col) { return columnNames[col]; }
    @Override
    public int getRowCount() { return links.size(); }
    @Override
    public int getColumnCount() { return columnNames.length; }
    @Override
    public Object getValueAt(int row, int col) {
        LinkInfo li = links.get(row);
        Object o=null;
        switch (col) {
        case 0: o=li.getName(); break;
        case 1: o=li.getType(); break;
        case 2: o=li.getSpec(); break;
        case 3: o=li.getStream(); break;
        case 4: o=li.getStatus(); break;
        case 5: o=li.getDataCount(); break;
        }
        return o;
    }
    @Override
    public boolean isCellEditable(int row, int col) { return false; }
    @Override
    public void setValueAt(Object value, int row, int col) {}
}
