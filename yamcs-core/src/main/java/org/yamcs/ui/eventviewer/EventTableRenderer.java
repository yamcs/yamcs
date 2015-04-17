package org.yamcs.ui.eventviewer;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.TableCellRenderer;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.ui.UiColors;

/**
 * Event table renderer class. Its purpose is to highlight the events with
 * severity WARNING and ERROR
 */
class EventTableRenderer extends JTextArea implements TableCellRenderer {
    private static final long serialVersionUID=1L;

    @Override
    public void validate() {
    }

    @Override
    public void invalidate() {
    }

    @Override
    public void revalidate() {
    }

    @Override
    public void repaint() {
    }

    public void firePropertyChange() {
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    public EventTableRenderer() {
        super();
    }
    
    /**
     * Calculates the height required for showing this row. Extracted outside of
     * getTableCellRendererComponent, so that the height can be retrieved (and
     * set!) without the row having been rendered yet.
     */
    public int updateCalculatedHeight(JTable table, Object value, int row) {
        Event event = (Event) value;
        String[] lines=event.getMessage().split("\n");
        if(lines.length>5) {
            StringBuilder buf=new StringBuilder();
            for(int i=0;i<5;i++) {
                buf.append(lines[i]).append("\n");
            }
            buf.append("[truncated]");
            setText(buf.toString());
        } else {
            setText(event.getMessage());
        }
        
        int wantedHeight = (int) getPreferredSize().getHeight() + table.getIntercellSpacing().height;
        if (wantedHeight != table.getRowHeight(row)) {
            table.setRowHeight(row, wantedHeight);
        }
        return wantedHeight;
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        updateCalculatedHeight(table, value, row);
        if (isSelected) {
            setForeground(table.getSelectionForeground());
            setBackground(table.getSelectionBackground());
        } else {
            // This is a textarea, so does not follow the row layout set by prepareRenderer
            Event event = (Event) value;
            switch (event.getSeverity()) {
            case WARNING:
            	setForeground(UiColors.WARNING_FAINT_FG);
            	setBackground(UiColors.WARNING_FAINT_BG);
                break;
            case ERROR:
            	setForeground(UiColors.ERROR_FAINT_FG);
                setBackground(UiColors.ERROR_FAINT_BG);
                break;
            default:
                setForeground(table.getForeground());
                setBackground(table.getBackground());
                break;
            }
        }
        return this;
    }
}