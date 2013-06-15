package org.yamcs.ui.packetviewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.yamcs.ui.PacketListener;
import org.yamcs.ui.packetviewer.PacketViewer.ListPacket;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;

public class PacketsTable extends JTable implements ListSelectionListener, PacketListener {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Generation Time", "APID", "Opsname", "Size"};
    private static final String MARK_PACKET = "Mark packet";
    private static final String UNMARK_PACKET = "Unmark packet";

    private PacketViewer packetViewer;
    private JPopupMenu popup; 
    private JMenuItem markPacketMenuItem;
    private int maxLines = 1000;

    private Set<Integer> markedRows = new HashSet<Integer>(2);

    private Action markPacketAction;
    private Action unmarkPacketAction;

    public PacketsTable(PacketViewer packetViewer) {
        super();
        this.packetViewer = packetViewer;

        DefaultTableModel packetsModel = new DefaultTableModel(COLUMNS, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<?> getColumnClass(int columnIndex) { 
                if (columnIndex == 1 || columnIndex == 3)
                    return Number.class;
                return super.getColumnClass(columnIndex);
            }
        };
        setModel(packetsModel);

        setPreferredScrollableViewportSize(getPreferredSize());
        setFillsViewportHeight(true);
        getColumnModel().getColumn(0).setPreferredWidth(160);
        getColumnModel().getColumn(1).setPreferredWidth(50);
        getColumnModel().getColumn(2).setPreferredWidth(160);
        getColumnModel().getColumn(3).setPreferredWidth(50);

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setShowHorizontalLines(false);
        setGridColor(new Color(216, 216, 216));
        setIntercellSpacing(new Dimension(0, 0));
        setRowHeight(getRowHeight() + 2);

        TableRowSorter<DefaultTableModel> packetsSorter = new TableRowSorter<DefaultTableModel>(packetsModel);

        Comparator<Number> numberComparator = new Comparator<Number>() {
            @Override
            public int compare(Number o1, Number o2) {
                return o1.intValue() < o2.intValue() ? -1 : (o1.intValue() > o2.intValue() ? 1 : 0);
            }
        };
        packetsSorter.setComparator(1, numberComparator);
        packetsSorter.setComparator(3, numberComparator);
        setRowSorter(packetsSorter);

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

        DefaultTableCellRenderer numberRenderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table,
                    Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                return super.getTableCellRendererComponent(table, value,
                        isSelected, false /* disable focus ! */, row, column);
            }
        };
        numberRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        setDefaultRenderer(Number.class, numberRenderer);

        createActions();
        createPopupMenu();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        if (popup.isShowing() && isCellSelected(row, column)) {
            component.setBackground(new Color(216, 216, 216));
        } else if (!isCellSelected(row, column)) {
            if (markedRows.contains(row))
                component.setBackground(Color.YELLOW);
            else
                component.setBackground(Color.WHITE);
        }
        return component;
    }

    public void clear() {
        ((DefaultTableModel) getModel()).setRowCount(0);
        markedRows.clear();
    }

    public void addRow(Object[] rowData) {
        ((DefaultTableModel) getModel()).addRow(rowData);
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    private void createActions() {
        markPacketAction = new AbstractAction(MARK_PACKET) {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = getSelectedRow();
                if (row != -1) markedRows.add(row);
            }
        };

        unmarkPacketAction = new AbstractAction(UNMARK_PACKET) {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = getSelectedRow();
                if (row != -1) markedRows.remove(row);
            }
        };
    }

    private void createPopupMenu() {
        popup = new JPopupMenu();
        markPacketMenuItem = new JMenuItem(markPacketAction);
        popup.add(markPacketMenuItem);
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // Make sure selection background of entire row is updated
                // Not just the parts that were covered by the popup.
                repaint();
            }
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        MouseListener popupListener = new PopupListener();
        addMouseListener(popupListener);
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        super.valueChanged(e);
        if (!e.getValueIsAdjusting()) {
            int rowIndex = convertRowIndexToModel(getSelectedRow());
            if (rowIndex != -1) {
                // Reflect selection to mark/unmark actions
                if (markedRows.contains(rowIndex))
                    markPacketMenuItem.setAction(unmarkPacketAction);
                else
                    markPacketMenuItem.setAction(markPacketAction);
                packetViewer.setSelectedPacket((ListPacket)getModel().getValueAt(rowIndex, 2));
            }
        }
    }

    @Override
    public void packetReceived(CcsdsPacket c) {
        final ListPacket ccsds = packetViewer.new ListPacket(c.getByteBuffer());
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                DefaultTableModel packetsModel = (DefaultTableModel) getModel();
                addRow(new Object[] {
                        TimeEncoding.toCombinedFormat(ccsds.getInstant()),
                        ccsds.getAPID(),
                        ccsds,
                        ccsds.getCccsdsPacketLength() + 7
                });
                while (packetsModel.getRowCount() > maxLines) {
                    packetsModel.removeRow(0);
                }

                if (packetViewer.miAutoScroll.isSelected()) {
                    int row = convertRowIndexToModel(packetsModel.getRowCount() - 1);
                    Rectangle rect = getCellRect(row, 0, true);
                    scrollRectToVisible(rect);
                }
                if (packetViewer.miAutoSelect.isSelected()) {
                    int row = convertRowIndexToModel(packetsModel.getRowCount() - 1);
                    getSelectionModel().setSelectionInterval(row, row);
                }
            }
        });
    }

    @Override
    public void exception(final Exception e) {
        packetViewer.log(e.toString());
        System.out.println(e);
    }

    @Override
    public boolean isCanceled() {
        // never called in this app
        return false;
    }

    @Override
    public void replayFinished() {
        // TODO Auto-generated method stub        
    }

    private class PopupListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                int row = rowAtPoint(e.getPoint());
                if (row >= 0 && row < getRowCount())
                    setRowSelectionInterval(row, row);
                else
                    clearSelection();

                if (getSelectedRow() < 0)
                    return;
                if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                    repaint(); // !
                }
            }
        }
    }
}
