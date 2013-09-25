package org.yamcs.ui.eventviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;


import org.yamcs.api.ConnectionListener;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsConnectDialog;
import org.yamcs.api.YamcsConnector;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;

public class EventViewer extends JFrame implements ActionListener, ItemListener, MenuListener, ConnectionListener {
    static Logger log= LoggerFactory.getLogger(YamcsConnector.class);
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
    JTable                                      eventTable             = null;
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
    private void readConfiruration() throws ConfigurationException {
        YConfiguration cfg = null;

        cfg = YConfiguration.getConfiguration("event-viewer");
        if(cfg.containsKey("soundfile")) {
            soundFile = cfg.getString("soundfile");
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
        
        readConfiruration();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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

        JMenuItem menuItem = new JMenuItem("Connect");
        menuItem.addActionListener(this);
        menuItem.setActionCommand("connect");
        menu.add(menuItem);

        menu.addSeparator();
        miRetrievePast = new JMenuItem("Retrieve Past Events");
        miRetrievePast.addActionListener(this);
        miRetrievePast.setActionCommand("retrieve_past");
        menu.add(miRetrievePast);
        miRetrievePast.setEnabled(false);
        menu.addSeparator();

        menuItem = new JMenuItem("New", KeyEvent.VK_N);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        menuItem.addActionListener(this);
        menuItem.setActionCommand("new");
        menu.add(menuItem);

        menuItem = new JMenuItem("Save As ...", KeyEvent.VK_S);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        menuItem.addActionListener(this);
        menuItem.setActionCommand("save");
        menu.add(menuItem);

        menu.addSeparator();

        menuItem = new JMenuItem("Quit", KeyEvent.VK_Q);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
        menuItem.getAccessibleContext().setAccessibleDescription("Quit the event viewer");
        menuItem.addActionListener(this);
        menuItem.setActionCommand("exit");
        menu.add(menuItem);

        // Edit menu
        menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        menuItem = new JMenuItem("Preferences", KeyEvent.VK_P);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
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

        populateViewMenu();
        menuBar.add(viewMenu);

        //
        // frame content
        //

        Box panel = Box.createHorizontalBox();
        getContentPane().add(panel, BorderLayout.SOUTH);

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

        tableModel = new EventTableModel(getFilteringRulesTable());
        final EventTableRenderer renderer = new EventTableRenderer();
        eventTable = new JTable(tableModel) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                if (column == 4) {
                    return renderer;
                }
                return super.getCellRenderer(row, column);
            }

        };

        eventTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        eventTable.setPreferredScrollableViewportSize(new Dimension(920, 400));

        tableSorter = new TableRowSorter<EventTableModel>(tableModel);
        eventTable.setRowSorter(tableSorter);

        final TableColumnModel tcm = eventTable.getColumnModel();
        tcm.getColumn(0).setMaxWidth(200);
        tcm.getColumn(1).setMaxWidth(200);
        tcm.getColumn(2).setMaxWidth(200);
        tcm.getColumn(3).setMaxWidth(150);

        tcm.getColumn(0).setPreferredWidth(100);
        tcm.getColumn(1).setPreferredWidth(200);
        tcm.getColumn(2).setPreferredWidth(100);
        tcm.getColumn(3).setPreferredWidth(100);
        tcm.getColumn(4).setPreferredWidth(400);

        eventPane = new JScrollPane(eventTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // status log area

        logTextArea = new JTextArea(5, 20);
        logTextArea.setEditable(false);
        JScrollPane logPane = new JScrollPane(logTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventPane, logPane);
        split.setResizeWeight(1.0);
        getContentPane().add(split, BorderLayout.CENTER);

        // popup menu
        popupMenu = new JPopupMenu();
        JMenuItem item = new JMenuItem("New filtering rule");
        item.setActionCommand("new_rule_popup");
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
                // show the popup menu and select the row under the mouse
                // pointer
                popupMenu.show(eventTable, e.getX(), e.getY());
                int row = eventTable.rowAtPoint(e.getPoint());
                eventTable.getSelectionModel().setSelectionInterval(row, row);
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
            YamcsConnectData ycd=YamcsConnectDialog.showDialog(this, true, authenticationEnabled);
            if( ycd.isOk ) {
            	yconnector.connect(ycd);
            }
        } else if (cmd.equals("retrieve_past")) {
            eventReceiver.retrievePastEvents();
        } else if (cmd.equals("switch_rule_status")) {
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
            int index = viewMenuFilterChkBoxes.get(item);

            getFilteringRulesTable().switchRuleActivation(index, item.isSelected());
        } else if (cmd.equals("new")) {
            clearTable();
        } else if (cmd.equals("save")) {
            saveTableAs();
        } else if (cmd.equals("exit")) {
            System.exit(0);
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
            return !f.isFile() || f.getName().toLowerCase().endsWith("." + ext);
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
            filechooser = new JFileChooser();
            filechooser.setMultiSelectionEnabled(false);
            filechooser.addChoosableFileFilter(new ExtendedFileFilter("csv", "CSV Files"));
            filechooser.addChoosableFileFilter(new ExtendedFileFilter("txt", "Text Files"));
        }
        int ret = filechooser.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            FileFilter filt = filechooser.getFileFilter();
            File file = filechooser.getSelectedFile();
            if (filt instanceof ExtendedFileFilter) {
                file = ((ExtendedFileFilter) filt).appendExtensionIfNeeded(file);
            }
            try {
                PrintStream out = new PrintStream(file);
                int iend = tableModel.getRowCount();
                
                /*
                 * $UHB_UI_040
                 * Add column names as part of the Event Viewer CVS output.
                 * (2.5.4 Include header line in event viewer output files.)
                 * Initial implementation by ES 
                 * 
                 *  TO DO: Code review (by NM, MU, or TN?)
                 */
                String headerLine = "Source\tGeneration Time\tReception Time" 
                 							   + "\tEvent Type\tEvent Text\n";
                out.printf(headerLine);
                /*
                 * End of $UHB_UI_040 implementation.
                 */
                
                for (int row = 0; row < iend; ++row) {
                    out.printf("\"%s\"\t\"%s\"\t\"%s\"\t\"%s\"\t\"%s\"\n", tableModel.getValueAt(row, 0), tableModel.getValueAt(row, 1), tableModel.getValueAt(row, 2), tableModel.getValueAt(row, 3), ((Event) tableModel.getValueAt(row, 4)).getMessage());
                }
                out.close();
                log("Saved table to " + file.getAbsolutePath());
            } catch (IOException x) {
                showMessage("Cannot open file '" + file.getPath() + "' for writing: " + x.getMessage());
            }
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
                    eventPane.getVerticalScrollBar().setValue(eventPane.getVerticalScrollBar().getMaximum());
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
                    eventPane.getVerticalScrollBar().setValue(eventPane.getVerticalScrollBar().getMaximum());
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
     * Play the sound
     */
    private void playAlertSound() {
        System.out.println("playing alert sound!!!!");
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
            Thread.currentThread().sleep(clip.getMicrosecondLength());

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
        System.err.println("Example:\n\tevent-viewer.sh yamcs://localhost:5445/yops");
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
        YamcsConnectData ycd=null;
        if(args.length==1) {
            if (args[0].startsWith("yamcs://")) {
                ycd=YamcsConnectData.parse(args[0]);
            } else {
                printUsageAndExit();
            }        
        } 
        YamcsConnector yconnector=new YamcsConnector();
        YamcsEventReceiver eventReceiver = new YamcsEventReceiver(yconnector);
        EventViewer ev=new EventViewer(yconnector, eventReceiver);
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

class EventTableModel extends AbstractTableModel implements Observer {
    /** Column names */
    static final String[]   columnNames         = { "Source", "Generation Time", "Reception Time", "Event Type", "Event Text" };

    /** Column indices */
    public static final int SOURCE_COL          = 0;
    public static final int GENERATION_TIME_COL = 1;
    public static final int RECEPTION_TIME_COL  = 2;
    public static final int EVENT_TYPE_COL      = 3;
    public static final int EVENT_TEXT_COL      = 4;

    /** Vector with all events */
    private Vector<Event>   allEvents           = null;

    /** Parallel data model with only visible events */
    private Vector<Event>   visibleEvents       = null;

    /** Filtering table */
    FilteringRulesTable     filteringTable      = null;

    /**
     * Constructor.
     */
    public EventTableModel(FilteringRulesTable table) {
        allEvents = new Vector<Event>();
        visibleEvents = new Vector<Event>();
        filteringTable = table;
        filteringTable.registerObserver(this);
    }

    @Override
    public int getRowCount() {
        return visibleEvents.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object value = null;
        Event event = visibleEvents.elementAt(rowIndex);

        switch (columnIndex)
        {
        case EventTableModel.SOURCE_COL:
            value = event.getSource();
            break;
        case EventTableModel.EVENT_TEXT_COL:
            // return event here to be compatible with older code
            value = event;
            break;
        case EventTableModel.EVENT_TYPE_COL:
            value = event.getType();
            break;
        case EventTableModel.GENERATION_TIME_COL:
            value = TimeEncoding.toString(event.getGenerationTime());
            break;
        case EventTableModel.RECEPTION_TIME_COL:
            value = TimeEncoding.toString(event.getReceptionTime());
            break;
        default:
            break;
        }

        return value;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    /**
     * Add list of events into model.
     * @param eventList List of events to be added.
     */
    public void addEvents(final List<Event> eventList) {
        allEvents.addAll(eventList);

        int firstr = visibleEvents.size();
        for (Event event : eventList) {
            if (filteringTable.isEventVisible(event))
            {
                visibleEvents.add(event);
            }
        }
        int lastr = visibleEvents.size() - 1;

        if (firstr <= lastr) {
            fireTableRowsInserted(firstr, lastr);
        }
    }

    /**
     * Add single event into model.
     * @param event Event to be added.
     */
    public void addEvent(final Event event) {
        allEvents.add(event);

        if (filteringTable.isEventVisible(event))
        {
            visibleEvents.add(event);
            int addedRow = visibleEvents.size() - 1;
            fireTableRowsInserted(addedRow, addedRow);
        }
    }

    /**
     * Access to event on the specific row.
     * @param row Row
     * @return Event on the row.
     */
    public Event getEvent(int row)
    {
        return visibleEvents.elementAt(row);
    }

    /**
     * Remove all events from the model.
     */
    public void clear()
    {
        allEvents.clear();
        visibleEvents.clear();
    }

    /**
     * Apply new filtering rules. After the change of filtering rules the
     * visible data must conform with them.
     */
    public void applyNewFilteringRules()
    {
        visibleEvents.clear();

        for (Event event : allEvents)
        {
            if (filteringTable.isEventVisible(event))
            {
                visibleEvents.add(event);
            }
        }

        fireTableDataChanged();
    }

    @Override
    public void update(Observable o, Object arg)
    {
        applyNewFilteringRules();
    }
}

/**
 * Event table renderer class. Its purpose is to highlight the events with
 * severity WARNING and ERROR
 */
class EventTableRenderer extends JTextArea implements TableCellRenderer
{
    private static final long serialVersionUID = 1L;

    @Override
    public void validate() {
    }

    @Override
    public void invalidate() {
    }

    @Override
    public void revalidate() {
    }

    @Override
    public void repaint() {
    }

    public void firePropertyChange() {
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    public EventTableRenderer() {
        super();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Event event = (Event) value;
        switch (event.getSeverity())
        {
        case WARNING:
            setBackground(Color.YELLOW);
            setSelectedTextColor(Color.YELLOW);
            setDisabledTextColor(Color.YELLOW);
            break;
        case ERROR:
            setBackground(Color.RED);
            break;
        default:
            setBackground(null);
            break;
        }

        String[] lines=event.getMessage().split("\n");
        if(lines.length>5) {
            StringBuilder buf=new StringBuilder();
            for(int i=0;i<5;i++) {
                buf.append(lines[i]).append("\n");
            }
            buf.append("[truncated]");
            setText(buf.toString());
        } else {
            setText(event.getMessage());
        }
        
        int height_wanted = (int) getPreferredSize().getHeight();
        if (height_wanted != table.getRowHeight(row))
            table.setRowHeight(row, height_wanted);
        if (isSelected)
        {
            setForeground(table.getSelectionForeground());
            setBackground(table.getSelectionBackground());
        }
        else
        {
            switch (event.getSeverity())
            {
            case WARNING:
                setBackground(Color.YELLOW);
                break;
            case ERROR:
                setBackground(Color.RED);
                break;
            default:
                setForeground(table.getForeground());
                setBackground(table.getBackground());
                break;
            }
        }
        return this;
    }
}

