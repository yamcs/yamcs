package org.yamcs.ui.packetviewer;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.yamcs.ui.PacketListener;
import org.yamcs.ui.packetviewer.PacketViewer.ListPacket;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;

public class PacketsTable extends JTable implements ListSelectionListener, PacketListener {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"", "Generation Time", "APID", "Opsname", "Size"};

    private static final ImageIcon ICON_UNSTARRED = new ImageIcon(PacketsTable.class.getResource("/org/yamcs/images/unstarred.png"));
    private static final ImageIcon ICON_STARRED = new ImageIcon(PacketsTable.class.getResource("/org/yamcs/images/starred.png"));

    private PacketViewer packetViewer;
    private int maxLines = 1000;

    public PacketsTable(PacketViewer packetViewer) {
        super();
        this.packetViewer = packetViewer;

        DefaultTableModel packetsModel = new DefaultTableModel(COLUMNS, 0);
        setModel(packetsModel);

        setPreferredScrollableViewportSize(getPreferredSize());
        getColumnModel().getColumn(0).setPreferredWidth(50);
        getColumnModel().getColumn(1).setPreferredWidth(330);
        getColumnModel().getColumn(2).setPreferredWidth(50);
        getColumnModel().getColumn(3).setPreferredWidth(280);

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));

        TableRowSorter<DefaultTableModel> packetsSorter = new TableRowSorter<DefaultTableModel>(packetsModel);
        packetsSorter.setComparator(1, new Comparator<Number>() {
            @Override
            public int compare(Number o1, Number o2) {
                return o1.intValue() < o2.intValue() ? -1 : (o1.intValue() > o2.intValue() ? 1 : 0);
            }
        });
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

        setDefaultRenderer(ImageIcon.class, new IconRenderer());

        addMouseListener(new TableMouseListener());
    }

    public void clear() {
        ((DefaultTableModel) getModel()).setRowCount(0);
    }

    public void addRow(Object[] rowData) {
        Vector<Object> v = new Vector<Object>(rowData.length + 1);
        for (Object o : rowData) {
            v.addElement(o);
        }

        v.add(0, ICON_UNSTARRED);
        ((DefaultTableModel) getModel()).addRow(v);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        int realColumn = convertColumnIndexToModel(column);
        if (realColumn == 0) {
            return ICON_UNSTARRED.getClass();
        } else {
            return super.getColumnClass(column);
        }
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        super.valueChanged(e);
        if (!e.getValueIsAdjusting()) {
            int rowIndex = convertRowIndexToModel(getSelectedRow());
            if (rowIndex != -1) {
                packetViewer.setSelectedPacket((ListPacket)getModel().getValueAt(rowIndex, 3));
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

    private class TableMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            int row = convertRowIndexToModel(rowAtPoint(e.getPoint()));
            int col = convertColumnIndexToModel(columnAtPoint(e.getPoint()));

            if (col == 0) {            
                if (ICON_UNSTARRED.equals(getModel().getValueAt(row, col))) {
                    getModel().setValueAt(ICON_STARRED, row, col);
                } else {
                    getModel().setValueAt(ICON_UNSTARRED, row, col);
                }
            }
        }
    }

    /**
     * Custom IconRenderer that (as opposed to the default JTable IconRenderer)
     * prevents visual effectes of cell selection (blue border around Icon)
     */
    private static class IconRenderer extends DefaultTableCellRenderer.UIResource {
        private static final long serialVersionUID = 1L;

        public IconRenderer() {
            super();
            setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public void setValue(Object value) {
            setIcon((value instanceof Icon) ? (Icon)value : null);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row,
                int column) {
            return super.getTableCellRendererComponent(table, value,
                    isSelected, false /* ! */, row, column);
        }
    }
}
