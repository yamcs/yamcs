package org.yamcs.ui.eventviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsConnectDialog;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.YamcsConnectDialog.YamcsConnectDialogResult;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.ui.YamcsConnector;
import org.yamcs.utils.TimeEncoding;

import com.csvreader.CsvWriter;

public class EventViewer extends JFrame implements ActionListener, ItemListener, MenuListener, ConnectionListener {
    private static final long serialVersionUID = 1L;
    static Logger log= LoggerFactory.getLogger(EventViewer.class);
    // colors taken from USS configuration
    final Color                                 iconColorGreen         = new Color(0x86B78A);
    final Color                                 iconColorRed           = new Color(0xB88687);
    Color                                       iconColorGrey;                               // obtained
                                                                                              // during
                                                                                              // gui
                                                                                              // build

    EventTableModel                             tableModel             = null;
    TableRowSorter<EventTableModel>             tableSorter            = null;
    public JTextArea                            logTextArea            = null;
    JMenuItem                                   miAutoScroll           = null;
    JMenuItem                                   miShowErrors           = null;
    JMenuItem                                   miRetrievePast         = null;
    EventTable                                  eventTable             = null;
    JScrollPane                                 eventPane              = null;

    JLabel                                      labelEventCount        = null;
    JLabel                                      labelWarnings          = null;
    JLabel                                      labelErrors            = null;
    JLabel                                      fwLabel                = null;
    JLabel                                      upLabel                = null;
    JLabel                                      dnLabel                = null;

    Icon                                        fwOKIcon               = null;
    Icon                                        fwNOKIcon              = null;
    Icon                                        upOKIcon               = null;
    Icon                                        upNOKIcon              = null;
    Icon                                        dnOKIcon               = null;
    Icon                                        dnNOKIcon              = null;

    int                                         eventCount             = 0;
    int                                         warningCount           = 0;
    int                                         errorCount             = 0;

    JFileChooser                                filechooser            = null;
    PreferencesDialog                           preferencesDialog      = null;
    EventReceiver                               eventReceiver          = null;
    YamcsConnector                              yconnector             = null;

    String                                      currentUrl             = null;
    String                                      currentChannel         = null;
    boolean                                     connected              = false;
    Thread                                      connectingThread       = null;
    private String                              soundFile              = null;
    List<Map<String,String>>                    extraColumns           = null;

    private Clip                                alertClip              = null;
    private JPopupMenu                          popupMenu              = null;

    /** Table model with filtering table */
    private FilteringRulesTable                 rules                  = null;

    /** View menu */
    private JMenu                               viewMenu               = null;

    /** Mapping of filtering rules into menu */
    private HashMap<JCheckBoxMenuItem, Integer> viewMenuFilterChkBoxes = null;
    
    boolean authenticationEnabled = false;
    /**
     * Read properties from configuration file
     */
    private void readConfiguration() throws ConfigurationException {
        YConfiguration cfg = null;

        cfg = YConfiguration.getConfiguration("event-viewer");
        if(cfg.containsKey("soundfile")) {
            soundFile = cfg.getString("soundfile");
        }
        if(cfg.containsKey("extraColumns")) {
            extraColumns=cfg.getList("extraColumns");
        }
    }

    /**
     * Access to table with filtering rules
     * @return Instance of the filtering table
     */
    public FilteringRulesTable getFilteringRulesTable() {
        if (rules == null) {
            rules = new FilteringRulesTable();
        }
        return rules;
    }

    public EventTableModel getEventTable() {
        return tableModel;
    }

    public EventViewer(YamcsConnector yc, final EventReceiver eventReceiver) throws ConfigurationException {
        super();
        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        if(config.containsKey("authenticationEnabled")) {
            authenticationEnabled = config.getBoolean("authenticationEnabled");
        }
        
        this.yconnector=yc;
        this.eventReceiver = eventReceiver;
        
        readConfiguration();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				eventTable.storePreferences();
				dispose();
			}
        });
        
        setIconImage(getIcon("yamcs-event-32.png").getImage());

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("shutting down");
                yconnector.disconnect();
            }
        });

        eventCount = 0;
        warningCount = 0;
        errorCount = 0;
        
        
        //
        // menu
        //

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menu);
        
        // Ctrl on win/linux, Command on mac
        int menuKey=Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        JMenuItem menuItem = new JMenuItem("Connect to Yamcs...");
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuKey));
        menuItem.addActionListener(this);
        menuItem.setActionCommand("connect");
        menu.add(menuItem);

        menu.addSeparator();
        miRetrievePast = new JMenuItem("Retrieve Past Events...");
        miRetrievePast.addActionListener(this);
        miRetrievePast.setActionCommand("retrieve_past");
        menu.add(miRetrievePast);
        miRetrievePast.setEnabled(false);
        menu.addSeparator();

        menuItem = new JMenuItem("Save As...", KeyEvent.VK_S);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuKey));
        menuItem.addActionListener(this);
        menuItem.setActionCommand("save");
        menu.add(menuItem);

        menu.addSeparator();

        menuItem = new JMenuItem("Quit", KeyEvent.VK_Q);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuKey));
        menuItem.getAccessibleContext().setAccessibleDescription("Quit the event viewer");
        menuItem.addActionListener(this);
        menuItem.setActionCommand("exit");
        menu.add(menuItem);

        // Edit menu
        menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        menuItem = new JMenuItem("Preferences", KeyEvent.VK_P);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuKey));
        menuItem.getAccessibleContext().setAccessibleDescription("Edit preferences");
        menuItem.addActionListener(this);
        menuItem.setActionCommand("preferences");
        menu.add(menuItem);

        menuBar.add(menu);

        // View menu
        viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        viewMenu.addMenuListener(this);

        miAutoScroll = new JCheckBoxMenuItem("Auto-Scroll");
        miAutoScroll.setSelected(true);
        viewMenu.add(miAutoScroll);

        viewMenu.addSeparator();
        menuItem = new JMenuItem("Clear", KeyEvent.VK_C);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuKey));
        menuItem.addActionListener(this);
        menuItem.setActionCommand("clear");
        viewMenu.add(menuItem);
        
        menuBar.add(viewMenu);


        populateViewMenu();

        viewMenu.addSeparator();
        //
        // frame content
        //

        Box panel = Box.createHorizontalBox();
        getContentPane().add(panel, BorderLayout.SOUTH);
        panel.add(Box.createHorizontalStrut(20));

        panel.add(new JLabel("Total Events:"));
        labelEventCount = new JLabel(String.valueOf(eventCount));
        labelEventCount.setPreferredSize(new Dimension(50, labelEventCount.getPreferredSize().height));
        labelEventCount.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        labelEventCount.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(labelEventCount);

        panel.add(Box.createHorizontalStrut(20));
        panel.add(new JLabel("Warnings:"));
        labelWarnings = new JLabel(String.valueOf(warningCount));
        labelWarnings.setPreferredSize(new Dimension(50, labelWarnings.getPreferredSize().height));
        labelWarnings.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        labelWarnings.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(labelWarnings);

        panel.add(Box.createHorizontalStrut(20));
        panel.add(new JLabel("Errors:"));
        labelErrors = new JLabel(String.valueOf(errorCount));
        labelErrors.setPreferredSize(new Dimension(50, labelErrors.getPreferredSize().height));
        labelErrors.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        labelErrors.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(labelErrors);

        panel.add(Box.createHorizontalGlue());

        fwOKIcon = getIcon("fwLinkActive.gif");
        fwNOKIcon = getIcon("fwLinkInactive.gif");
        upOKIcon = getIcon("upLinkActive.gif");
        upNOKIcon = getIcon("upLinkInactive.gif");
        dnOKIcon = getIcon("dnLinkActive.gif");
        dnNOKIcon = getIcon("dnLinkInactive.gif");

        fwLabel = new JLabel(fwNOKIcon);
        fwLabel.setOpaque(true);
        fwLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panel.add(fwLabel);
        iconColorGrey = fwLabel.getBackground();
        upLabel = new JLabel(upNOKIcon);
        upLabel.setOpaque(true);
        upLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panel.add(upLabel);
        dnLabel = new JLabel(dnNOKIcon);
        dnLabel.setOpaque(true);
        dnLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panel.add(dnLabel);

        // event table

        tableModel = new EventTableModel(getFilteringRulesTable(), extraColumns);
        eventTable = new EventTable(tableModel);

        eventTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        eventTable.setPreferredScrollableViewportSize(new Dimension(920, 400));

        tableSorter = new TableRowSorter<EventTableModel>(tableModel);
        eventTable.setRowSorter(tableSorter);

        final TableColumnModel tcm = eventTable.getColumnModel();
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.SOURCE_COL)).setMaxWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.GENERATION_TIME_COL)).setMaxWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.RECEPTION_TIME_COL)).setMaxWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.EVENT_TYPE_COL)).setMaxWidth(150);

        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.SOURCE_COL)).setPreferredWidth(100);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.GENERATION_TIME_COL)).setPreferredWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.RECEPTION_TIME_COL)).setPreferredWidth(100);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.EVENT_TYPE_COL)).setPreferredWidth(100);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.EVENT_TEXT_COL)).setPreferredWidth(400);

        eventPane = new JScrollPane(eventTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // status log area

        logTextArea = new JTextArea(5, 20);
        logTextArea.setEditable(false);
        JScrollPane logPane = new JScrollPane(logTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventPane, logPane);
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        getContentPane().add(split, BorderLayout.CENTER);

        // popup menu
        popupMenu = new JPopupMenu();
        JMenuItem item = new JMenuItem("New filtering rule");
        item.setActionCommand("new_rule_popup");
        item.addActionListener(this);
        popupMenu.add(item);
        item=new JMenuItem("Details...");
        item.setActionCommand("show_event_details");
        item.addActionListener(this);
        popupMenu.add(item);
        popupMenu.setBorder(new BevelBorder(BevelBorder.RAISED));
        eventTable.addMouseListener(new MousePopupListener());

        
        // prepare model names

        updateStatus();
        pack();
        setLocation(30, 30);
        setVisible(true);
        
        eventReceiver.setEventViewer(this);
        yconnector.addConnectionListener(this);
    }

    public void populateViewMenu() {
        if (viewMenuFilterChkBoxes == null) {
            viewMenuFilterChkBoxes = new HashMap<JCheckBoxMenuItem, Integer>(25);
        }

        for (JCheckBoxMenuItem item : viewMenuFilterChkBoxes.keySet()) {
            viewMenu.remove(item);
        }

        viewMenuFilterChkBoxes.clear();

        JCheckBoxMenuItem item = null;
        Vector<FilteringRule> rules = getFilteringRulesTable().getRules();

        String label = "";
        for (int i = 0; i < rules.size(); ++i) {
            FilteringRule rule = rules.elementAt(i);

            label = (rule.isShowOn()) ? "Show - " : "Hide - ";

            label += rule.getName();

            item = new JCheckBoxMenuItem(label);
            item.setSelected(rule.isActive());
            getFilteringRulesTable().writeFilteringRules();
            item.addActionListener(this);
            item.setActionCommand("switch_rule_status");

            viewMenu.add(item);

            viewMenuFilterChkBoxes.put(item, i);
        }
    }

    /**
     * Shows event in the detail dialog
     * @param event Event to be presented
     */
    private void showEventInDetailDialog(Event event) {
        EventDialog detailDialog = new EventDialog(this);
        detailDialog.setEvent(event);
        detailDialog.setVisible(true);
    }

    // ***********
    // An inner class to check whether mouse events are the popup trigger
    class MousePopupListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            checkPopup(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            checkPopup(e);
            if (e.getClickCount() == 2) {
                JTable target = (JTable) e.getSource();
                int row = target.getSelectedRow();
                showEventInDetailDialog(((EventTableModel)target.getModel()).getEvent(row));
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            checkPopup(e);
        }

        private void checkPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                // show the popup menu and select the row under the mouse pointer.
                // If multiple rows are selected keep them selected, unless the right
                // click landed on a previously unselected row.
                int row = eventTable.rowAtPoint(e.getPoint());
                if(!eventTable.getSelectionModel().isSelectedIndex(row)) {
                    eventTable.getSelectionModel().setSelectionInterval(row, row);
                } else {
                    popupMenu.show(eventTable, e.getX(), e.getY());
                }
            }
        }
    }

    // ****************

    public ImageIcon getIcon(String imagename) {
        return new ImageIcon(getClass().getResource("/org/yamcs/images/" + imagename));
    }

    public void updateStatus()  {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                StringBuffer title = new StringBuffer("Event Viewer");

                if (yconnector.isConnected()) {
                    if (miRetrievePast != null) miRetrievePast.setEnabled(true);
                    title.append(" (connected)");
                    fwLabel.setBackground(iconColorGreen);
                    fwLabel.setIcon(fwOKIcon);
                    upLabel.setBackground(iconColorGreen);
                    upLabel.setIcon(upOKIcon);
                    dnLabel.setBackground(iconColorGreen);
                    dnLabel.setIcon(dnOKIcon);

                } else if (yconnector.isConnecting()) {
                    if (miRetrievePast != null)
                        miRetrievePast.setEnabled(false);
                    title.append(" (connecting)");
                    fwLabel.setBackground(iconColorGrey);
                    upLabel.setBackground(iconColorGrey);
                    dnLabel.setBackground(iconColorGrey);
                } else {
                    if (miRetrievePast != null)
                        miRetrievePast.setEnabled(false);
                    title.append(" (not connected)");
                    fwLabel.setBackground(iconColorGrey);
                    upLabel.setBackground(iconColorGrey);
                    dnLabel.setBackground(iconColorGrey);
                }
                setTitle(title.toString());
            }
        });
    }
    @Override
    public void connected(String url) {
        log("Connected to "+url);
        updateStatus();
    }

    @Override
    public void connecting(String url) {
        log("Connecting to "+url);
        updateStatus();
    }

    @Override
    public void disconnected() {
        log("Disconnected");
        updateStatus();
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        log("Connection to "+url+" failed: "+exception);
        updateStatus();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("connect")) {
            YamcsConnectDialogResult r = YamcsConnectDialog.showDialog(this, true, authenticationEnabled);
            if( r.isOk() ) {
            	yconnector.connect(r.getConnectionProperties());
            }
        } else if (cmd.equals("retrieve_past")) {
            eventReceiver.retrievePastEvents();
        } else if (cmd.equals("switch_rule_status")) {
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
            int index = viewMenuFilterChkBoxes.get(item);

            getFilteringRulesTable().switchRuleActivation(index, item.isSelected());
        } else if (cmd.equals("clear")) {
            clearTable();
        } else if (cmd.equals("save")) {
            saveTableAs();
        } else if (cmd.equals("exit")) {
            processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        } else if (cmd.equals("preferences")) {
            getPreferencesDialog().setVisible(true);
        } else if (cmd.equals("new_rule_popup")) {
            int[] rows = eventTable.getSelectedRows();
            for (int row : rows) {
                FilteringRule rule = new FilteringRule();
                Event event = ((EventTableModel) eventTable.getModel()).getEvent(row);
                rule.setSource(event.getSource());
                rule.setEventType(event.getType());
                rule.setEventText(event.getMessage());
                rule.setSeverity(event.getSeverity());
                getPreferencesDialog().addNewRule(rule);
            }

            getPreferencesDialog().setVisible(true);
        } else if (cmd.equals("show_event_details")) {
            showEventInDetailDialog(((EventTableModel)eventTable.getModel()).getEvent(eventTable.getSelectedRow()));
        }
    }

    private void showAlertPopupDialog(Event event) {
        int messageType = JOptionPane.INFORMATION_MESSAGE;
        switch (event.getSeverity()) {
        case INFO:
            messageType = JOptionPane.INFORMATION_MESSAGE;
            break;
        case WARNING:
            messageType = JOptionPane.WARNING_MESSAGE;
            break;
        case ERROR:
            messageType = JOptionPane.ERROR_MESSAGE;
            break;
        default:
            messageType = JOptionPane.INFORMATION_MESSAGE;
        }

        JOptionPane.showMessageDialog(this, event.toString(), "Alert message", messageType);
    }

    /**
     * Shows Preferences dialog (modal)
     */
    private PreferencesDialog getPreferencesDialog() {
        if (preferencesDialog == null) {
            preferencesDialog = new PreferencesDialog(this, ModalityType.APPLICATION_MODAL);
        }
        return preferencesDialog;
    }


    @Override
    public void itemStateChanged(ItemEvent arg0) {
    }

    @Override
    public void log(final String s) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logTextArea.append(TimeEncoding.toCombinedFormat(TimeEncoding.currentInstant()) + " " + s + "\n");
            }
        });
    }

    void clearTable() {
        tableModel.clear();
        eventCount = 0;
        warningCount = 0;
        errorCount = 0;
        labelEventCount.setText(String.valueOf(eventCount));
        labelWarnings.setText(String.valueOf(warningCount));
        labelErrors.setText(String.valueOf(errorCount));
        eventTable.repaint();
    }

    void showMessage(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Event Viewer", JOptionPane.ERROR_MESSAGE);
    }

    class ExtendedFileFilter extends FileFilter {
        public String  ext;
        private String desc;

        public ExtendedFileFilter(String ext, String desc) {
            this.ext = ext;
            this.desc = desc;
        }

        @Override
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith("." + ext);
        }

        @Override
        public String getDescription() {
            return desc;
        }

        public File appendExtensionIfNeeded(File f) {
            return accept(f) ? f : new File(f.getPath() + "." + ext);
        }
    }

    void saveTableAs() {
        if (filechooser == null) {
            filechooser = new JFileChooser() {
                private static final long serialVersionUID = 1L;

                @Override
                public File getSelectedFile() {
                    File file = super.getSelectedFile();
                    if (getFileFilter() instanceof ExtendedFileFilter) {
                        file = ((ExtendedFileFilter) getFileFilter()).appendExtensionIfNeeded(file);
                    }
                    return file;
                }
                
                @Override
                public void approveSelection() {
                    File file=getSelectedFile();
                    if(file.exists()) {
                        int response=JOptionPane.showConfirmDialog(filechooser, "The file "+file+" already exists. Do you want to replace the existing file?", "Overwrite file", JOptionPane.YES_NO_OPTION);
                        if(response!=JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                    super.approveSelection();
                }
            };
            filechooser.setMultiSelectionEnabled(false);
            FileFilter csvFilter=new ExtendedFileFilter("csv", "CSV File");
            filechooser.addChoosableFileFilter(csvFilter);
            FileFilter txtFilter=new ExtendedFileFilter("txt", "Text File");
            filechooser.addChoosableFileFilter(txtFilter);
            filechooser.setFileFilter(csvFilter); // By default, choose CSV
        }
        int ret = filechooser.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file=filechooser.getSelectedFile();
            CsvWriter writer=null;
            try {
                writer=new CsvWriter(new FileOutputStream(file), '\t', Charset.forName("UTF-8"));
                int cols = tableModel.getColumnCount();
                String[] colNames = new String[cols];
                colNames[0] = "Source";
                colNames[1] = "Generation Time";
                colNames[2] = "Reception Time";
                colNames[3] = "Event Type";
                colNames[4] = "Event Text";
                for (int i = 5; i < cols; i++) {
                    colNames[i] = tableModel.getColumnName(i);
                }
                writer.writeRecord(colNames);
                writer.setForceQualifier(true);
                int iend = tableModel.getRowCount();
                for (int i = 0; i < iend; i++) {
                    String[] rec = new String[cols];
                    rec[0] = (String) tableModel.getValueAt(i, 0);
                    rec[1] = (String) tableModel.getValueAt(i, 1);
                    rec[2] = (String) tableModel.getValueAt(i, 2);
                    rec[3] = (String) tableModel.getValueAt(i, 3);
                    rec[4] = ((Event) tableModel.getValueAt(i, 4)).getMessage();
                    for (int j = 5; j < cols; j++) {
                        rec[j] = (String) tableModel.getValueAt(i, j);
                    }
                    writer.writeRecord(rec);
                }
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Could not export events to file '" + file.getPath() + "': " + e.getMessage());
            } finally {
                writer.close();
            }
            log("Saved table to " + file.getAbsolutePath());
        }
    }

    public void addEvents(final List<Event> events) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                tableModel.addEvents(events);
                for (Event event : events)  {
                    switch (event.getSeverity())  {
                    case WARNING:
                        ++warningCount;
                        break;
                    case ERROR:
                        ++errorCount;
                        break;
                    }
                    ++eventCount;
                }
                labelEventCount.setText(String.valueOf(eventCount));
                labelWarnings.setText(String.valueOf(warningCount));
                labelErrors.setText(String.valueOf(errorCount));
                eventTable.revalidate();
                eventPane.validate();
                if (miAutoScroll.isSelected()) {
                    updateVerticalScrollPosition();
                }
            }
        });
    }

    /**
     * Add event. This method is used for incoming events.
     * @param event Event to be added.
     */
    public void addEvent(final Event event) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                switch (event.getSeverity()) {
                case WARNING:
                    ++warningCount;
                    labelWarnings.setText(String.valueOf(warningCount));
                    break;
                case ERROR:
                    ++errorCount;
                    labelErrors.setText(String.valueOf(errorCount));
                    break;
                }
                ++eventCount;
                labelEventCount.setText(String.valueOf(eventCount));

                tableModel.addEvent(event);

                // auto-resize text column (does not resize scrollview)
                /*
                 * TableColumn col = eventTable.getColumnModel().getColumn(4);
                 * Component cell = eventTable.getCellRenderer(0,
                 * 4).getTableCellRendererComponent(eventTable, event, false,
                 * false, 0, 4); int w = cell.getPreferredSize().width + 10; if
                 * (w > col.getPreferredWidth()) { col.setPreferredWidth(w); }
                 */

                eventTable.revalidate();
                eventPane.validate();

                if (miAutoScroll.isSelected()) {
                    updateVerticalScrollPosition();
                }

                // alert the user if necessary
                AlertType alert = getFilteringRulesTable().getAlertType(event);
                if (alert.alertSound) {
                    playAlertSound();
                }
                if (alert.alertPopup) {
                    showAlertPopupDialog(event);
                }
            }
        });
    }
    
    /**
     * Adjusts vertical scroll position
     * (horizontal position remains unchanged)
     */
    private void updateVerticalScrollPosition() {
        if(eventTable.getRowCount()<=0) return;
        
        int row = eventTable.convertRowIndexToView(eventTable.getRowCount()-1);
        int col = eventTable.convertColumnIndexToView(0);
        Rectangle rect = eventTable.getCellRect(row, col, true);
        
        // Retain view's x position
        int x = eventTable.getVisibleRect().x;
        
        // y should correctly show full contents of multiline text cells.
        int y = rect.y;
        int textViewColumn = eventTable.convertColumnIndexToView(EventTableModel.EVENT_TEXT_COL);
        TableCellRenderer renderer = eventTable.getCellRenderer(row, textViewColumn);
        if (renderer instanceof EventTableRenderer) {
            Object value = eventTable.getValueAt(row, textViewColumn);
            // Trigger an update of the row height
            int actualHeight = ((EventTableRenderer)renderer).updateCalculatedHeight(eventTable, value, row);
            y += (actualHeight - rect.height);
        }
        
        rect.setLocation(new Point(x, y));
        eventTable.scrollRectToVisible(rect);
    }

    /**
     * Play the sound
     */
    private void playAlertSound() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                playSoundFile();
            }
        }).start();
    }

    /**
     * Access to sound clip.
     * 
     * @return
     */
    private Clip getAlertClip() {
        if (alertClip == null)  {
            String defFileName = "/org/yamcs/sounds/alert.wav";
            try {
                AudioInputStream inputStream = null;
                if (soundFile == null) {
                    inputStream = AudioSystem.getAudioInputStream(new BufferedInputStream(getClass().getResourceAsStream(defFileName)));
                } else {
                    inputStream = AudioSystem.getAudioInputStream(new File(soundFile));
                }
                AudioFormat format = inputStream.getFormat();
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                alertClip = (Clip)AudioSystem.getLine(info);
                alertClip.open(inputStream);
            } catch (Exception e) {
                log.warn("Error occured while playing sound clip ", e);
                alertClip = null;
            }
        }
        return alertClip;
    }

    /**
     * Plays file. This method is not synchronized.
     * 
     * @param fileName File with the sound resource
     * @todo This method should be accessed only by one thread.....
     */
    private void playSoundFile() {
        Clip clip = null;

        try {
            clip = getAlertClip();
            if (clip == null)
                return;

            clip.stop();
            clip.setFramePosition(0);
            clip.start();

            // sleep while the sound is playing
            Thread.sleep(clip.getMicrosecondLength());

        } catch (InterruptedException ie) {
            // ok
        } catch (Exception e) {
            System.err.println("Error occured while playing the clip: " + e.getMessage());
        } finally {
            if (clip != null) {
                clip.stop();
                clip.setFramePosition(0);
                
            }
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage event-viewer.sh yamcsurl");
        System.err.println("Example:\n\tevent-viewer.sh http://localhost:8090/yops");
        System.exit(-1);
    }

    void test(EventViewer ev) {
        // test
        for (int evId = 0; evId < 12; ++evId) {
            Event.Builder builder = Event.newBuilder();

            builder.setGenerationTime(5000);
            builder.setReceptionTime(56000);
            builder.setMessage("This is eventX message");
            builder.setSource("ACES");
            builder.setType("ASW_CMD_ERR aaa");
            builder.setSeqNumber(evId);
            builder.setSeverity(EventSeverity.ERROR);
            Event event = builder.build();
            ev.addEvent(event);
        }

        for (int evId = 0; evId < 12; ++evId) {
            Event.Builder builder = Event.newBuilder();

            builder.setGenerationTime(5000);
            builder.setReceptionTime(56000);
            builder.setMessage("This is event message");
            builder.setSource("ACES_X");
            builder.setType("ASW_CMD_ERR");
            builder.setSeqNumber(evId);
            Event event = builder.build();
            ev.addEvent(event);
        }
    }
    
    //Not used for the moment. TODO
    public void setStatusTm(String opsname, String value) {
        if (opsname.equals("CDMCS_FWLINK_STATUS")) {
            if (value.equalsIgnoreCase("OK")) {
                fwLabel.setBackground(iconColorGreen);
                fwLabel.setIcon(fwOKIcon);
            } else {
                fwLabel.setBackground(iconColorRed);
                fwLabel.setIcon(fwNOKIcon);
            }
            fwLabel.setToolTipText(opsname + " = " + value);
        } else if (opsname.equals("CDMCS_UPLINK_STATUS")) {
            if (value.equalsIgnoreCase("OK")) {
                upLabel.setIcon(upOKIcon);
                upLabel.setBackground(iconColorGreen);
            } else {
                upLabel.setIcon(upNOKIcon);
                upLabel.setBackground(iconColorRed);
            }
            upLabel.setToolTipText(opsname + " = " + value);
        } else if (opsname.equals("CDMCS_DOWNLINK_STATUS")) {
            if (value.equalsIgnoreCase("OK")) {
                dnLabel.setIcon(dnOKIcon);
                dnLabel.setBackground(iconColorGreen);
            } else {
                dnLabel.setIcon(dnNOKIcon);
                dnLabel.setBackground(iconColorRed);
            }
            dnLabel.setToolTipText(opsname + " = " + value);
        }
    }

    /**
     * Application entry point.
     * 
     * @param args
     * @throws IOException
     * @throws ConfigurationException
     * @throws URISyntaxException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     * @throws ClassNotFoundException 
     */
    public static void main(String[] args) throws IOException, ConfigurationException, URISyntaxException, ClassNotFoundException, InstantiationException, IllegalAccessException  {
        if (args.length > 1)  printUsageAndExit();
        YConfiguration.setup();
        YamcsConnectionProperties ycd = null;
        if(args.length==1) {
            if (args[0].startsWith("http")) {
                ycd = YamcsConnectionProperties.parse(args[0]);
            } else {
                printUsageAndExit();
            }        
        } 
        YamcsConnector yconnector=new YamcsConnector("EventViewer");
        YamcsEventReceiver eventReceiver = new YamcsEventReceiver(yconnector);
        EventViewer ev = new EventViewer(yconnector, eventReceiver);
        if(ycd!=null) yconnector.connect(ycd);
    }

    @Override
    public void menuSelected(MenuEvent e) {
        populateViewMenu();
    }

    @Override
    public void menuDeselected(MenuEvent e) {
        // do nothing
    }

    @Override
    public void menuCanceled(MenuEvent e) {
        // do nothing
    }
}
