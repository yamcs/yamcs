package org.yamcs.ui.yamcsmonitor;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.ui.UiColors;

public class LinkTable extends JTable {
    private static final long serialVersionUID = 1L;
    private LinkTableModel linkTableModel;
    private LinkCellRenderer linkInfoRenderer;
    private DataCountCellRenderer dataCountRenderer;

    public LinkTable(LinkTableModel linkTableModel) {
        super(linkTableModel);
        this.linkTableModel = linkTableModel;
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoCreateRowSorter(true);
        linkInfoRenderer = new LinkCellRenderer(this);
        dataCountRenderer = new DataCountCellRenderer(this);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int row = rowAtPoint(e.getPoint());
        if (row == -1)
            return "";
        row = convertRowIndexToModel(row);
        return linkTableModel.getLinkInfo(row).getDetailedStatus();
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        final int modelColumn = convertColumnIndexToModel(column);
        if (modelColumn == 5) {
            return dataCountRenderer;
        } else {
            return linkInfoRenderer;
        }
    }

    private static class LinkCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        final Border statusBorder;
        final Border statusBorderSelected;
        final Font statusFont;
        final LinkTable linkTable;
        LinkCellRenderer(LinkTable linkTable) {
            this.linkTable = linkTable;
            statusBorder = BorderFactory.createMatteBorder(1, 1, 1, 1, linkTable.getBackground());
            statusBorderSelected = BorderFactory.createMatteBorder(1, 1, 1, 1, linkTable.getSelectionBackground());
            statusFont = linkTable.getFont().deriveFont(getFont().getSize() * .8f);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false /* Prevent blue focus border */, row, column);
            final int modelRow = linkTable.convertRowIndexToModel(row);
            final int modelColumn = linkTable.convertColumnIndexToModel(column);
            final LinkInfo info = linkTable.linkTableModel.getLinkInfo(modelRow);
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            if (info != null && modelColumn==4) {
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(statusFont);
                setBorder(isSelected ? statusBorderSelected : statusBorder);

                if(info.getDisabled()) {
                    setBackground(UiColors.DISABLED_FAINT_BG);
                    setForeground(UiColors.DISABLED_FAINT_FG);
                } else if ("OK".equals(info.getStatus())) {
                    if (linkTable.linkTableModel.isDataCountIncreasing(modelRow)) {
                        setBackground(UiColors.GOOD_BRIGHT_BG);
                        setForeground(UiColors.GOOD_BRIGHT_FG);
                    } else {
                        setBackground(UiColors.GOOD_FAINT_BG);
                        setForeground(UiColors.GOOD_FAINT_FG);
                    }
                } else {
                    setBackground(UiColors.ERROR_FAINT_BG);
                    setForeground(UiColors.ERROR_FAINT_FG);
                }
            } else if(modelColumn == 5) { // Data count
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
            }
            return this;
        }
    }

    /**
     * Adds some extra capabilities for the data count column to render big numbers in groups of three
     */
    private static final class DataCountCellRenderer extends LinkCellRenderer {
        private static final long serialVersionUID = 1L;
        private final NumberFormat formatter = NumberFormat.getInstance(Locale.US);

        DataCountCellRenderer(LinkTable linkTable) {
            super(linkTable);
        }

        @Override
        protected void setValue(Object value) {
            Object result = value;
            if (value != null) {
                Number numberValue = (Number) value;
                result = formatter.format(numberValue.doubleValue());
            }
            super.setValue(result);
        }
    }
}
