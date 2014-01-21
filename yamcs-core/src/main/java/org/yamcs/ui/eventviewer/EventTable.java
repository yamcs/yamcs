package org.yamcs.ui.eventviewer;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;

public class EventTable extends JTable {
    private static final long serialVersionUID = 1L;
    static final Color COLOR_ERROR_BG=new Color(255, 221, 221);
    static final Color COLOR_WARNING_BG=new Color(248, 238, 199);
    private final EventTableRenderer renderer = new EventTableRenderer();
    
    public EventTable(EventTableModel model) {
        super(model);
//        getTableHeader().addMouseListener(new MouseAdapter() {
//
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                JTableHeader h = (JTableHeader) e.getSource();
//                int i = h.columnAtPoint(e.getPoint());
//                Object o = h.getColumnModel().getColumn(i).getHeaderValue();
//                if (i < 0 || o == selectedColumn) {
//                    selectedColumn = null;
//                    return;
//                }
//                selectedColumn = o;
//                h.requestFocusInWindow();
//            }
//        });
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
            Event event = (Event)getModel().getValueAt(modelRow, 4);
            if(event.getSeverity()==EventSeverity.WARNING) {
                c.setBackground(COLOR_WARNING_BG);
                //setSelectedTextColor(COLOR_WARNING_BG);
                //setDisabledTextColor(COLOR_WARNING_BG);
            } else if(event.getSeverity()==EventSeverity.ERROR) {
                c.setBackground(COLOR_ERROR_BG);
            }
        }
        return c;
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (convertColumnIndexToModel(column) == 4) {
            return renderer;
        }
        // Disable blue focus border
        return new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                return super.getTableCellRendererComponent(table, value, isSelected, false /* no focus ! */, row, column);
            }
        };
    }
}
