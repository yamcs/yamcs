package org.yamcs.ui.eventviewer;

import java.awt.Component;
import java.awt.Dimension;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.ui.PrefsObject;
import org.yamcs.ui.UiColors;

public class EventTable extends JTable {
    private static final long serialVersionUID = 1L;
    private static final ImageIcon INFO_ICON = new ImageIcon(EventTable.class.getResource("/org/yamcs/images/blank.png"));
    private static final ImageIcon WARNING_ICON = new ImageIcon(EventTable.class.getResource("/org/yamcs/images/warn.png"));
    private static final ImageIcon ERROR_ICON = new ImageIcon(EventTable.class.getResource("/org/yamcs/images/error.png"));
    private final EventTableRenderer renderer = new EventTableRenderer();
    private boolean inLayout;
    private Preferences uiPrefs;
    
    public EventTable(EventTableModel model) {
        super(model);
        setShowHorizontalLines(true);
        setIntercellSpacing(new Dimension(0, 0));

        uiPrefs = Preferences.userNodeForPackage(EventTable.class);
        Object columnOrder = PrefsObject.getObject(uiPrefs, "ColumnOrder");
        if (columnOrder != null) {
        	setColumnOrder((int[]) columnOrder);
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
    
    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (!isRowSelected(row)) {
            c.setBackground(getBackground());
            int modelRow = convertRowIndexToModel(row);
            Event event = (Event)getModel().getValueAt(modelRow, EventTableModel.EVENT_TEXT_COL);
            if(event.getSeverity()==EventSeverity.WARNING) {
                c.setBackground(UiColors.WARNING_FAINT_BG);
                //setSelectedTextColor(COLOR_WARNING_BG);
                //setDisabledTextColor(COLOR_WARNING_BG);
            } else if(event.getSeverity()==EventSeverity.ERROR) {
                c.setBackground(UiColors.ERROR_FAINT_BG);
            }
        }
        ((JComponent) c).setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, UiColors.BORDER_COLOR));
        return c;
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (convertColumnIndexToModel(column) == EventTableModel.EVENT_TEXT_COL) {
            return renderer;
        } else if (convertColumnIndexToModel(column) == EventTableModel.SOURCE_COL) {
            return new DefaultTableCellRenderer() {
                private static final long serialVersionUID = 1L;
                
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    super.getTableCellRendererComponent(table, value, isSelected, false /* no focus ! */, row, column);
                    int modelRow = convertRowIndexToModel(row);
                    Event event = (Event)getModel().getValueAt(modelRow, EventTableModel.EVENT_TEXT_COL);
                    if(event.getSeverity()==EventSeverity.WARNING) {
                        setIcon(WARNING_ICON);
                    } else if(event.getSeverity()==EventSeverity.ERROR) {
                        setIcon(ERROR_ICON);
                    } else if(event.getSeverity()==EventSeverity.INFO) {
                        setIcon(INFO_ICON);
                    }
                    return this;
                }
            };
        } else {
            // Disable blue focus border
            return new DefaultTableCellRenderer() {
                private static final long serialVersionUID = 1L;
                
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    return super.getTableCellRendererComponent(table, value, isSelected, false /* no focus ! */, row, column);
                }
            };
        }
    }
    
    private void setColumnOrder(int[] indices) {
    	// Remember all columns
        TableColumn[] columns = new TableColumn[columnModel.getColumnCount()];
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            columns[i] = columnModel.getColumn(i);
        }
        // Remove all
        while (columnModel.getColumnCount() > 0) {
            columnModel.removeColumn(columnModel.getColumn(0));
        }
        // Re-add, give preference to columns in indices[]
        for (int i = 0; i < indices.length; i++) {
        	if (indices[i] < columns.length) {
        		columnModel.addColumn(columns[indices[i]]);
        		columns[indices[i]] = null;
        	}
        }
        for (TableColumn column : columns) {
        	if (column != null) {
        		columnModel.addColumn(column);
        	}
        }
    }

    /*
     * Next few methods make sure that event-viewer columns can always
     * be resized, and that a horizontal scrollbar will appear if needed.
     * See http://stackoverflow.com/questions/15499255
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return hasExcessWidth();
    }

    @Override
    public void doLayout() {
        if (hasExcessWidth()) {
            // fool super
            autoResizeMode = AUTO_RESIZE_ALL_COLUMNS;
        }
        inLayout = true;
        super.doLayout();
        inLayout = false;
        autoResizeMode = AUTO_RESIZE_OFF;
    }


    protected boolean hasExcessWidth() {
        return getPreferredSize().width < getParent().getWidth();
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
        if (isEditing()) {
            // JW: darn - cleanup to terminate editing ...
            removeEditor();
        }
        TableColumn resizingColumn = getTableHeader().getResizingColumn();
        // Need to do this here, before the parent's
        // layout manager calls getPreferredSize().
        if (resizingColumn != null && autoResizeMode == AUTO_RESIZE_OFF && !inLayout) {
            resizingColumn.setPreferredWidth(resizingColumn.getWidth());
        }
        resizeAndRepaint();
    }

	public void storePreferences() {
		// Store column order
		int[] columnOrder=new int[columnModel.getColumnCount()];
		for(int i=0; i<columnModel.getColumnCount(); i++) {
			columnOrder[i] = columnModel.getColumn(i).getModelIndex();
		}
		PrefsObject.putObject(uiPrefs, "ColumnOrder", columnOrder);
	}
}
