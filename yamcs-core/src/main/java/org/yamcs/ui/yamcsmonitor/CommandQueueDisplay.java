package org.yamcs.ui.yamcsmonitor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;

import org.yamcs.ui.CommandQueueControlClient;
import org.yamcs.ui.CommandQueueListener;
import org.yamcs.ui.YamcsConnector;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.QueueState;
import org.yamcs.utils.TimeEncoding;


/**
 * Display for the telecommand queues implemented in yamcs
 * @author nm
 *
 */
public class CommandQueueDisplay extends JSplitPane implements ActionListener, CommandQueueListener {
    DefaultTableModel commandTableModel;
    HashMap<String,QueuesTableModel> queuesModels = new HashMap<String,QueuesTableModel>();
    TableRowSorter<QueuesTableModel> queueSorter;
    JFrame frame;
    JTable queueTable, commandTable;
    JScrollPane queueScroll;
    QueuesTableModel currentQueuesModel, emptyQueuesModel;
    boolean isAdmin;
    static long oldCommandWarningTime=60;

    CommandQueueControlClient commandQueueControl;

    final String[] queueStateItems = {"BLOCKED", "DISABLED", "ENABLED"};
    private volatile String selectedInstance;

    /**
     * 
     * 
     */
    public CommandQueueDisplay(YamcsConnector yconnector, boolean isAdmin)	{
        super(VERTICAL_SPLIT);
        this.isAdmin = isAdmin;
        commandQueueControl=new CommandQueueControlClient(yconnector);
        //build the table showing the queues

        emptyQueuesModel = new QueuesTableModel(null, null);
        queueTable = new JTable(emptyQueuesModel);
        queueTable.getTableHeader().setReorderingAllowed(false);
        queueTable.setAutoCreateColumnsFromModel(false);
        JComboBox combo = new JComboBox(queueStateItems);
        combo.setEditable(false);
        queueTable.setRowHeight(combo.getPreferredSize().height);
        queueTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(combo));
        queueSorter = new TableRowSorter<QueuesTableModel>(emptyQueuesModel);
        queueSorter.setComparator(2, new Comparator<Number>() {
            public int compare(Number o1, Number o2) {
                return o1.intValue() < o2.intValue() ? -1 : (o1.intValue() > o2.intValue() ? 1 : 0);
            }
        });
        queueTable.setRowSorter(queueSorter);
        queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queueTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                //Ignore extra messages.
                if (e.getValueIsAdjusting()) return;
                int row = queueTable.getSelectedRow();
                if (row != -1) {
                    row = queueTable.convertRowIndexToModel(row);
                    if (currentQueuesModel != null) {
                        currentQueuesModel.setQueue(row);
                    }
                }
            }
        });

        queueScroll = new JScrollPane(queueTable);
        queueTable.setPreferredScrollableViewportSize(new Dimension(400, 150));
        setTopComponent(queueScroll);

        //build the table showing the commands from the selected queues

        final String[] columnToolTips = {
                "The queue which contains the command",
                "The user who submitted the command",
                "Command source code",
                "Unique id of the command"
        };

        final CommandRenderer cRenderer = new CommandRenderer();
        final String[] commandColumns={"Queue","User","Command String","Time"};
        commandTableModel = new DefaultTableModel(commandColumns,0);
        commandTable = new JTable(commandTableModel) {
            protected JTableHeader createDefaultTableHeader() {
                return new JTableHeader(columnModel) {
                    public String getToolTipText(MouseEvent e) {
                        int index = columnModel.getColumnIndexAtX(e.getPoint().x);
                        if (index == -1) return "";
                        int realIndex = columnModel.getColumn(index).getModelIndex();
                        return columnToolTips[realIndex];
                    }
                };
            }
            public boolean isCellEditable(int row, int column) { return false; }
            public TableCellRenderer getCellRenderer(int row, int column) {
                return column == convertColumnIndexToModel(2) ? cRenderer : super.getCellRenderer(row, column);
            }
        };
        commandTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        commandTable.getTableHeader().setResizingAllowed(true);
        JScrollPane scroll = new JScrollPane(commandTable);
        commandTable.setPreferredScrollableViewportSize(new Dimension(400, 100));
        setBottomComponent(scroll);

        //width of columns
        TableColumnModel model = commandTable.getTableHeader().getColumnModel();
        model.getColumn(0).setPreferredWidth(60);
        model.getColumn(1).setPreferredWidth(80);
        model.getColumn(2).setPreferredWidth(500);
        int newWidth = new JLabel("0000-00-00T00:00:00.000").getPreferredSize().width + 20;
        model.getColumn(3).setPreferredWidth(newWidth);

        //setDividerLocation(0.5);
        setResizeWeight(0.1);


        //add right click menus
        final JPopupMenu cmdPopup = new JPopupMenu();
        JMenuItem miSend = new JMenuItem("Send");
        miSend.addActionListener(this);
        miSend.setActionCommand("send");
        cmdPopup.add(miSend);

        JMenuItem miReject = new JMenuItem("Reject");
        miReject.addActionListener(this);
        miReject.setActionCommand("reject");
        cmdPopup.add(miReject);

        commandTable.addMouseListener(new MouseAdapter() {
            public void mousePressed( MouseEvent e ) { maybeShowPopup(e); }
            public void mouseReleased( MouseEvent e ) { maybeShowPopup(e); }
            private void maybeShowPopup( MouseEvent e ) {
                if ( e.isPopupTrigger() ) {
                    int row = commandTable.rowAtPoint(e.getPoint());
                    if ((row != -1) && !commandTable.isRowSelected(row)) commandTable.setRowSelectionInterval(row, row);
                    cmdPopup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        commandQueueControl.addCommandQueueListener(this);
    }

    public void addProcessor(String instance, String processorName) {
        QueuesTableModel model = new QueuesTableModel(instance, processorName);
        queuesModels.put(instance+"."+processorName, model);
    }

    public void removeProcessor(String instance, String channelName) {
        queuesModels.remove(instance+"."+channelName);
    }

    public void setProcessor(String instance, String processorName) {
        currentQueuesModel = processorName == null ? emptyQueuesModel : queuesModels.get(instance+"."+processorName);
        if (currentQueuesModel == null) currentQueuesModel = emptyQueuesModel;

        queueTable.setModel(currentQueuesModel);
        queueSorter.setModel(currentQueuesModel);
    }


    @Override
    public void actionPerformed(ActionEvent ae) {
        final String cmd = ae.getActionCommand();
        try {
            if (currentQueuesModel != null) {
                if (cmd.equals("send")) {
                    int rows[]=commandTable.getSelectedRows();
                    for(int row:rows) {
                        //	PreparedCommand=currentQueuesModel.queues
                        String queueName=(String)commandTableModel.getValueAt(commandTable.convertRowIndexToModel(row), 0);

                        int index=commandTable.convertRowIndexToModel(row);

                        CommandQueueEntry cqe=currentQueuesModel.getCommand(queueName, index);
                        if(cqe==null) continue;
                        long timeinthequeue=TimeEncoding.currentInstant()-cqe.getGenerationTime();
                        if(timeinthequeue>oldCommandWarningTime*1000L) {
                            int res=CommandFateDialog.showDialog(frame, cqe.getCmdId());
                            switch(res) {
                            case -1: //cancel 
                                return;
                            case 0: //rebuild the command
                                YamcsMonitor.theApp.log("sending command with updated time: "+cqe.getSource());
                                commandQueueControl.releaseCommand(cqe, true);
                                break;
                            case 1: //send the command with the old generation time
                                YamcsMonitor.theApp.log("sending command: "+cqe);
                                commandQueueControl.releaseCommand(cqe, false);
                                break;
                            case 2: //rejecting command
                                System.out.println("rejecting command: "+cqe.getSource());
                                commandQueueControl.rejectCommand(cqe);
                            }
                        } else {
                            YamcsMonitor.theApp.log("sending command: "+cqe.getSource());
                            commandQueueControl.releaseCommand(cqe, false);
                        }
                    }
                } else if (cmd.equals("reject")) {
                    int rows[]=commandTable.getSelectedRows();
                    for (int row:rows) {
                        String queueName=(String)commandTableModel.getValueAt(commandTable.convertRowIndexToModel(row), 0);
                        int index=commandTable.convertRowIndexToModel(row);
                        CommandQueueEntry cqe=currentQueuesModel.getCommand(queueName, index);
                        if(cqe==null) continue;
                        YamcsMonitor.theApp.log("rejecting command: "+cqe.getSource());
                        commandQueueControl.rejectCommand(cqe);
                    }
                }
            }
        } catch (Exception e) {
            YamcsMonitor.theApp.showMessage(e.getMessage());
        }
    }



    @Override
    public void log(String msg) {
        YamcsMonitor.theApp.log(msg);
    }


    @Override
    public void updateQueue(final CommandQueueInfo cqi) {
        if(!selectedInstance.equals(cqi.getInstance())) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                QueuesTableModel model = queuesModels.get(cqi.getInstance()+"."+cqi.getProcessorName());
                model.updateQueue(cqi);
                if(cqi.getEntryCount()>0) {
                    for(CommandQueueEntry cqe: cqi.getEntryList()) {
                        commandAdded(cqe);
                    }
                }
            }
        });
    }

    @Override
    public void commandAdded(final CommandQueueEntry cqe) {
        if(!selectedInstance.equals(cqe.getInstance())) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                QueuesTableModel model = queuesModels.get(cqe.getInstance()+"."+cqe.getProcessorName());
                model.commandAdded(cqe);
            }
        });
    }

    @Override
    public void commandRejected(final CommandQueueEntry cqe) {
        if(!selectedInstance.equals(cqe.getInstance())) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                QueuesTableModel model=queuesModels.get(cqe.getInstance()+"."+cqe.getProcessorName());
                model.removeCommandFromQueue(cqe);
            }
        });
    }

    @Override
    public void commandSent(final CommandQueueEntry cqe) {
        if(!selectedInstance.equals(cqe.getInstance())) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                QueuesTableModel model=queuesModels.get(cqe.getInstance()+"."+cqe.getProcessorName());
                model.removeCommandFromQueue(cqe);
            }
        });
    }

    public void setSelectedInstance(String newInstance) {
        queuesModels.clear();
        this.selectedInstance = newInstance;
    }



    /*contains all the queues and corresponding commands for one channel*/
    class QueuesTableModel extends AbstractTableModel {
        List<CommandQueueInfo> queues=new ArrayList<CommandQueueInfo>(3);
        Map<String, ArrayList<CommandQueueEntry>> commands=new HashMap<String, ArrayList<CommandQueueEntry>>();
        String instance, channel;

        public QueuesTableModel(String instance, String channel) {
            this.instance=instance;
            this.channel=channel;
        }

        void updateQueue(CommandQueueInfo cqi) {
            boolean found=false;
            for(int i=0;i<queues.size();i++) {
                CommandQueueInfo q=queues.get(i);
                if(q.getName().equals(cqi.getName())) {
                    queues.set(i, cqi);
                    found=true;
                    fireTableRowsUpdated(i, i);
                    break;
                }
            }
            if(!found) {
                queues.add(cqi);
                fireTableRowsInserted(queues.size(), queues.size());
            }
        }

        void commandAdded(final CommandQueueEntry cqe) {
            ArrayList<CommandQueueEntry> cmds = commands.get(cqe.getQueueName());
            if(cmds==null) {
                cmds = new ArrayList<CommandQueueEntry>();
                commands.put(cqe.getQueueName(), cmds);
            }
            cmds.add(cqe);
            reloadCommandsTable();
        }

        void removeCommandFromQueue(CommandQueueEntry cqe) {
            ArrayList<CommandQueueEntry> cmds=commands.get(cqe.getQueueName());
            if(cmds==null) {
                return;
            }
            for(int i=0;i<cmds.size();i++) {
                if(cmds.get(i).getCmdId().equals(cqe.getCmdId())){
                    cmds.remove(i);
                    reloadCommandsTable();
                    break;
                }
            }
        }
        /**
         * Called when a row is selected in the queue table. 
         *  Shows all the commands in the selected queues
         */
        void setQueue(int index) {
            commandTableModel.setRowCount(0);
            CommandQueueInfo q = queues.get(index);
            ArrayList<CommandQueueEntry> cmds=commands.get(q.getName());
            if(cmds==null) {
                return;
            }
            for(CommandQueueEntry cqe:cmds) {
                Object[] r={q.getName(), cqe.getUsername(), cqe.getSource(), TimeEncoding.toString(cqe.getGenerationTime())};
                commandTableModel.addRow(r);
            }
        }

        void reloadCommandsTable() {
            if (this == currentQueuesModel) {
                int row = queueTable.convertRowIndexToModel(queueTable.getSelectedRow());
                if (row >= 0) {
                    setQueue(row);
                }
            }
        }


        CommandQueueEntry getCommand(String queueName, int index) {
            ArrayList<CommandQueueEntry> cmds=commands.get(queueName);
            if(cmds==null)return null;
            return cmds.get(index);
        }

        //private static final long serialVersionUID = 4531138066222987136L;
        final String[] queueColumns = {"Queue", "State", "Commands"};
        public String getColumnName(int col) {
            return queueColumns[col];
        }
        public int getRowCount() { return queues.size(); }
        public int getColumnCount() { return queueColumns.length; }
        public Object getValueAt(int row, int col) {
            CommandQueueInfo q=queues.get(row);
            Object o=null;
            switch (col) {
            case 0:
                o=q.getName();
                break;
            case 1:
                o=q.getState().toString();
                break;
            case 2:
                ArrayList<CommandQueueEntry> cmds=commands.get(q.getName());
                if(cmds==null){
                    o=0;
                } else {
                    o=cmds.size();
                }
                break;
            }
            return o;
        }
        public boolean isCellEditable(int row, int col) { return col == 1; }
        public void setValueAt(Object value, int row, int col) {
            super.setValueAt(value, row, col);
            try {
                CommandQueueInfo q=queues.get(row);
                if (value.equals(queueStateItems[0])) {
                    commandQueueControl.setQueueState(CommandQueueInfo.newBuilder(q).setState(QueueState.BLOCKED).build(), false);
                } else if (value.equals(queueStateItems[1])) {
                    commandQueueControl.setQueueState(CommandQueueInfo.newBuilder(q).setState(QueueState.DISABLED).build(), false);
                } else if (value.equals(queueStateItems[2])) {
                    boolean oldcommandsfound=false;
                    ArrayList<CommandQueueEntry> cmds=commands.get(q.getName());
                    if(cmds!=null) {
                        for(CommandQueueEntry cqe:cmds) {
                            if(TimeEncoding.currentInstant()-cqe.getGenerationTime()>oldCommandWarningTime*1000L) {
                                oldcommandsfound=true;
                                break;
                            }
                        }
                    }

                    if(oldcommandsfound) {
                        int result=CommandFateDialog.showDialog2(frame);
                        switch(result) {
                        case -1://cancel
                            return;
                        case 0: //send with updated times
                            commandQueueControl.setQueueState(CommandQueueInfo.newBuilder(q).setState(QueueState.ENABLED).build(),true);
                            break;
                        case 1://send with old times
                            commandQueueControl.setQueueState(CommandQueueInfo.newBuilder(q).setState(QueueState.ENABLED).build(),false);
                            break;
                        }
                    } else {
                        commandQueueControl.setQueueState(CommandQueueInfo.newBuilder(q).setState(QueueState.ENABLED).build(),false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                YamcsMonitor.theApp.showMessage(e.toString());
            }
        }
    }

    class CommandRenderer extends JTextArea implements TableCellRenderer {
        public void validate() {};
        public void invalidate(){};
        public void revalidate() {};
        public void repaint(){};
        public void firePropertyChange() {};
        public boolean isOpaque() { return true; }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value.toString());
            int height_wanted = (int)getPreferredSize().getHeight();
            table.setRowHeight(row, height_wanted);
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    static class CommandFateDialog extends JDialog implements ActionListener {
        int result;
        static CommandFateDialog cfd1=null;
        static CommandFateDialog cfd2=null;

        JRadioButton[] radioButtons;
        JLabel messageLabel;


        public static int showDialog(Frame aFrame, CommandId cmdId) {
            if(cfd1==null) cfd1=new CommandFateDialog(aFrame,1);
            cfd1.messageLabel.setText("The command '"+cmdId.getSequenceNumber()+"' is older than "+oldCommandWarningTime+" seconds");
            cfd1.setLocationRelativeTo(aFrame);
            cfd1.setVisible(true);
            return cfd1.result;
        }

        public static int showDialog2(Frame aFrame) {
            if(cfd2==null) cfd2=new CommandFateDialog(aFrame,2);
            cfd2.setLocationRelativeTo(aFrame);
            cfd2.setVisible(true);
            return cfd2.result;
        }

        public CommandFateDialog(Frame aFrame, int type) {
            super(aFrame, true);
            if(type==1)	
                setTitle("Command older than "+oldCommandWarningTime+" seconds");
            else
                setTitle("Command(s) older than "+oldCommandWarningTime+" seconds");
            JPanel box = new JPanel();
            if(type==1)
                messageLabel = new JLabel("The command 2009/208 17:07:17.845@255.255.255.255/123456 is older than "+oldCommandWarningTime+" seconds");
            else 
                messageLabel = new JLabel("Enabling the queue would cause some commands older than "+oldCommandWarningTime+" seconds to be sent.");
            box.setLayout(new BoxLayout(box, BoxLayout.PAGE_AXIS));
            box.add(messageLabel);
            ButtonGroup group = new ButtonGroup();

            if(type==1) {
                radioButtons = new JRadioButton[3];
                radioButtons[0] = new JRadioButton("Send the command with updated generation time");
                radioButtons[1] = new JRadioButton("Send the command with the current (old) generation time");
                radioButtons[2] = new JRadioButton("Reject the command");
            } else {
                radioButtons = new JRadioButton[2];
                radioButtons[0] = new JRadioButton("Send all the commands with updated generation time");
                radioButtons[1] = new JRadioButton("Send all the command with the current (old) generation time");
            }

            for (int i = 0; i < radioButtons.length; i++) {
                box.add(radioButtons[i]);
                group.add(radioButtons[i]);
            }
            group.setSelected(radioButtons[0].getModel(), true);
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(this);
            final JButton okButton = new JButton("OK");
            okButton.setActionCommand("OK");
            okButton.addActionListener(this);
            getRootPane().setDefaultButton(okButton);

            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
            buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            buttonPane.add(Box.createHorizontalGlue());
            buttonPane.add(okButton);   
            buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
            buttonPane.add(cancelButton);
            buttonPane.add(Box.createHorizontalGlue());
            Container contentPane = getContentPane();
            contentPane.add(box, BorderLayout.LINE_START);
            contentPane.add(buttonPane, BorderLayout.PAGE_END);
            pack();
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent we) {
                    result=-1;
                    setVisible(false);
                }
            });

        }

        @Override
        public void actionPerformed(ActionEvent ev) {
            if(ev.getActionCommand().equalsIgnoreCase("OK")) {
                result=-1;
                for(int i=0;i<radioButtons.length;i++) {
                    if(radioButtons[i].isSelected()) {
                        result=i;
                        break;
                    }
                }
                setVisible(false);				
            } else if(ev.getActionCommand().equalsIgnoreCase("Cancel")) {
                result=-1;
                setVisible(false);
            }
        }
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new JFrame("Name That Baby");
                frame.pack();
                //	int res=CommandFateDialog.showDialog(frame," abcrada da ds");
                int res=CommandFateDialog.showDialog2(frame);
                frame.dispose();
            }
        });
    }


}
