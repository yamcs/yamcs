package org.yamcs.ui.packetviewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import org.yamcs.ui.packetviewer.PacketViewer.Range;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ValueEnumeration;

public class ParametersTable extends JTable implements ListSelectionListener {

    private static final long serialVersionUID = 1L;
    private static final Color GRAYISH_COLOR = new Color(235, 235, 235);
    private static final String ADD_PARAMETER_TO_LEFT = "Apply as Left Column";
    
    private static final String[] COLUMNS = { "Name", "Eng Value",
        "Raw Value", "Nominal Low", "Nominal High", "Danger Low",
        "Danger High", "Bit Offset", "Bit Size", "Calibration" };

    private List<Integer> rowsWithSearchResults = new ArrayList<Integer>();

    private PacketViewer packetViewer;
    private String lastSearchTerm;

    private RightClickMenu rightClickMenu = new RightClickMenu();
    public ParametersTable(PacketViewer packetViewer) {
        super(new DefaultTableModel(COLUMNS, 0));
        this.packetViewer = packetViewer;
        setPreferredScrollableViewportSize(new Dimension(600, 400));
        setFillsViewportHeight(true);
        getSelectionModel().addListSelectionListener(this);
        setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        MouseListener linkListener = new MouseListener();
        addMouseListener(linkListener);
        addMouseMotionListener(linkListener);

        for (String colname : COLUMNS)
            getColumn(colname).setPreferredWidth(85);
        getColumnModel().getColumn(0).setPreferredWidth(300);
        setAutoResizeMode(AUTO_RESIZE_OFF);

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

                Component c;
                if (value instanceof EnumeratedParameterType) {
                    String name = ((EnumeratedParameterType) value).getName();
                    String link = String.format("<html><a href=\"#\">%s</a></html>", name);
                    c = super.getTableCellRendererComponent(table, link, isSelected, false /* disable focus ! */, row, column);
                } else if(value instanceof Parameter){
                    c = super.getTableCellRendererComponent(table, ((Parameter)value).getName(),  isSelected, false /* disable focus ! */, row, column);
                } else {
                    c = super.getTableCellRendererComponent(table, value,  isSelected, false /* disable focus ! */, row, column);
                }

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

    public void addRow(Object[] vec) {
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

    public SearchStats nextSearchResult(String searchTerm) {
        updateMatchingRows(searchTerm);

        SearchStats stats = null;
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

                stats = new SearchStats();
                stats.totalMatching = rowsWithSearchResults.size();
                stats.selectedMatch = rowsWithSearchResults.indexOf(nextIndex) + 1;
            }
        }

        lastSearchTerm = searchTerm;
        repaint();
        return (stats != null) ? stats : null;
    }

    public SearchStats previousSearchResult(String searchTerm) {
        updateMatchingRows(searchTerm);

        SearchStats stats = null;
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

                stats = new SearchStats();
                stats.totalMatching = rowsWithSearchResults.size();
                stats.selectedMatch = rowsWithSearchResults.indexOf(prevIndex) + 1;
            }
        }

        lastSearchTerm = searchTerm;
        repaint();
        return (stats != null) ? stats : null;
    }

    @SuppressWarnings("rawtypes")
    private void updateMatchingRows(String searchTerm) {
        if (!searchTerm.equals(lastSearchTerm)) {
            rowsWithSearchResults.clear();
            Vector rowData = ((DefaultTableModel) getModel()).getDataVector();
            for(int i = 0; i < rowData.size(); i++) {
                String opsName = ((Parameter)((Vector) rowData.get(i)).get(0)).getName();
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

    private class MouseListener extends MouseAdapter {
        
        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }
        
        @Override
        public void mouseClicked(MouseEvent e) {
            int column = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
            int row = rowAtPoint(e.getPoint());
            maybeShowPopup(e);
            if (column == 9 && row != -1) {
                Object val = getModel().getValueAt(row, column);
                if (val instanceof EnumeratedParameterType) {
                    EnumeratedParameterType type = (EnumeratedParameterType) val;

                    JPanel msgPanel = new JPanel(new GridLayout(0, 2));
                    for (ValueEnumeration v : type.getValueEnumerationList()) {
                        msgPanel.add(new JLabel("" + v.getValue()));
                        msgPanel.add(new JLabel(v.getLabel()));
                    }

                    int rawValue = Integer.valueOf((String) getModel().getValueAt(row, 2));

                    Object[][] rowData = new Object[type.getValueEnumerationList().size()][2];
                    int i = 0;
                    int preselectedRow = -1;
                    for (ValueEnumeration v : type.getValueEnumerationList()) {
                        rowData[i][0] = v.getValue();
                        rowData[i][1] = v.getLabel();
                        if ((Long) rowData[i][0] == rawValue)
                            preselectedRow = i;
                        i++;
                    }

                    JTable lov = new JTable(rowData, new String[] { "#", "Label" });
                    lov.setFillsViewportHeight(true);
                    lov.setShowVerticalLines(false);
                    lov.setIntercellSpacing(new Dimension(0, 0));
                    lov.setGridColor(new Color(216, 216, 216));
                    lov.getColumnModel().getColumn(0).setPreferredWidth(40);

                    // Set preferred width of label column according to largest content
                    int width = 0;
                    for (i = 0; i < lov.getRowCount(); i++) {
                        TableCellRenderer renderer = lov.getCellRenderer(i, 1);
                        Component c = lov.prepareRenderer(renderer, i, 1);
                        width = Math.max(c.getPreferredSize().width, width);
                    }
                    lov.getColumnModel().getColumn(1).setPreferredWidth(Math.max(width, 200));
                    lov.getTableHeader().setReorderingAllowed(false);
                    lov.setEnabled(false);

                    if (preselectedRow != -1)
                        lov.setRowSelectionInterval(preselectedRow, preselectedRow);

                    JScrollPane msgPanelScroll = new JScrollPane(lov);
                    int preferredWidth = Math.min(lov.getPreferredSize().width + 20, 600);
                    int preferredHeight = lov.getRowHeight() * Math.min(lov.getRowCount(), 20);
                    lov.setPreferredScrollableViewportSize(new Dimension(preferredWidth, preferredHeight));

                    String title = "List of Values · " + type.getName();
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    JOptionPane.showMessageDialog(getJFrameContainer(ParametersTable.this), msgPanelScroll, title, JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int column = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
            int row = rowAtPoint(e.getPoint());
            if (column == 9 && row != -1) {
                Object val = getModel().getValueAt(row, column);
                if (val instanceof EnumeratedParameterType) {
                    // It's an enumeration displayed as a link. Give a visual clue to the user
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
        private JFrame getJFrameContainer(JComponent component) {
            Container parent = component.getParent();
            if (parent instanceof JFrame)
                return (JFrame) parent;
            else
                return getJFrameContainer((JComponent) parent);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                int row = rowAtPoint(e.getPoint());
                if (row != -1) {
                    setRowSelectionInterval(row, row);
                    rightClickMenu.selectedParameter = (Parameter) getModel().getValueAt(row, 0);
                    rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        }

    }
    static class SearchStats {
        /**
         * row index of selected match (as visible to user)
         */
        int selectedMatch;

        /**
         * total matching search results
         */
        int totalMatching;
    }


    private class RightClickMenu extends JPopupMenu {
        Parameter selectedParameter;
        public RightClickMenu() {
                
            Action a = new AbstractAction(ADD_PARAMETER_TO_LEFT) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    packetViewer.addParameterToTheLeftTable(selectedParameter);
                }
            };
           add(a);
        }
    }
}
