package org.yamcs.ui.packetviewer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
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
    private static final String[] COLUMNS = {"#", "Generation Time", "APID", "Opsname", "Size"};
    private static final String MARK_PACKET = "Mark Packet";
    private static final String UNMARK_PACKET = "Unmark Packet";
    private static final Color LIGHT_GRAY = new Color(216, 216, 216);

    // Expose action keys (for easier installing in JMenuBar)
    public static final String TOGGLE_MARK_ACTION_KEY = "toggle-mark";
    public static final String GO_TO_PACKET_ACTION_KEY = "go-to-packet";
    public static final String BACK_ACTION_KEY = "back";
    public static final String FORWARD_ACTION_KEY = "forward";
    public static final String UP_ACTION_KEY = "up";
    public static final String DOWN_ACTION_KEY = "down";

    private PacketViewer packetViewer;
    private JPopupMenu popup; 
    private JMenuItem markPacketMenuItem;
    private int maxLines = 1000;

    private Set<Integer> markedPacketNrs = new HashSet<Integer>(2);
    private int continuousRowCount = 0; // Always increases, even when rows were removed

    // Store history of previously visited packet numbers
    private List<Integer> history = new ArrayList<Integer>();
    private int historyPosition = -1;

    public PacketsTable(PacketViewer packetViewer) {
        super();
        this.packetViewer = packetViewer;

        DefaultTableModel packetsModel = new DefaultTableModel(COLUMNS, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<?> getColumnClass(int columnIndex) { 
                if (columnIndex == 0 || columnIndex == 2 || columnIndex == 4)
                    return Number.class;
                return super.getColumnClass(columnIndex);
            }
        };
        setModel(packetsModel);

        setPreferredScrollableViewportSize(getPreferredSize());
        setFillsViewportHeight(true);
        getColumnModel().getColumn(0).setPreferredWidth(50);
        getColumnModel().getColumn(1).setPreferredWidth(160);
        getColumnModel().getColumn(2).setPreferredWidth(50);
        getColumnModel().getColumn(3).setPreferredWidth(160);
        getColumnModel().getColumn(4).setPreferredWidth(50);

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
        packetsSorter.setComparator(0, numberComparator);
        packetsSorter.setComparator(2, numberComparator);
        packetsSorter.setComparator(4, numberComparator);
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
        installPopupMenu();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        if (popup.isShowing() && isCellSelected(row, column)) {
            component.setBackground(LIGHT_GRAY);
        } else if (!isCellSelected(row, column)) {
            row = convertRowIndexToModel(row);
            int packetNr = (Integer) getModel().getValueAt(row, 0);
            if (markedPacketNrs.contains(packetNr))
                component.setBackground(Color.YELLOW);
            else
                component.setBackground(Color.WHITE);
        }
        return component;
    }

    public void clear() {
        clearSelection();
        ((DefaultTableModel) getModel()).setRowCount(0);
        continuousRowCount = 0;
        markedPacketNrs.clear();
        history.clear();
        historyPosition = -1;
        updateActionStates();
    }

    public void addRow(Object[] rowData) {
        Vector<Object> v = new Vector<Object>(rowData.length + 1);
        v.add(++continuousRowCount);
        for (Object o : rowData)
            v.add(o);
        ((DefaultTableModel) getModel()).addRow(v);

        if (getRowCount() == 1) updateActionStates();
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    /**
     * Goes back to the previously selected packet
     */
    public void goBack() {
        if (historyPosition > 0) {
            historyPosition--;
            goToPacket(history.get(historyPosition));
        }
    }

    /**
     * Goes forward to the packet that was selected before
     * the <tt>goBack()</tt> was used.
     */
    public void goForward() {
        if (historyPosition < history.size() - 1) {
            historyPosition++;
            goToPacket(history.get(historyPosition));
        }
    }

    /**
     * Goes to the packet that visually succeeds the currently selected packet
     */
    public void goUp() {
        int rowIndex = getSelectedRow();
        if (rowIndex != -1) {
            if (rowIndex > 0) {
                rowIndex = rowIndex - 1;
                setRowSelectionInterval(rowIndex, rowIndex);
                scrollRectToVisible(getCellRect(rowIndex, 0, true));
            }
        } else if (getRowCount() > 0) {
            setRowSelectionInterval(0, 0);
            scrollRectToVisible(getCellRect(0, 0, true));
        }
    }

    /**
     * Goes to the packet that visually succeeds the currently selected packet
     */
    public void goDown() {
        int rowIndex = getSelectedRow();
        if (rowIndex != -1) {
            if (rowIndex < getRowCount() - 1) {
                rowIndex = rowIndex + 1;
                setRowSelectionInterval(rowIndex, rowIndex);
                scrollRectToVisible(getCellRect(rowIndex, 0, true));
            }
        } else if (getRowCount() > 0) {
            setRowSelectionInterval(0, 0);
            scrollRectToVisible(getCellRect(0, 0, true));
        }
    }

    /**
     * Jumps to the specified packet number. Note that packet numbers
     * do not necessarily start at 1. When connecting to a Yamcs instance,
     * only the latest 1000 packets are displayed.
     */
    public void goToPacket(int packetNumber) {
        int firstPacketNumber = getPacketNumberRange()[0];
        packetNumber = packetNumber - firstPacketNumber;
        int rowIndex = convertRowIndexToView(packetNumber);
        setRowSelectionInterval(rowIndex, rowIndex);
        scrollRectToVisible(getCellRect(rowIndex, 0, true));
    }

    public int[] getPacketNumberRange() {
        int lo = (Integer) getModel().getValueAt(0, 0);
        int hi = lo + getRowCount() - 1;
        return new int[] { lo, hi };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    private void createActions() {
        //
        // GO TO PACKET
        Action goToPacketAction = new AbstractAction("Go to Packet...") {
            private static final long serialVersionUID = 1L;
            private GoToPacketDialog goToPacketDialog;
            @Override
            public void actionPerformed(ActionEvent e) {
                if (goToPacketDialog == null) {
                    goToPacketDialog = new GoToPacketDialog(PacketsTable.this);
                }
                int ret = goToPacketDialog.showDialog();
                if (ret == GoToPacketDialog.APPROVE_OPTION) {
                    goToPacket(goToPacketDialog.getLineNumber());
                }
            }
        };
        goToPacketAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_P);
        goToPacketAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.CTRL_MASK));
        getActionMap().put(GO_TO_PACKET_ACTION_KEY, goToPacketAction);

        //
        // BACK
        Action backAction = new AbstractAction("Back") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                goBack();
            }
        };
        backAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_B);
        backAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, ActionEvent.ALT_MASK));
        getActionMap().put(BACK_ACTION_KEY, backAction);

        //
        // FORWARD
        Action forwardAction = new AbstractAction("Forward") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                goForward();
            }
        };
        forwardAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_F);
        forwardAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, ActionEvent.ALT_MASK));
        getActionMap().put(FORWARD_ACTION_KEY, forwardAction);


        //
        // UP
        Action upAction = new AbstractAction("Previous Packet") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                goUp();
            }
        };
        upAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_P);
        upAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_UP, ActionEvent.ALT_MASK));
        getActionMap().put(UP_ACTION_KEY, upAction);

        //
        // DOWN
        Action downAction = new AbstractAction("Next Packet") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                goDown();
            }
        };
        downAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_N);
        downAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, ActionEvent.ALT_MASK));
        getActionMap().put(DOWN_ACTION_KEY, downAction);

        //
        // TOGGLE MARK
        Action toggleMarkAction = new AbstractAction(MARK_PACKET) {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                int rowIndex = getSelectedRow();
                if (rowIndex != -1) {
                    rowIndex = convertRowIndexToModel(rowIndex);
                    int packetNr = (Integer) getModel().getValueAt(rowIndex, 0);
                    if (markedPacketNrs.contains(packetNr))
                        markedPacketNrs.remove(packetNr);
                    else
                        markedPacketNrs.add(packetNr);
                }
            }
        };
        toggleMarkAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_M);
        toggleMarkAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.CTRL_MASK));
        getActionMap().put(TOGGLE_MARK_ACTION_KEY, toggleMarkAction);

        updateActionStates();
    }

    private void installPopupMenu() {
        popup = new JPopupMenu();
        markPacketMenuItem = new JMenuItem(getActionMap().get(TOGGLE_MARK_ACTION_KEY));
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

        addMouseListener(new PopupListener());
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        super.valueChanged(e);
        if (!e.getValueIsAdjusting()) {
            int rowIndex = getSelectedRow();
            if (rowIndex != -1) {
                rowIndex = convertRowIndexToModel(rowIndex);

                packetViewer.setSelectedPacket((ListPacket)getModel().getValueAt(rowIndex, 3));
                int packetNumber = (Integer) getModel().getValueAt(rowIndex, 0);

                if (history.isEmpty() || history.get(historyPosition) != packetNumber) {
                    historyPosition++;
                    history.add(historyPosition, packetNumber);

                    // Clear Forward history (if any)
                    for (int i = history.size() - 1; i > historyPosition; i--)
                        history.remove(i);

                    // Limit total history size to 10
                    if (history.size() > 10) {
                        history.remove(0);
                        historyPosition--;
                    }
                }
            }
            updateActionStates();
        }
    }

    private void updateActionStates() {
        // Reflect selection to mark/unmark actions
        int rowIndex = getSelectedRow();
        Action toggleMark = getActionMap().get(TOGGLE_MARK_ACTION_KEY);
        if (rowIndex == -1) {
            toggleMark.putValue(Action.NAME, MARK_PACKET);
            toggleMark.setEnabled(false);
        } else {
            toggleMark.setEnabled(true);
            rowIndex = convertRowIndexToModel(rowIndex);
            int packetNr = (Integer) getModel().getValueAt(rowIndex, 0);
            if (markedPacketNrs.contains(packetNr))
                toggleMark.putValue(Action.NAME, UNMARK_PACKET);
            else
                toggleMark.putValue(Action.NAME, MARK_PACKET);
        }

        // Activate "Go to Packet" only for non-empty packet table
        Action goToPacket = getActionMap().get(GO_TO_PACKET_ACTION_KEY);
        goToPacket.setEnabled(getRowCount() > 0);

        // Update enabled-state of Back-action
        Action back = getActionMap().get(BACK_ACTION_KEY);
        back.setEnabled(historyPosition > 0);

        // Update enabled-state of Forward-action
        Action forward = getActionMap().get(FORWARD_ACTION_KEY);
        forward.setEnabled(historyPosition < history.size() - 1);

        // Update enabled-state of Up-action
        Action up = getActionMap().get(UP_ACTION_KEY);
        up.setEnabled(getRowCount() > 0);

        // Update enabled-state of Down-action
        Action down = getActionMap().get(DOWN_ACTION_KEY);
        down.setEnabled(getRowCount() > 0);
    }

    /**
     * @param rowIndex row index in model, not in view
     */
    private void removeRow(int rowIndex) {
        DefaultTableModel packetsModel = (DefaultTableModel) getModel();
        int packetNr = (Integer) packetsModel.getValueAt(rowIndex, 0);
        markedPacketNrs.remove(packetNr);

        int historyIndex = history.indexOf(packetNr);
        if (historyIndex != -1) {
            history.remove(historyIndex);
            if (historyIndex < historyPosition)
                historyPosition--;
        }

        packetsModel.removeRow(0);
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
                    removeRow(0);
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
