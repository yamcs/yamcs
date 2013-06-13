package org.yamcs.ui.packetviewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

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

    private List<Integer> rowsWithSearchResults = new ArrayList<Integer>();

    private PacketViewer packetViewer;
    private String lastSearchTerm;

    public ParametersTable(PacketViewer packetViewer) {
        super(new DefaultTableModel(COLUMNS, 0));
        this.packetViewer = packetViewer;

        setPreferredScrollableViewportSize(getPreferredSize());
        getSelectionModel().addListSelectionListener(this);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        for (String colname : COLUMNS) {
            getColumn(colname).setPreferredWidth(85);
        }
        getColumnModel().getColumn(0).setPreferredWidth(200);
        getColumnModel().getColumn(9).setPreferredWidth(500);

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
                Component c = super.getTableCellRendererComponent(table, value,
                        isSelected, false /* disable focus ! */, row, column);

                // Highlight search results
                if (!rowsWithSearchResults.isEmpty()) {
                    int rowIndex = convertRowIndexToModel(row);
                    if (rowsWithSearchResults.contains(rowIndex))
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                }

                return c;
            }
        });
    }

    public void clear() {
        ((DefaultTableModel) getModel()).setRowCount(0);
        clearSearchResults();
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

    public void nextSearchResult(String searchTerm) {
        updateMatchingRows(searchTerm);

        if (!rowsWithSearchResults.isEmpty()) {
            // Always search up/down relative to current selected row
            int relpos = getSelectedRow();

            // First, set a reasonable default for nextIndex
            int nextIndex = rowsWithSearchResults.get(0);
            for (int index : rowsWithSearchResults) {
                if (index > relpos) {
                    nextIndex = index;
                    break;
                }
            }

            // Now finetune it
            if (rowsWithSearchResults.contains(relpos)) {
                int x = rowsWithSearchResults.indexOf(relpos);
                if (x < rowsWithSearchResults.size() - 1) {
                    nextIndex = rowsWithSearchResults.get(x + 1);
                } else if (x == rowsWithSearchResults.size() - 1) {
                    nextIndex = rowsWithSearchResults.get(0); // Circulate
                }
            }

            if (nextIndex != relpos) {
                setRowSelectionInterval(nextIndex, nextIndex);
                scrollRectToVisible(getCellRect(nextIndex, 0, true));
            }
        }

        lastSearchTerm = searchTerm;
        repaint();
    }

    public void previousSearchResult(String searchTerm) {
        updateMatchingRows(searchTerm);

        if (!rowsWithSearchResults.isEmpty()) {
            // Always search up/down relative to current selected row
            int relpos = getSelectedRow();

            // First, set a reasonable default for prevIndex
            int prevIndex = rowsWithSearchResults.get(0);
            for (int i=rowsWithSearchResults.size() - 1; i >= 0; i--) {
                int index = rowsWithSearchResults.get(i);
                if (index < relpos) {
                    prevIndex = index;
                    break;
                }
            }

            // Now finetune it
            if (rowsWithSearchResults.contains(relpos)) {
                int x = rowsWithSearchResults.indexOf(relpos);
                if (x > 0) {
                    prevIndex = rowsWithSearchResults.get(x - 1);
                } else if (x == 0) {
                    prevIndex = rowsWithSearchResults.get(rowsWithSearchResults.size() - 1); // Circulate
                }
            }

            if (prevIndex != relpos) {
                setRowSelectionInterval(prevIndex, prevIndex);
                scrollRectToVisible(getCellRect(prevIndex, 0, true));
            }
        }

        repaint();
    }

    @SuppressWarnings("rawtypes")
    private void updateMatchingRows(String searchTerm) {
        if (!searchTerm.equals(lastSearchTerm)) {
            rowsWithSearchResults.clear();
            Vector rowData = ((DefaultTableModel) getModel()).getDataVector();
            for(int i = 0; i < rowData.size(); i++) {
                String opsName = (String)((Vector) rowData.get(i)).get(0);
                if (opsName.toLowerCase().contains(searchTerm)) {
                    rowsWithSearchResults.add(i);
                }
            }
        }

        lastSearchTerm = searchTerm;
    }

    public void clearSearchResults() {
        rowsWithSearchResults.clear();
        lastSearchTerm = null;
    }
}
