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
import java.util.ArrayList;
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
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import org.yamcs.api.YamcsConnectDialog.YamcsConnectDialogResult;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.YamcsConnector;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;

import com.csvreader.CsvWriter;

@SuppressWarnings("serial")
public class EventViewer extends JFrame implements ActionListener, ItemListener, MenuListener, ConnectionListener {

    static Logger log = LoggerFactory.getLogger(EventViewer.class);

    // colors taken from USS configuration
    final Color iconColorGreen = new Color(0x86B78A);
    final Color iconColorRed = new Color(0xB88687);
    Color iconColorGrey; // obtained during gui build

    EventTableModel tableModel;
    TableRowSorter<EventTableModel> tableSorter;
    public JTextArea logTextArea;
    JMenuItem miAutoScroll;
    JMenuItem miShowErrors;
    JMenuItem miRetrievePast;
    EventTable eventTable;
    JScrollPane eventPane;

    Map<String, JLabel> linkLabel;

    Map<String, Icon> linkOKIcon;
    Map<String, Icon> linkNOKIcon;

    List<JCheckBox> columnCheckbox;

    JFileChooser filechooser;
    PreferencesDialog preferencesDialog;
    EventReceiver eventReceiver;
    YamcsConnector yconnector;

    String currentUrl;
    String currentChannel;
    boolean connected = false;
    Thread connectingThread;
    private String soundFile;
    List<Map<String, String>> extraColumns;
    List<Map<String, String>> linkStatus;

    private Clip alertClip;
    private JPopupMenu popupMenu;

    private FilteringRulesTable rules;

    private JMenu viewMenu = null;

    /** Mapping of filtering rules into menu */
    private HashMap<JCheckBoxMenuItem, Integer> viewMenuFilterChkBoxes;

    /**
     * Read properties from configuration file
     */
    private void readConfiguration() throws ConfigurationException {
        linkStatus = new ArrayList<>();
        if (YConfiguration.isDefined("event-viewer")) {
            YConfiguration cfg = YConfiguration.getConfiguration("event-viewer");
            if (cfg.containsKey("soundfile")) {
                soundFile = cfg.getString("soundfile");
            }
            if (cfg.containsKey("extraColumns")) {
                extraColumns = cfg.getList("extraColumns");
            }
            if (cfg.containsKey("linkstatus")) {
                linkStatus = cfg.getList("linkstatus");
            }
        }
    }

    /**
     * Access to table with filtering rules
     * 
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

        this.yconnector = yc;
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

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menu);

        // Ctrl on win/linux, Command on mac
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

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
        panel.add(Box.createHorizontalGlue());

        linkOKIcon = new HashMap<>();
        linkNOKIcon = new HashMap<>();
        linkLabel = new HashMap<>();

        for (Map<String, String> link : linkStatus) {
            linkOKIcon.put(link.get("name"), getIcon(link.get("okicon")));
            linkNOKIcon.put(link.get("name"), getIcon(link.get("nokicon")));

            JLabel label = new JLabel(linkNOKIcon.get(link.get("name")));
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            linkLabel.put(link.get("name"), label);
            panel.add(label);
        }

        // event table

        tableModel = new EventTableModel(getFilteringRulesTable(), extraColumns);
        eventTable = new EventTable(tableModel);

        eventTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        eventTable.setPreferredScrollableViewportSize(new Dimension(920, 400));

        tableSorter = new TableRowSorter<>(tableModel);
        eventTable.setRowSorter(tableSorter);

        TableColumnModel tcm = eventTable.getColumnModel();
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.SOURCE_COL)).setMaxWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.GENERATION_TIME_COL)).setMaxWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.RECEPTION_TIME_COL)).setMaxWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.EVENT_TYPE_COL)).setMaxWidth(150);

        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.SOURCE_COL)).setPreferredWidth(100);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.GENERATION_TIME_COL)).setPreferredWidth(200);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.RECEPTION_TIME_COL)).setPreferredWidth(100);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.EVENT_TYPE_COL)).setPreferredWidth(100);
        tcm.getColumn(eventTable.convertColumnIndexToView(EventTableModel.EVENT_TEXT_COL)).setPreferredWidth(400);

        eventPane = new JScrollPane(eventTable, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel filterablePane = new JPanel(new BorderLayout());
        filterablePane.add(eventPane, BorderLayout.CENTER);

        JPanel filterPane = new JPanel(new BorderLayout());
        JTextField filterField = new JTextField();
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateRowFilter(filterField.getText());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateRowFilter(filterField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateRowFilter(filterField.getText());
            }
        });
        filterPane.add(filterField, BorderLayout.CENTER);
        filterPane.add(new JLabel("Filter:"), BorderLayout.WEST);

        filterablePane.add(filterPane, BorderLayout.NORTH);

        logTextArea = new JTextArea(5, 20);
        logTextArea.setEditable(false);
        JScrollPane logPane = new JScrollPane(logTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filterablePane, logPane);
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        getContentPane().add(split, BorderLayout.CENTER);

        popupMenu = new JPopupMenu();
        JMenuItem item = new JMenuItem("New filtering rule");
        item.setActionCommand("new_rule_popup");
        item.addActionListener(this);
        popupMenu.add(item);
        item = new JMenuItem("Details...");
        item.setActionCommand("show_event_details");
        item.addActionListener(this);
        popupMenu.add(item);
        popupMenu.setBorder(new BevelBorder(BevelBorder.RAISED));
        eventTable.addMouseListener(new MousePopupListener());

        updateStatus();
        pack();
        setLocation(30, 30);
        setVisible(true);

        eventReceiver.setEventViewer(this);
        yconnector.addConnectionListener(this);
    }

    private void updateRowFilter(String filterText) {
        String lcFilterText = filterText.toLowerCase();
        tableSorter.setRowFilter(new RowFilter<EventTableModel, Object>() {
            @Override
            public boolean include(Entry<? extends EventTableModel, ? extends Object> entry) {
                for (int i = entry.getValueCount() - 1; i >= 0; i--) {
                    if (entry.getStringValue(i).toLowerCase().contains(lcFilterText)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void populateViewMenu() {
        if (viewMenuFilterChkBoxes == null) {
            viewMenuFilterChkBoxes = new HashMap<>(25);
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

    private void showEventInDetailDialog(Event event) {
        EventDialog detailDialog = new EventDialog(this);
        detailDialog.setEvent(event);
        detailDialog.setVisible(true);
    }

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
                showEventInDetailDialog(((EventTableModel) target.getModel()).getEvent(
                        tableSorter.convertRowIndexToModel(row)));
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
                if (!eventTable.getSelectionModel().isSelectedIndex(row)) {
                    eventTable.getSelectionModel().setSelectionInterval(row, row);
                } else {
                    popupMenu.show(eventTable, e.getX(), e.getY());
                }
            }
        }
    }

    public ImageIcon getIcon(String imagename) {
        return new ImageIcon(getClass().getResource("/org/yamcs/images/" + imagename));
    }

    public void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            StringBuffer title = new StringBuffer("Event Viewer");

            if (yconnector.isConnected()) {
                if (miRetrievePast != null) {
                    miRetrievePast.setEnabled(true);
                }
                title.append(" (connected)");
            } else if (yconnector.isConnecting()) {
                if (miRetrievePast != null) {
                    miRetrievePast.setEnabled(false);
                }
                title.append(" (connecting)");
                linkLabel.values().forEach(l -> l.setBackground(iconColorGrey));
            } else {
                if (miRetrievePast != null) {
                    miRetrievePast.setEnabled(false);
                }
                title.append(" (not connected)");
                linkLabel.values().forEach(l -> l.setBackground(iconColorGrey));
            }
            setTitle(title.toString());
        });
    }

    public void updateLinkLabel(String name, String status) {
        JLabel label = linkLabel.get(name);
        if (status.equals("OK")) {
            label.setBackground(iconColorGreen);
            label.setIcon(linkOKIcon.get(name));
        } else if (status.equals("DISABLED")) {
            label.setBackground(iconColorRed);
            label.setIcon(linkNOKIcon.get(name));
        } else {
            label.setBackground(iconColorGrey);
            label.setIcon(linkOKIcon.get(name));
        }
    }

    @Override
    public void connected(String url) {
        log("Connected to " + url);
        updateStatus();
    }

    @Override
    public void connecting(String url) {
        log("Connecting to " + url);
        updateStatus();
    }

    @Override
    public void disconnected() {
        log("Disconnected");
        updateStatus();
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        log("Connection to " + url + " failed: " + exception);
        updateStatus();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("connect")) {
            YamcsConnectDialogResult r = YamcsConnectDialog.showDialog(this, true);
            if (r.isOk()) {
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
            if (getColumnsCheckBox() == 0) {
                saveTableAs();
            }
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
            showEventInDetailDialog(((EventTableModel) eventTable.getModel()).getEvent(
                    tableSorter.convertRowIndexToModel(eventTable.getSelectedRow())));
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
    public void log(String s) {
        SwingUtilities.invokeLater(() -> logTextArea
                .append(TimeEncoding.toCombinedFormat(TimeEncoding.currentInstant()) + " " + s + "\n"));
    }

    void clearTable() {
        tableModel.clear();
        eventTable.repaint();
    }

    void showMessage(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Event Viewer", JOptionPane.ERROR_MESSAGE);
    }

    class ExtendedFileFilter extends FileFilter {
        public String ext;
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

    private int getColumnsCheckBox() {

        String[] colNames = new String[eventTable.getColumnCount()];

        int i = 0;
        for (int col = 0; col < eventTable.getColumnCount(); col++) {
            colNames[i] = getColumnName(col);
            i++;
        }
        Object[] obj = new Object[eventTable.getColumnCount() + 1];
        obj[0] = "Select columns to be saved:";
        columnCheckbox = new ArrayList<>();
        i = 1;
        for (String mc : colNames) {
            JCheckBox box = new JCheckBox(mc);
            box.setSelected(true);
            columnCheckbox.add(box);
            obj[i] = box;
            i++;
        }
        return JOptionPane.showConfirmDialog(this, obj, "Save", JOptionPane.OK_CANCEL_OPTION);

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
                    File file = getSelectedFile();
                    if (file.exists()) {
                        int response = JOptionPane.showConfirmDialog(filechooser,
                                "The file " + file + " already exists. Do you want to replace the existing file?",
                                "Overwrite file", JOptionPane.YES_NO_OPTION);
                        if (response != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                    super.approveSelection();
                }
            };
            filechooser.setMultiSelectionEnabled(false);
            FileFilter csvFilter = new ExtendedFileFilter("csv", "CSV File");
            filechooser.addChoosableFileFilter(csvFilter);
            FileFilter txtFilter = new ExtendedFileFilter("txt", "Text File");
            filechooser.addChoosableFileFilter(txtFilter);
            filechooser.setFileFilter(csvFilter); // By default, choose CSV
        }
        int ret = filechooser.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = filechooser.getSelectedFile();
            CsvWriter writer = null;
            try {
                writer = new CsvWriter(new FileOutputStream(file), '\t', Charset.forName("UTF-8"));

                List<Integer> selectedColumns = new ArrayList<>();
                for (int i = 0; i < columnCheckbox.size(); i++) {
                    if (columnCheckbox.get(i).isSelected()) {
                        selectedColumns.add(i);
                    }
                }

                String[] colNames = new String[selectedColumns.size()];

                int i = 0;
                for (int col : selectedColumns) {
                    colNames[i] = getColumnName(col);
                    i++;
                }

                writer.writeRecord(colNames);
                writer.setForceQualifier(true);

                for (int row = 0; row < eventTable.getRowCount(); row++) {
                    String[] rec = new String[selectedColumns.size()];
                    i = 0;
                    for (int col : selectedColumns) {
                        rec[i] = getColumnValue(col, row);
                        i++;

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

    private String getColumnName(int col) {
        switch (col) {
        case 0:
            return "Source";
        case 1:
            return "Generation Time";
        case 2:
            return "Reception Time";
        case 3:
            return "Event Type";
        case 4:
            return "Event Text";
        default:
            return tableModel.getColumnName(col);
        }
    }

    private String getColumnValue(int col, int row) {
        row = tableSorter.convertRowIndexToModel(row);
        switch (col) {
        case 4:
            return ((Event) tableModel.getValueAt(row, col)).getMessage();
        default:
            return (String) tableModel.getValueAt(row, col);
        }

    }

    public void addEvents(final List<Event> events) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            tableModel.addEvents(events);
            eventTable.revalidate();
            eventPane.validate();
            if (miAutoScroll.isSelected()) {
                updateVerticalScrollPosition();
            }
        });
    }

    /**
     * Add event. This method is used for incoming events.
     */
    public void addEvent(Event event) {
        SwingUtilities.invokeLater(() -> {
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
        });
    }

    /**
     * Adjusts vertical scroll position (horizontal position remains unchanged)
     */
    private void updateVerticalScrollPosition() {
        if (eventTable.getRowCount() <= 0) {
            return;
        }

        int row = eventTable.convertRowIndexToView(eventTable.getRowCount() - 1);
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
            int actualHeight = ((EventTableRenderer) renderer).updateCalculatedHeight(eventTable, value, row);
            y += (actualHeight - rect.height);
        }

        rect.setLocation(new Point(x, y));
        eventTable.scrollRectToVisible(rect);
    }

    /**
     * Play the sound
     */
    private void playAlertSound() {
        new Thread(() -> playSoundFile()).start();
    }

    /**
     * Access to sound clip.
     */
    private Clip getAlertClip() {
        if (alertClip == null) {
            String defFileName = "/org/yamcs/sounds/alert.wav";
            try {
                AudioInputStream inputStream = null;
                if (soundFile == null) {
                    inputStream = AudioSystem
                            .getAudioInputStream(new BufferedInputStream(getClass().getResourceAsStream(defFileName)));
                } else {
                    inputStream = AudioSystem.getAudioInputStream(new File(soundFile));
                }
                AudioFormat format = inputStream.getFormat();
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                alertClip = (Clip) AudioSystem.getLine(info);
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
     * @param fileName
     *            File with the sound resource
     * @todo This method should be accessed only by one thread.....
     */
    private void playSoundFile() {
        Clip clip = null;

        try {
            clip = getAlertClip();
            if (clip == null) {
                return;
            }

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

    public static void main(String[] args) throws IOException, ConfigurationException, URISyntaxException,
            ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (args.length > 1) {
            printUsageAndExit();
        }
        YConfiguration.setup();
        YamcsConnectionProperties ycd = null;
        if (args.length == 1) {
            if (args[0].startsWith("http")) {
                ycd = YamcsConnectionProperties.parse(args[0]);
            } else {
                printUsageAndExit();
            }
        }
        YamcsConnector yconnector = new YamcsConnector("EventViewer");
        YamcsEventReceiver eventReceiver = new YamcsEventReceiver(yconnector);

        new EventViewer(yconnector, eventReceiver);
        if (ycd != null) {
            yconnector.connect(ycd);
        }

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

    public List<String> getParameterLinkStatus() {
        List<String> parameterLinks = new ArrayList<>();
        for (Map<String, String> link : linkStatus) {
            parameterLinks.add(link.get("name"));
        }
        return parameterLinks;

    }
}
