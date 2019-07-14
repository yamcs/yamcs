package org.yamcs.ui.yamcsmonitor;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;

@SuppressWarnings("serial")
public class LinkTableModel extends AbstractTableModel {

    private static final String[] columnNames = { "Name", "Type", "Spec", "Status", "In", "Out" };

    private ArrayList<LinkInfo> links = new ArrayList<>();
    private ArrayList<Long> lastDataInCountIncrease = new ArrayList<>();
    private ArrayList<Long> lastDataOutCountIncrease = new ArrayList<>();
    private ArrayList<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();

    ScheduledThreadPoolExecutor timer;

    public LinkTableModel(ScheduledThreadPoolExecutor timer) {
        this.timer = timer;
    }

    public void update(LinkInfo uli) {
        boolean found = false;
        for (int i = 0; i < links.size(); ++i) {
            LinkInfo li = links.get(i);
            if (li.getName().equals(uli.getName())) {
                if (uli.getDataInCount() > li.getDataInCount()) {
                    lastDataInCountIncrease.set(i, System.currentTimeMillis());
                    scheduleFireTableRowsUpdated(i);
                }
                if (uli.getDataOutCount() > li.getDataOutCount()) {
                    lastDataOutCountIncrease.set(i, System.currentTimeMillis());
                    scheduleFireTableRowsUpdated(i);
                }
                links.set(i, uli);
                fireTableRowsUpdated(i, i);
                found = true;
                break;
            }
        }
        if (!found) {
            links.add(uli);
            lastDataInCountIncrease.add(0L);
            lastDataOutCountIncrease.add(0L);
            scheduledFutures.add(null);
            fireTableRowsInserted(links.size() - 1, links.size() - 1);
        }
    }

    /**
     * schedule a fire rows updated , to change the color of the line if no data has been received in the last two
     * seconds
     * 
     * @param row
     */
    private void scheduleFireTableRowsUpdated(final int row) {
        ScheduledFuture<?> future = scheduledFutures.get(row);
        if (future != null) {
            future.cancel(false);
        }
        future = timer.schedule(() -> SwingUtilities.invokeLater(() -> fireTableRowsUpdated(row, row)), 2,
                TimeUnit.SECONDS);
        scheduledFutures.set(row, future);
    }

    public boolean isDataInCountIncreasing(int index) {
        return (System.currentTimeMillis() - lastDataInCountIncrease.get(index)) < 1500;
    }

    public boolean isDataOutCountIncreasing(int index) {
        return (System.currentTimeMillis() - lastDataOutCountIncrease.get(index)) < 1500;
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

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public int getRowCount() {
        return links.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int row, int col) {
        LinkInfo li = links.get(row);
        Object o = null;
        switch (col) {
        case 0:
            o = li.getName();
            break;
        case 1:
            o = li.getType();
            break;
        case 2:
            o = li.getSpec();
            break;
        case 3:
            o = li.getStatus();
            break;
        case 4:
            o = li.getDataInCount();
            break;
        case 5:
            o = li.getDataOutCount();
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
