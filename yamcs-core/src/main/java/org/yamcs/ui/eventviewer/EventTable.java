package org.yamcs.ui.eventviewer;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

public class EventTable extends JTable {
    private static final long serialVersionUID = 1L;
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
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 4) {
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
