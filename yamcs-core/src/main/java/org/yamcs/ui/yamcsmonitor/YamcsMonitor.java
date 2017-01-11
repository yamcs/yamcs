package org.yamcs.ui.yamcsmonitor;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsVersion;
import org.yamcs.api.*;
import org.yamcs.api.YamcsConnectDialog.YamcsConnectDialogResult;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.YamcsManagement.*;
import org.yamcs.ui.*;
import org.yamcs.ui.archivebrowser.ArchiveIndexReceiver;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;


public class YamcsMonitor implements WebSocketClientCallback, ProcessorListener, ConnectionListener, ActionListener, ItemListener, LinkListener {
    YProcTableModel processorTableModel=new YProcTableModel();
    ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1);
    LinkTableModel linkTableModel=new LinkTableModel(timer);
    ClientTableModel clientTableModel;
    DefaultTableModel statsTableModel;
    JFrame frame;
    CommandQueueControlClient commandQueueControl;

    ArchiveBrowserSelector archiveBrowserSelector;

    private JTextArea logTextArea;
    private JMenuItem miConnect, dcmi;//, queueControl;
    private JLabel tmQuickStatus, tcQuickStatus;
    TitledBorder processorStatusBorder;
    JTabbedPane processorStatusPanel;
    private JMenu clientsPopupMenu;
    private JTable linkTable, processorTable, clientsTable;
    private JComboBox<ProcessorWidget> processorChooser;
    private JTextField newYProcName;
    CommandQueueDisplay commandQueueDisplay;
    JScrollPane linkTableScroll, processorTableScroll;
    private Set<String> allProcessors = new HashSet<String>();//stores instance.processorName for all processors to populate the connectToProcessor popup menu

    static boolean hasAdminRights=true;
    static String initialUrl=null;

    private JButton createProcessorButton;
    private JCheckBox persistentCheckBox;

    public YamcsConnectionProperties connectionParams = null;


    private LinkControlClient linkControl;
    private ArchiveIndexReceiver indexReceiver;
    private ProcessorControlClient processorControl;

    private YamcsConnector yconnector;
    
    static YamcsMonitor theApp;

    HashMap<String, Statistics> processorStats=new HashMap<String, Statistics>();

    static final SimpleDateFormat format_yyyyddd = new SimpleDateFormat("yyyy/DDD HH:mm:ss");

    private JMenuItem instanceMenuItem;
    private String selectedInstance;
    boolean authenticationEnabled = false;

    public YamcsMonitor() throws ConfigurationException, IOException{
        theApp = this;
        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        if(config.containsKey("authenticationEnabled")) {
            authenticationEnabled = config.getBoolean("authenticationEnabled");
        }

        yconnector = new YamcsConnector("YamcsMonitor");
        yconnector.addConnectionListener(this);
        
        processorControl = new ProcessorControlClient(yconnector);
        processorControl.setYProcessorListener(this);
        linkControl = new LinkControlClient(yconnector, this);

        indexReceiver = new YamcsArchiveIndexReceiver(yconnector);
        archiveBrowserSelector = new ArchiveBrowserSelector(frame, yconnector, indexReceiver, processorControl, hasAdminRights);
        indexReceiver.setIndexListener(archiveBrowserSelector);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                yconnector.disconnect();
            }
        });
    }


    private void createAndShowGUI() {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(getIcon("yamcs-monitor-32.png").getImage());

        JMenuBar menuBar = new JMenuBar();

        // Ctrl on win/linux, Command on mac
        int menuKey=Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menu);

        miConnect = new JMenuItem("Connect to Yamcs...");
        miConnect.setMnemonic(KeyEvent.VK_C);
        miConnect.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuKey));
        miConnect.addActionListener(this);
        miConnect.setActionCommand("connect");
        menu.add(miConnect);

        menu.addSeparator();

        JMenuItem menuItem = new JMenuItem("Quit",KeyEvent.VK_Q);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuKey));
        menuItem.getAccessibleContext().setAccessibleDescription("Quit Yamcs Monitor");
        menuItem.addActionListener(this);
        menuItem.setActionCommand("exit");
        menu.add(menuItem);
        frame.setJMenuBar(menuBar);

        menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);
        menuBar.add(menu);

        menuItem = new JMenuItem("About Yamcs Monitor");
        menuItem.addActionListener(this);
        menuItem.setActionCommand("about");
        menu.add(menuItem);

        instanceMenuItem=new JMenu("Instance");
        menuBar.add(instanceMenuItem);

        // build GUI
        Box dsp=Box.createVerticalBox();
        dsp.add(buildLinkTable());
        dsp.add(buildProcessorTable());
        dsp.add(buildProcessorStatusPanel());

        Box csp=Box.createVerticalBox();
        csp.add(buildClientTable());
        csp.add(buildCreateProcessorPanel());

        logTextArea=new JTextArea(5,20);
        logTextArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logTextArea);
        scroll.setBorder(BorderFactory.createEtchedBorder());

        JSplitPane phoriz = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dsp, csp);
        phoriz.setResizeWeight(0.5);
        phoriz.setContinuousLayout(true);
        JSplitPane pvert = new JSplitPane(JSplitPane.VERTICAL_SPLIT, phoriz, scroll);
        pvert.setResizeWeight(1.0);
        pvert.setContinuousLayout(true);
        frame.getContentPane().add(pvert, BorderLayout.CENTER);

        //Display the window.
        setTitle("not connected");
        frame.pack();
        frame.setVisible(true);
    }

    private Component buildLinkTable() {
        //build the table showing the links
        linkTable=new LinkTable(linkTableModel);

        if(hasAdminRights) {
            final JPopupMenu popupLinks = new JPopupMenu();
            JMenuItem menuitem;

            menuitem = new JMenuItem("Enable Link");
            popupLinks.add(menuitem);
            menuitem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int selectedRow = linkTable.convertRowIndexToModel(linkTable.getSelectedRow());
                    if (selectedRow < 0) {
                        showMessage("Please select a link first");
                        return;
                    }
                    final LinkInfo li = linkTableModel.getLinkInfo(selectedRow);
                    try {
                        linkControl.enable(li);
                    } catch (Exception x) {
                        showMessage(x.toString());
                    }
                }
            });

            popupLinks.addSeparator();

            menuitem = new JMenuItem("Disable Link");
            popupLinks.add(menuitem);
            menuitem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int selectedRow = linkTable.convertRowIndexToModel(linkTable.getSelectedRow());
                    if (selectedRow < 0) {
                        showMessage("Please select a link first");
                        return;
                    }
                    final LinkInfo li = linkTableModel.getLinkInfo(selectedRow);
                    try {
                        linkControl.disable(li);
                    } catch (Exception x) {
                        showMessage(x.toString());
                    }
                }
            });

            linkTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
                @Override
                public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
                private void maybeShowPopup(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        int clickedRow = linkTable.rowAtPoint(e.getPoint());
                        if (clickedRow != -1) {
                            if(!linkTable.isRowSelected(clickedRow)) linkTable.setRowSelectionInterval(clickedRow, clickedRow);
                        }
                        popupLinks.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });
        }
        linkTableScroll = new JScrollPane(linkTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        linkTable.setPreferredScrollableViewportSize(new Dimension(500, 100));
        linkTableScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Data Links"));
        return linkTableScroll;
    }

    private Component buildProcessorTable() {
        //build the table showing the processors
        processorTable=new JTable(processorTableModel) {
            /*  public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row == -1) return "";
                row = convertRowIndexToModel(row);
                return processorTableModel.getProcessorInfo(row).tooltip;
            }*/
            //public TableCellRenderer getCellRenderer(int row, int column) {
            //  return processorRenderer;
            //  }
        };
        processorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processorTable.setAutoCreateRowSorter(true);

        if(hasAdminRights) {
            final JPopupMenu popupProcessors = new JPopupMenu();

            dcmi = new JMenuItem("Destroy Processor");
            dcmi.setEnabled(false);
            popupProcessors.add(dcmi);
            dcmi.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    destroyProcessor(processorTable.convertRowIndexToModel(processorTable.getSelectedRow()));
                }
            });

            processorTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
                @Override
                public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
                private void maybeShowPopup(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        int clickedRow = processorTable.rowAtPoint(e.getPoint());
                        if ((clickedRow != -1) && !processorTable.isRowSelected(clickedRow)) {
                            if (clickedRow != -1) processorTable.setRowSelectionInterval(clickedRow, clickedRow);
                        }
                        popupProcessors.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });
        }

        processorTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                //Ignore extra messages.
                if (e.getValueIsAdjusting()) return;

                int selectedRow = processorTable.getSelectedRow();
                if (selectedRow == -1) {
                    commandQueueDisplay.setProcessor(null, null);
                    archiveBrowserSelector.archivePanel.replayPanel.clearReplayPanel();
                    if (dcmi != null) dcmi.setEnabled(false);
                } else {
                    selectedRow = processorTable.convertRowIndexToModel(selectedRow);
                    final ProcessorInfo ci = processorTableModel.getYProcessorInfo(selectedRow);
                    commandQueueDisplay.setProcessor(ci.getInstance(), ci.getName());
                    if (dcmi != null) dcmi.setEnabled(!"lounge".equalsIgnoreCase(ci.getType()));

                    // show replay transport control if applicable
                    if (ci.hasReplayRequest()) {
                        archiveBrowserSelector.archivePanel.replayPanel.setupReplayPanel(ci);
                    } else {
                        archiveBrowserSelector.archivePanel.replayPanel.clearReplayPanel();
                    }
                }
            }
        });

        processorTableScroll = new JScrollPane(processorTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        processorTable.setPreferredScrollableViewportSize(new Dimension(500, 100));
        processorTableScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Processors"));
        return processorTableScroll;
    }


    private Component buildProcessorStatusPanel() {
        //build the processor information panel composed of a few statuses (in one grid panel)
        // and a table showing the tm statistics
        processorStatusPanel = new JTabbedPane();
        processorStatusBorder = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Processor Information");
        processorStatusPanel.setBorder(processorStatusBorder);
        //processorStatusPanel.setPreferredSize(new Dimension(400, 300));



        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 1, 1, 1);

        JPanel tmPanel = new JPanel(gridbag);
        processorStatusPanel.addTab("Telemetry", tmPanel);

        JLabel label = new JLabel("TM Link Status:");
        c.weightx = 0.0; c.weighty = 0.0; c.anchor = GridBagConstraints.NORTHWEST;
        c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        gridbag.setConstraints(label, c);
        tmPanel.add(label);

        tmQuickStatus = new JLabel();
        c.weightx = 1.0; c.weighty = 0.0; c.anchor = GridBagConstraints.NORTHWEST;
        c.gridwidth = GridBagConstraints.REMAINDER; c.fill = GridBagConstraints.HORIZONTAL;
        gridbag.setConstraints(tmQuickStatus, c);
        tmPanel.add(tmQuickStatus);

        final String[] packetColumnToolTips = {
                "The Opsname of the packet.\n Unknown means that packet is not defined in the database.\nNo opsname means that the packet does not have an opsname set",
                "Number of times this packet has been received",
                "Local time of last received packet",
                "Generation time of last received packet",
        "The number of parameters contained in this packet subscribed when the packet has been last received"};

        final String[] cols = { "Packet Name", "Count", "Last Recv'd", "CCSDS Time", "Parameters" };
        statsTableModel = new DefaultTableModel(cols,0);
        JTable table = new JTable(statsTableModel) {
            @Override
            protected JTableHeader createDefaultTableHeader() {
                return new JTableHeader(columnModel) {
                    @Override
                    public String getToolTipText(MouseEvent e) {
                        int index = columnModel.getColumnIndexAtX(e.getPoint().x);
                        if (index == -1) return "";
                        int realIndex = columnModel.getColumn(index).getModelIndex();
                        return packetColumnToolTips[realIndex];
                    }
                };
            }
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table.setPreferredScrollableViewportSize(new Dimension(500, 150));
        JScrollPane scroll = new JScrollPane(table);
        c.weightx = 1.0; c.weighty = 1.0;
        c.gridwidth = GridBagConstraints.REMAINDER; c.fill = GridBagConstraints.BOTH;
        gridbag.setConstraints(scroll, c);
        tmPanel.add(scroll);

        // TC

        JPanel tcPanel = new JPanel(gridbag);
        processorStatusPanel.addTab("Telecommands", tcPanel);

        label = new JLabel("TC Link Status:");
        c.weightx = 0.0; c.weighty = 0.0;
        c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        gridbag.setConstraints(label, c);
        tcPanel.add(label);

        tcQuickStatus = new JLabel();
        c.weightx = 1.0; c.weighty = 0.0; c.anchor = GridBagConstraints.NORTHWEST;
        c.gridwidth = GridBagConstraints.REMAINDER; c.fill = GridBagConstraints.HORIZONTAL;
        gridbag.setConstraints(tcQuickStatus, c);
        tcPanel.add(tcQuickStatus);

        commandQueueDisplay = new CommandQueueDisplay(yconnector, hasAdminRights);
        c.weightx = 1.0; c.weighty = 1.0;
        c.gridwidth = GridBagConstraints.REMAINDER; c.fill = GridBagConstraints.BOTH;
        gridbag.setConstraints(commandQueueDisplay, c);
        tcPanel.add(commandQueueDisplay);

        return processorStatusPanel;
    }


    private Component buildClientTable() {

        // build the table showing the clients

        clientTableModel=new ClientTableModel();
        clientsTable = new JTable(clientTableModel);
        TableRowSorter<ClientTableModel> clientsSorter = new TableRowSorter<ClientTableModel>(clientTableModel);
        clientsSorter.setComparator(0, new Comparator<Number>() {
            @Override
            public int compare(Number o1, Number o2) {
                return o1.intValue() < o2.intValue() ? -1 : (o1.intValue() > o2.intValue() ? 1 : 0);
            }
        });
        clientsTable.setRowSorter(clientsSorter);
        clientsTable.getColumnModel().getColumn(0).setMaxWidth(40);
        clientsTable.getColumnModel().getColumn(1).setMaxWidth(120);
        JScrollPane scroll = new JScrollPane(clientsTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        clientsTable.setPreferredScrollableViewportSize(new Dimension(400, 200));
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Connected Clients"));

        final JPopupMenu popup = new JPopupMenu();
        clientsPopupMenu = new JMenu("Connect to Processor");
        popup.add(clientsPopupMenu);

        clientsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int clickedRow = clientsTable.rowAtPoint(e.getPoint());
                    // if the clicked row is not selected, deselect everything and select just that row
                    if ((clickedRow != -1) && !clientsTable.isRowSelected(clickedRow)) {
                        clientsTable.setRowSelectionInterval(clickedRow, clickedRow);
                    }
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        return scroll;
    }

    private Component buildCreateProcessorPanel() {
        // "create processor" panel
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        JPanel createPanel = new JPanel(gridbag);
        createPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "New Processor"));
        JLabel label = new JLabel("Name:");
        label.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        c.weightx = 0.0; c.gridwidth = 1; gridbag.setConstraints(label, c); 
        createPanel.add(label);
        newYProcName = new JTextField();
        c.gridwidth = GridBagConstraints.REMAINDER; c.fill=GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        gridbag.setConstraints(newYProcName, c);
        createPanel.add(newYProcName);

        label = new JLabel("Type:");
        label.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        c.weightx = 0.0; c.gridwidth = 1; gridbag.setConstraints(label, c); 
        createPanel.add(label);

        ArrayList<ProcessorWidget> widgets = new ArrayList<ProcessorWidget>();
        try {
            YConfiguration yconf = YConfiguration.getConfiguration("yamcs-ui");
            if(yconf.containsKey("processorWidgets")) {
                @SuppressWarnings("rawtypes")
                List ywidgets = yconf.getList("processorWidgets");
                for(Object ywidget : ywidgets) {
                    @SuppressWarnings("rawtypes")
                    Map m = (Map) ywidget;
                    String processorType = YConfiguration.getString(m, "type");
                    String widgetClass = YConfiguration.getString(m, "class");
                    ProcessorWidget widget = new YObjectLoader<ProcessorWidget>().loadObject(widgetClass, processorType);
                    widgets.add(widget);
                }
            } else {
                widgets.add(new ArchiveProcWidget("Archive"));
            }
        } catch(ConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        processorChooser = new JComboBox<ProcessorWidget>(widgets.toArray(new ProcessorWidget[widgets.size()]));

        c.gridwidth = GridBagConstraints.REMAINDER; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0; gridbag.setConstraints(processorChooser, c);
        createPanel.add(processorChooser);

        label = new JLabel("Spec:");
        label.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        c.weightx = 0.0; c.gridwidth = 1; c.anchor = GridBagConstraints.NORTH;
        gridbag.setConstraints(label, c);
        createPanel.add(label);
        final CardLayout specLayout = new CardLayout();
        final JPanel specPanel = new JPanel(specLayout);
        specPanel.setBorder(BorderFactory.createEtchedBorder());
        c.weightx = 1.0; c.weighty = 1.0; c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.BOTH; gridbag.setConstraints(specPanel, c);
        createPanel.add(specPanel);
        for (ProcessorWidget widget:widgets) {
            widget.setSuggestedNameComponent(newYProcName);
            specPanel.add(widget.createConfigurationPanel(), widget.processorType);
        }
        // when a processor type is selected, bring the appropriate widget to the front
        processorChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent ae ) {
                specLayout.show(specPanel, processorChooser.getSelectedItem().toString());
                ProcessorWidget widget = (ProcessorWidget)processorChooser.getSelectedItem();
                widget.activate();
            }
        });


        if(hasAdminRights) {        

            label = new JLabel("Persistent:");
            label.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            c.weightx = 0.0; c.gridwidth = 1; c.anchor = GridBagConstraints.NORTH;
            gridbag.setConstraints(label, c);
            createPanel.add(label);

            persistentCheckBox=new JCheckBox();
            c.weightx = 1.0; c.gridwidth = GridBagConstraints.REMAINDER;
            gridbag.setConstraints(persistentCheckBox,c);
            createPanel.add(persistentCheckBox);

        }

        createProcessorButton=new JButton("Create");
        createProcessorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createProcessor(clientsTable.getSelectedRows());
            }
        });
        createProcessorButton.setEnabled(false);
        c.gridwidth = GridBagConstraints.REMAINDER; c.fill=GridBagConstraints.NONE; c.weightx = 1.0;
        c.anchor = GridBagConstraints.NORTH; c.weighty = 0.0;
        gridbag.setConstraints(createProcessorButton, c);
        createPanel.add(createProcessorButton);

        clientsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                createProcessorButton.setEnabled(clientsTable.getSelectedRowCount() != 0);
            }
        });
        return createPanel;
    }


    private void changeSelectedInstance(String newInstance) {
        yconnector.disconnect();
        selectedInstance = newInstance;
        connectionParams.setInstance(newInstance);
        yconnector.connect(connectionParams);
        
        /* commandQueueDisplay.setSelectedInstance(newInstance);

        linkTableModel.clear();
        processorTableModel.clear();
        processorControl.receiveInitialConfig();
        linkControl.receiveInitialConfig();
        commandQueueDisplay.update();
        */
        setTitle("connected to "+yconnector.getUrl() + " "+selectedInstance);
        
        updateBorders();
    }

    private void updateBorders() {
        linkTableScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Data Links ("+selectedInstance+")"));
        processorTableScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Processors ("+selectedInstance+")"));
    }

    void setTitle(final String title) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.setTitle("Yamcs Monitor ("+title+")");
            }
        });
    }

    void showArchiveBrowserSelector() {
        archiveBrowserSelector.setVisible(true);
    }


    void showMessage(String msg) {
        showMessage(msg, frame);
    }

    static void showMessage(String msg, Component parent) {
        JOptionPane.showMessageDialog(parent, msg, YamcsMonitor.theApp.frame.getTitle(), JOptionPane.PLAIN_MESSAGE);
    }

    protected void destroyProcessor( int selectedRow ) {
        String name=(String)processorTableModel.getValueAt(selectedRow, 0);
        try {
            processorControl.destroyYProcessor(name);
        } catch (YamcsApiException e) {
            showMessage("Cannot destroy processor '"+name+" because the processor was already closed");
        }
    }

    protected void createProcessor( int[] selectedRows ) {
        boolean persistent=false;
        if(hasAdminRights) persistent=persistentCheckBox.isSelected();
        if (!persistent && selectedRows.length == 0 ) {
            showMessage("Please select at least one client to create the processor for.");
            return;
        }
        String name = newYProcName.getText();
        ProcessorWidget type = (ProcessorWidget)processorChooser.getSelectedItem();
        Yamcs.ReplayRequest replayRequest = type.getReplayRequest();
        if(hasAdminRights) {
            persistent=persistentCheckBox.isSelected();
        }

        if ( replayRequest == null ) return;
        int[] clients=new int[selectedRows.length];
        for(int i=0;i<selectedRows.length;i++) {
            clients[i]=(Integer) clientTableModel.getValueAt(clientsTable.convertRowIndexToModel(selectedRows[i]), 0);
        }
        try {
            //	archiveWindow.setBusyPointer();
            processorControl.createProcessor(selectedInstance, name, type.toString(), replayRequest, persistent, clients);
        } catch (Exception e) {
            showMessage(e.getMessage());
        }
        //	ArchivePanel.setNormalPointer(frame);
    }

    public void connect(YamcsConnectionProperties ycp) {
        connectionParams = ycp;
        yconnector.connect(ycp);
    }
    
    @Override
    public void actionPerformed( ActionEvent ae ) {
        String cmd = ae.getActionCommand();
        if ( cmd.equals("connect") ) {
            YamcsConnectDialogResult ycdr = YamcsConnectDialog.showDialog(frame, false, authenticationEnabled); 
            if ( ycdr.isOk() ) {
                logTextArea.removeAll();
                connect(ycdr.getConnectionProperties());
            }
        } else if ( cmd.equals("exit") ) {
            System.exit(0);
        } else if ( cmd.equals("about") ) {
            showAbout();
        }
    }

    public void showAbout()	{
        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setText("<center>" +
                "<h2>Yamcs Monitor</h2>" +
                "<h3>&copy; Space Applications Services</h3>" +
                "<h3>Version " + YamcsVersion.version + "</h3>" +
                "<p>This program is used to manage processors in a Yamcs server " +
                "and clients connected to it." +
                "</center>"
                );
        pane.setPreferredSize(new Dimension(350, 180));

        JOptionPane.showMessageDialog(frame, pane, "Yamcs Monitor", JOptionPane.PLAIN_MESSAGE, getIcon("yamcs-64x64.png"));
    }

    public ImageIcon getIcon(String imagename) {
        return new ImageIcon(getClass().getResource("/org/yamcs/images/" + imagename));
    }

    //----------- interface ConnectionListener

    @Override
    public void connecting(String corbaUrl) {
        log("connecting to "+corbaUrl);
    }

    @Override
    public void connectionFailed(String corbaUrl, YamcsException exception) {
        log("connection to "+corbaUrl+" failed: "+exception);
        connectionParams=null;
        disconnected();
    }

    @Override
    public void connected(String url) {
        log("connected to "+url);
        final List<String> instances = yconnector.getYamcsInstances();
        setTitle("connected to "+url);
        if( instances == null ) {
            log( "Failed to get instances from "+url );
            return;
        }
        if(selectedInstance==null) {
           selectedInstance = instances.get(0);
        }
        commandQueueDisplay.setSelectedInstance(selectedInstance);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                instanceMenuItem.removeAll();
                for(final String sn:instances) {
                    JMenuItem mi=new JMenuItem(sn);
                    instanceMenuItem.add(mi);
                    mi.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            changeSelectedInstance(sn);
                        }
                    });
                }

                buildClientListPopup();
                linkTableModel.clear();
                processorTableModel.clear();
                clientTableModel.clear();

                updateBorders();
            }
        });
    }

    @Override
    public void disconnected() {
        setTitle("not connected");
        linkTableModel.clear();
        processorTableModel.clear();
        clientTableModel.clear();
        updateStatistics(null);
        archiveBrowserSelector.archivePanel.disconnected();
    }


    //------------- end of interface ConnectionListener

    public ProcessorWidget getActiveProcessorWidget() {
        return (ProcessorWidget) processorChooser.getSelectedItem();
    }

/*    private void connect(final YamcsConnectData ycd) {
        yconnector.connect(ycd);
    }
*/
    @Override
    public void log(final String s) {
        System.out.println("received log: "+s);
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logTextArea.append(s+"\n");
            }
        });
    }

    /**
     * Called when a row is selected in the processor table. 
     *  Populates the tm statistics panel with information about the selected processor
     */
    @Override
    public void updateStatistics(final Statistics stats)	{
        if (statsTableModel == null) return;

        final int selectedRow = processorTable.getSelectedRow();
        if ((selectedRow == -1) || !yconnector.isConnected()) {
            processorStatusBorder.setTitle("Processor Statistics");
            processorStatusPanel.repaint();
            tmQuickStatus.setText("");
            tcQuickStatus.setText("");
            statsTableModel.setRowCount(0);
        } else {
            final int modelSelectedRow = processorTable.convertRowIndexToModel(selectedRow);
            final ProcessorInfo ci = processorTableModel.getYProcessorInfo(modelSelectedRow);

            //should perhaps setup a mechanism to send stats only for the selected processor
            if(!ci.getInstance().equals(stats.getInstance()) || !ci.getName().equals(stats.getYProcessorName())) return;


            try {
                final List<TmStatistics> tmstats = stats.getTmstatsList();
                processorStatusBorder.setTitle("Processor Information: " + ci.getName());
                processorStatusPanel.repaint();

                javax.swing.SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statsTableModel.setRowCount(0);
                        for(TmStatistics ts:tmstats) {
                            Object[] r=new Object[5];
                            r[0]=ts.getPacketName();
                            r[1]=ts.getReceivedPackets();
                            r[2]=TimeEncoding.toCombinedFormat(ts.getLastReceived());
                            r[3]=TimeEncoding.toCombinedFormat(ts.getLastPacketTime());
                            r[4]=ts.getSubscribedParameterCount();
                            statsTableModel.addRow(r);
                        }
                        if (ci.hasReplayRequest()) {
                            if (modelSelectedRow < processorTableModel.getRowCount()) {
                                archiveBrowserSelector.archivePanel.replayPanel.updateStatistics(stats);
                            }
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                showMessage(e.getMessage());
            }

        }
    }



    void buildClientListPopup() {
        final ActionListener ai = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                String instanceDotName = ae.getActionCommand();
                int[] selectedRows = clientsTable.getSelectedRows();
                int[] clients = new int[selectedRows.length];
                for(int i = 0; i < selectedRows.length; i++) {
                    ClientInfo ci = clientTableModel.get(clientsTable.convertRowIndexToModel(selectedRows[i]));
                    clients[i]=ci.getId();
                }
                try {
                    String[] in=instanceDotName.split("\\.",2);
                    processorControl.connectToProcessor(in[0], in[1], clients);
                } catch (Exception e) {
                    showMessage(e.toString());
                }
            }
        };

        clientsPopupMenu.removeAll();
        for ( String instanceDotName:allProcessors ) {
            JMenuItem mi = new JMenuItem(instanceDotName);
            mi.addActionListener(ai);
            mi.setActionCommand(instanceDotName);
            clientsPopupMenu.add(mi);
        }
        //clientsPopupMenu.revalidate();
    }


    @Override
    public void itemStateChanged(ItemEvent arg0) {
        // TODO Auto-generated method stub
    }

    /**
     * Called by the server when a processor has been added or changed
     */
    @Override
    public void processorUpdated(final ProcessorInfo ci) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                allProcessors.add(ci.getInstance()+"."+ci.getName());
                if(!ci.getInstance().equals(selectedInstance)) return;
                boolean added = processorTableModel.upsertYProc(ci);
                buildClientListPopup();
                archiveBrowserSelector.archivePanel.replayPanel.updateProcessorInfol(ci);
                if(added && ci.getHasCommanding() && hasAdminRights) {
                    commandQueueDisplay.addProcessor(ci.getInstance(), ci.getName());
                }
            }
        });
    }
    /**
     * Called by the server when a processor has been closed
     */
    @Override
    public void processorClosed(final ProcessorInfo pinfo) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                allProcessors.remove(pinfo.getInstance()+"."+pinfo.getName());
                if(!pinfo.getInstance().equals(selectedInstance)) return;
                processorTableModel.removeProcessor(pinfo.getInstance(), pinfo.getName());
                commandQueueDisplay.removeProcessor(pinfo.getInstance(), pinfo.getName());
                buildClientListPopup();
            }
        });
    }

    /**
     * Called by the server when a client has disconnected
     */
    @Override
    public void clientDisconnected(final ClientInfo ci) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                clientTableModel.removeClient(ci.getId());
            }
        });
    }

    /**
     * Called by the server when a client has changed processor or a new client has connected
     */
    @Override
    public void clientUpdated(final ClientInfo ci) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                clientTableModel.updateClient(ci);
            }
        });
    }

    @Override
    public void updateLink(final LinkInfo li) {
        if(!li.getInstance().equals(selectedInstance)) return;
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                linkTableModel.update(li);
            }
        });
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: yamcs-monitor.sh [-na] [-h] [url]");
        System.err.println("-na:\tRun in non-admin mode - hide some options which will raise no permission exception (as to not confuse the user)");
        System.err.println("url:\tConnect at startup to the given yamcs url");
        System.err.println("-h:\tShow this help text");
        System.err.println("Example:\n\t yamcs-monitor.sh http://yamcs:8090/");
        System.exit(1);
    }


    public static void main(String[] args) throws IOException, URISyntaxException, ConfigurationException {
        for(int i=0;i<args.length;i++) {
            if("-na".equals(args[i])) {
                hasAdminRights=false;
            } else if(args[i].equals("-h")) {
                printUsageAndExit();
            } else if(args[i].startsWith("http://")|| args[i].startsWith("https://")) {
                initialUrl=args[i];
            } else {
                printUsageAndExit();
            }
        } 
        YConfiguration.setup();
        final YamcsMonitor app=new YamcsMonitor();

        final YamcsConnectionProperties ycd=(initialUrl==null)?null:YamcsConnectionProperties.parse(initialUrl);
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                app.createAndShowGUI();
                if(ycd!=null) {
                    app.connect(ycd);
                }
            }
        });
    }


    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void popup(String text) {
        // TODO Auto-generated method stub
        
    }
}
