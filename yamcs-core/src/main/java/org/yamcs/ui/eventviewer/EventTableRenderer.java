package org.yamcs.ui.eventviewer;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.TableCellRenderer;

import org.yamcs.protobuf.Yamcs.Event;

/**
 * Event table renderer class. Its purpose is to highlight the events with
 * severity WARNING and ERROR
 */
class EventTableRenderer extends JTextArea implements TableCellRenderer {
    private static final long serialVersionUID = 1L;

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

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Event event = (Event) value;
        switch (event.getSeverity())
        {
        case WARNING:
            setBackground(Color.YELLOW);
            setSelectedTextColor(Color.YELLOW);
            setDisabledTextColor(Color.YELLOW);
            break;
        case ERROR:
            setBackground(Color.RED);
            break;
        default:
            setBackground(null);
            break;
        }

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
        
        int height_wanted = (int) getPreferredSize().getHeight() + table.getIntercellSpacing().height;
        if (height_wanted != table.getRowHeight(row))
            table.setRowHeight(row, height_wanted);
        if (isSelected)
        {
            setForeground(table.getSelectionForeground());
            setBackground(table.getSelectionBackground());
        }
        else
        {
            switch (event.getSeverity())
            {
            case WARNING:
                setBackground(Color.YELLOW);
                break;
            case ERROR:
                setBackground(Color.RED);
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