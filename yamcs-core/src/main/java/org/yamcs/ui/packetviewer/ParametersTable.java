package org.yamcs.ui.packetviewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import org.yamcs.ui.packetviewer.PacketViewer.Range;

public class ParametersTable extends JTable implements ListSelectionListener {

    private static final long serialVersionUID = 1L;
    private static final Color GRAYISH_COLOR = new Color(235, 235, 235);

    private static final String[] COLUMNS = { "Opsname", "Eng Value",
        "Raw Value", "Nominal Low", "Nominal High", "Danger Low",
        "Danger High", "Bit Offset", "Bit Size", "Calibration" };

    private PacketViewer packetViewer;

    public ParametersTable(PacketViewer packetViewer) {
        super(new DefaultTableModel(COLUMNS, 0));
        this.packetViewer = packetViewer;

        setPreferredScrollableViewportSize(new Dimension(600, 400));
        getSelectionModel().addListSelectionListener(this);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        for (String colname : COLUMNS) {
            getColumn(colname).setPreferredWidth(85);
        }
        getColumnModel().getColumn(0).setPreferredWidth(200);
        getColumnModel().getColumn(9).setPreferredWidth(300);

        // Disable Grid
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));

        // Swing highlights the selected cell with an annoying blue border.
        // Disable this behaviour by using a custom cell renderer
        setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table,
                    Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                return super.getTableCellRendererComponent(table, value,
                        isSelected, false /* disable focus ! */, row, column);
            }
        });
    }

    public void clear() {
        ((DefaultTableModel) getModel()).setRowCount(0);
    }

    public void addRow(String[] vec) {
        ((DefaultTableModel) getModel()).addRow(vec);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        if (!isCellSelected(row, column)) {
            if (row % 2 == 0) {
                component.setBackground(GRAYISH_COLOR);
            } else {
                component.setBackground(Color.WHITE);
            }
        }
        return component;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        super.valueChanged(e);
        if (e.getSource() == getSelectionModel()) {
            int[] rows = getSelectedRows();
            Range[] bits = new Range[rows.length];
            for (int i = 0; i < rows.length; ++i) {
                bits[i] = packetViewer.new Range(Integer.parseInt((String)getModel().getValueAt(rows[i], 7)),
                        Integer.parseInt((String)getModel().getValueAt(rows[i], 8)));
            }
            packetViewer.highlightBitRanges(bits);
        }
    }
}
