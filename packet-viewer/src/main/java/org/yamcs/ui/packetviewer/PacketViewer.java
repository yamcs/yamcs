package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jdesktop.swingx.prompt.PromptSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.client.ClientException;
import org.yamcs.client.ConnectionListener;
import org.yamcs.client.WebSocketClientCallback;
import org.yamcs.client.WebSocketRequest;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.YamcsConnectionProperties;
import org.yamcs.parameter.ContainerParameterValue;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.tctm.CcsdsPacketInputStream;
import org.yamcs.tctm.IssPacketPreprocessor;
import org.yamcs.tctm.PacketInputStream;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.ui.PrefsObject;
import org.yamcs.ui.packetviewer.filter.PacketFilter;
import org.yamcs.ui.packetviewer.filter.ParseException;
import org.yamcs.ui.packetviewer.filter.TokenMgrError;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

import com.google.common.io.CountingInputStream;

import io.netty.handler.codec.http.HttpMethod;

public class PacketViewer extends JFrame implements ActionListener,
        TreeSelectionListener, ParameterListener, ConnectionListener, WebSocketClientCallback {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(PacketViewer.class);
    public static final Color ERROR_FAINT_BG = new Color(255, 221, 221);
    public static final Color ERROR_FAINT_FG = new Color(255, 0, 0);
    public static final Border ERROR_BORDER = BorderFactory.createLineBorder(new Color(205, 87, 40));
    private static PacketViewer theApp;
    private static int maxLines = -1;
    XtceDb xtcedb;

    private File lastFile;
    private JSplitPane hexSplit;
    private JTextPane hexText;
    private StyledDocument hexDoc;
    private Style fixedStyle;
    private Style highlightedStyle;
    private Style offsetStyle;
    private JMenu fileMenu;
    private List<JMenuItem> miRecentFiles;
    JMenuItem miAutoScroll;
    JMenuItem miAutoSelect;
    JComboBox<String> filterField;
    private JTextArea logText;
    private JScrollPane logScrollpane;
    private PacketsTable packetsTable;
    private ParametersTable parametersTable;
    private JTree structureTree;
    private DefaultMutableTreeNode structureRoot;
    private DefaultTreeModel structureModel;
    private JSplitPane mainsplit;
    private FindParameterBar findBar;
    private ListPacket currentPacket;
    private OpenFileDialog openFileDialog;
    private YamcsClient client;
    private ConnectDialog connectDialog;
    Preferences uiPrefs;

    // used for decoding full packets
    private XtceTmProcessor tmProcessor;

    String streamName;
    private String defaultNamespace;
    private PacketPreprocessor packetPreprocessor;

    private Object packetInputStreamArgs;
    private String packetInputStreamClassName;

    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";

    @SuppressWarnings("serial")
    public PacketViewer(int maxLines) throws ConfigurationException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1440, 1080));

        uiPrefs = Preferences.userNodeForPackage(PacketViewer.class);

        YConfiguration config = null;
        if (YConfiguration.isDefined("packet-viewer")) {
            config = YConfiguration.getConfiguration("packet-viewer");
        }
        if (config != null) {
            defaultNamespace = config.getString("defaultNamespace", null);
            readConfig(null, config);
        } else {
            packetPreprocessor = new IssPacketPreprocessor(null);
        }

        packetPreprocessor.checkForSequenceDiscontinuity(false);

        // table to the left which shows one row per packet
        packetsTable = new PacketsTable(this);
        packetsTable.setMaxLines(maxLines);
        JScrollPane packetScrollpane = new JScrollPane(packetsTable);

        // table to the right which shows one row per parameter in the selected packet

        parametersTable = new ParametersTable(this);
        JScrollPane tableScrollpane = new JScrollPane(parametersTable);
        tableScrollpane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (e.getComponent().getWidth() < parametersTable.getPreferredSize().getWidth()) {
                    parametersTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                } else {
                    parametersTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                }
            }
        });

        // tree to the right which shows the container structure of the selected packet

        structureRoot = new DefaultMutableTreeNode();
        structureModel = new DefaultTreeModel(structureRoot);
        structureTree = new JTree(structureModel);
        structureTree.setEditable(false);
        structureTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        structureTree.addTreeSelectionListener(this);
        JScrollPane treeScrollpane = new JScrollPane(structureTree);

        Insets oldInsets = UIManager.getInsets("TabbedPane.contentBorderInsets");
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

        JTabbedPane tabpane = new JTabbedPane();
        UIManager.put("TabbedPane.contentBorderInsets", oldInsets);
        tabpane.add("Parameters", tableScrollpane);
        tabpane.add("Structure", treeScrollpane);

        findBar = new FindParameterBar(parametersTable);

        JPanel parameterPanel = new JPanel(new BorderLayout());
        parameterPanel.add(tabpane, BorderLayout.CENTER);
        parameterPanel.add(findBar, BorderLayout.SOUTH);

        // hexdump panel

        hexText = new JTextPane() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return false; // disable line wrap
            }
        };
        hexDoc = hexText.getStyledDocument();
        final Style defStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        fixedStyle = hexDoc.addStyle("fixed", defStyle);
        StyleConstants.setFontFamily(fixedStyle, Font.MONOSPACED);
        highlightedStyle = hexDoc.addStyle("highlighted", fixedStyle);
        StyleConstants.setBackground(highlightedStyle, parametersTable.getSelectionBackground());
        StyleConstants.setForeground(highlightedStyle, parametersTable.getSelectionForeground());
        offsetStyle = hexDoc.addStyle("offset", fixedStyle);
        StyleConstants.setForeground(offsetStyle, Color.GRAY);
        hexText.setEditable(false);

        JScrollPane hexScrollpane = new JScrollPane(hexText);
        hexScrollpane.getViewport().setBackground(hexText.getBackground());
        hexSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, parameterPanel, hexScrollpane);
        removeBorders(hexSplit);
        hexSplit.setResizeWeight(0.7);

        mainsplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, packetScrollpane, hexSplit);
        removeBorders(mainsplit);
        mainsplit.setResizeWeight(0.0);

        // log text

        logText = new JTextArea(3, 20);
        logText.setEditable(false);
        logScrollpane = new JScrollPane(logText);
        JSplitPane logsplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainsplit, logScrollpane);
        removeBorders(logsplit);
        logsplit.setResizeWeight(1.0);
        logsplit.setContinuousLayout(true);

        installMenubar();

        JPanel filterBar = new JPanel();
        filterBar.setLayout(new BorderLayout());

        filterField = new JComboBox<>();
        filterField.setEditable(true);
        filterBar.add(filterField, BorderLayout.CENTER);

        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.X_AXIS));
        eastPanel.setAlignmentY(CENTER_ALIGNMENT);
        JButton clearButton = new JButton();
        clearButton.setAction(new AbstractAction("Clear") {
            @Override
            public void actionPerformed(ActionEvent e) {
                filterField.setSelectedIndex(-1);
            }
        });
        eastPanel.add(clearButton);
        filterBar.add(eastPanel, BorderLayout.EAST);

        JTextField filterEditor = (JTextField) filterField.getEditor().getEditorComponent();

        filterEditor.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                String selectedItem = filterEditor.getText();

                if (selectedItem != null && !selectedItem.isEmpty() && xtcedb != null) {
                    clearButton.setEnabled(true);
                    try {
                        new PacketFilter(selectedItem, xtcedb);
                        JTextField dummy = new JTextField();
                        filterEditor.setBackground(dummy.getBackground());
                        filterEditor.setForeground(dummy.getForeground());
                    } catch (ParseException | TokenMgrError e1) {
                        filterEditor.setBackground(ERROR_FAINT_BG);
                        filterEditor.setForeground(ERROR_FAINT_FG);
                    }
                } else {
                    clearButton.setEnabled(false);
                    JTextField dummy = new JTextField();
                    filterEditor.setBackground(dummy.getBackground());
                    filterEditor.setForeground(dummy.getForeground());
                }

            }
        });

        for (String item : getFilterHistory()) {
            filterField.addItem(item);
        }
        filterField.setSelectedIndex(-1);

        filterField.addActionListener(e -> {
            try {
                String selectedItem = (String) filterField.getSelectedItem();
                if (selectedItem != null && !selectedItem.trim().isEmpty()) {
                    PacketFilter filter = new PacketFilter(selectedItem, xtcedb);
                    packetsTable.configureRowFilter(filter);
                    updateFilterHistory(selectedItem);
                    filterField.removeAllItems();
                    for (String item : getFilterHistory()) {
                        filterField.addItem(item);
                    }
                } else {
                    packetsTable.configureRowFilter(null);
                }
            } catch (ParseException | TokenMgrError e1) {
                packetsTable.configureRowFilter(null);
            }
        });
        PromptSupport.setPrompt("Display Filter (e.g. my-parameter == 123)", filterEditor);

        getContentPane().add(filterBar, BorderLayout.NORTH);
        getContentPane().add(logsplit, BorderLayout.CENTER);

        clearWindow();
        updateTitle();
        pack();
        setLocationRelativeTo(null); // Center on primary monitor

        packetsTable.requestFocusInWindow(); // Take focus away from search box
        setVisible(true);
    }

    private void installMenubar() {
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(fileMenu);

        // Ctrl on win/linux, Command on mac
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        JMenuItem menuitem = new JMenuItem("Open...", KeyEvent.VK_O);
        menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey));
        menuitem.setActionCommand("open file");
        menuitem.addActionListener(this);
        fileMenu.add(menuitem);

        menuitem = new JMenuItem("Connect to Yamcs...");
        menuitem.setMnemonic(KeyEvent.VK_C);
        menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuKey));
        menuitem.setActionCommand("connect-yamcs");
        menuitem.addActionListener(this);
        fileMenu.add(menuitem);

        fileMenu.addSeparator();

        miRecentFiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            menuitem = new JMenuItem();
            menuitem.setMnemonic(KeyEvent.VK_1 + i);
            menuitem.setActionCommand("recent-file-" + i);
            menuitem.addActionListener(this);
            fileMenu.add(menuitem);
            miRecentFiles.add(menuitem);
        }

        updateMenuWithRecentFiles();
        if (!getRecentFiles().isEmpty()) {
            fileMenu.addSeparator();
        }

        /*
         * menuitem = new JMenuItem("Preferences", KeyEvent.VK_COMMA);
         * menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuKey));
         * menu.add(menuitem);
         * menu.addSeparator();
         */

        menuitem = new JMenuItem("Quit", KeyEvent.VK_Q);
        menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuKey));
        menuitem.setActionCommand("quit");
        menuitem.addActionListener(this);
        fileMenu.add(menuitem);

        JMenu menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);
        menuBar.add(menu);

        Action openFindBarAction = findBar.getActionMap().get(FindParameterBar.OPEN_ACTION);
        menu.add(new JMenuItem(openFindBarAction));

        menu.addSeparator();

        Action toggleMarkAction = packetsTable.getActionMap().get(PacketsTable.TOGGLE_MARK_ACTION_KEY);
        menu.add(new JMenuItem(toggleMarkAction));

        menu = new JMenu("Navigate");
        menu.setMnemonic(KeyEvent.VK_N);
        menuBar.add(menu);

        Action goToPacketAction = packetsTable.getActionMap().get(PacketsTable.GO_TO_PACKET_ACTION_KEY);
        menu.add(new JMenuItem(goToPacketAction));

        menu.addSeparator();

        Action backAction = packetsTable.getActionMap().get(PacketsTable.BACK_ACTION_KEY);
        menu.add(new JMenuItem(backAction));

        Action forwardAction = packetsTable.getActionMap().get(PacketsTable.FORWARD_ACTION_KEY);
        menu.add(new JMenuItem(forwardAction));

        menu.addSeparator();

        Action upAction = packetsTable.getActionMap().get(PacketsTable.UP_ACTION_KEY);
        menu.add(new JMenuItem(upAction));

        Action downAction = packetsTable.getActionMap().get(PacketsTable.DOWN_ACTION_KEY);
        menu.add(new JMenuItem(downAction));

        menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);
        menuBar.add(menu);

        miAutoScroll = new JCheckBoxMenuItem("Auto-Scroll To Last Packet");
        miAutoScroll.setSelected(true);
        menu.add(miAutoScroll);

        miAutoSelect = new JCheckBoxMenuItem("Auto-Select Last Packet");
        miAutoSelect.setSelected(false);
        menu.add(miAutoSelect);

        menu.addSeparator();

        menuitem = new JMenuItem("Clear", KeyEvent.VK_C);
        menuitem.setActionCommand("clear");
        menuitem.addActionListener(this);
        menu.add(menuitem);
    }

    void updateTitle() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder title = new StringBuilder("Yamcs Packet Viewer");
            if (client != null && client.isConnected()) {
                title.append(" [").append(client.getUrl()).append("]");
            } else if (lastFile != null) {
                title.append(" - ");
                title.append(lastFile.getName());
            } else {
                title.append(" (no file loaded)");
            }
            setTitle(title.toString());
        });
    }

    void updateMenuWithRecentFiles() {
        List<String[]> recentFiles = getRecentFiles();
        int i;
        for (i = 0; i < recentFiles.size() && i < miRecentFiles.size(); i++) {
            String fileRef = recentFiles.get(i)[0];
            int maxChars = 30;
            if (fileRef.length() > maxChars) {
                // Search first slash from right to left
                int slashIndex = fileRef.lastIndexOf(File.separatorChar);
                if (fileRef.length() - slashIndex > maxChars - 3) {
                    // Chop off the end of the string of the last path segment
                    fileRef = "..." + fileRef.substring(slashIndex, slashIndex + maxChars - 2 * 3) + "...";
                } else {
                    // Output the complete filename, and fill up with initial path segments
                    fileRef = fileRef.substring(0, maxChars - 3 - (fileRef.length() - slashIndex))
                            + "..." + fileRef.substring(slashIndex);
                }
            }

            JMenuItem mi = miRecentFiles.get(i);
            mi.setVisible(true);
            mi.setText((i + 1) + " " + fileRef);
            mi.setToolTipText(recentFiles.get(i)[0]);
        }

        for (; i < miRecentFiles.size(); i++) {
            miRecentFiles.get(i).setVisible(false);
        }
    }

    static void debugLogComponent(String name, JComponent c) {
        Insets in = c.getInsets();
        System.out.println("component " + name + ": "
                + "min(" + c.getMinimumSize().width + "," + c.getMinimumSize().height + ") "
                + "pref(" + c.getPreferredSize().width + "," + c.getPreferredSize().height + ") "
                + "max(" + c.getMaximumSize().width + "," + c.getMaximumSize().height + ") "
                + "size(" + c.getSize().width + "," + c.getSize().height + ") "
                + "insets(" + in.top + "," + in.left + "," + in.bottom + "," + in.right + ")");
    }

    @Override
    public void log(final String s) {
        SwingUtilities.invokeLater(() -> {
            if (logText != null) {
                logText.append(s + "\n");
                logScrollpane.getVerticalScrollBar().setValue(logScrollpane.getVerticalScrollBar().getMaximum());
            } else {
                System.err.println(s);
            }
        });
    }

    void showMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.PLAIN_MESSAGE);
        });
    }

    void showError(String msg) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();
        if (cmd.equals("quit")) {
            processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        } else if (cmd.equals("clear")) {
            clearWindow();
        } else if (cmd.equals("open file")) {
            if (openFileDialog == null) {
                try {
                    openFileDialog = new OpenFileDialog();
                } catch (ConfigurationException e) {
                    showError("Cannot load local mdb config: " + e.getMessage());
                    return;
                }
            }
            int returnVal = openFileDialog.showDialog(this);
            if (returnVal == OpenFileDialog.APPROVE_OPTION) {
                openFile(openFileDialog.getSelectedFile(), openFileDialog.getSelectedDbConfig());
            }
        } else if (cmd.equals("connect-yamcs")) {
            if (connectDialog == null) {
                connectDialog = new ConnectDialog(this, true, true, true);
            }
            int ret = connectDialog.showDialog();
            if (ret == ConnectDialog.APPROVE_OPTION) {
                streamName = connectDialog.getStreamName();
                connectYamcs(connectDialog.getConnectData());
            }
        } else if (cmd.startsWith("recent-file-")) {
            JMenuItem mi = (JMenuItem) ae.getSource();
            for (String[] recentFile : getRecentFiles()) {
                if (recentFile[0].equals(mi.getToolTipText())) {
                    openFile(new File(recentFile[0]), recentFile[1]);
                }
            }
        }
    }

    private void openFile(File file, String xtceDb) {
        if (!file.exists() || !file.isFile()) {
            JOptionPane.showMessageDialog(null, "File not found: " + file, "File not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        disconnect();
        lastFile = file;
        if (loadLocalXtcedb(xtceDb)) {
            loadFile();
        }
        updateRecentFiles(lastFile, xtceDb);
    }

    private boolean loadLocalXtcedb(String configName) {
        if (tmProcessor != null) {
            tmProcessor.stopAsync();
        }
        log("Loading local XTCE db " + configName);
        try {
            xtcedb = XtceDbFactory.createInstanceByConfig(configName);
        } catch (ConfigurationException | DatabaseLoadException e) {
            log.error(e.toString(), e);
            showError(e.getMessage());
            return false;
        }

        tmProcessor = new XtceTmProcessor(xtcedb, getProcessorConfig());

        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.startAsync();
        log(String.format("Loaded definition of %d sequence container%s and %d parameter%s",
                xtcedb.getSequenceContainers().size(), (xtcedb.getSequenceContainers().size() != 1 ? "s" : ""),
                xtcedb.getParameterNames().size(), (xtcedb.getParameterNames().size() != 1 ? "s" : "")));

        packetsTable.setupParameterColumns();
        return true;
    }

    private boolean loadRemoteXtcedb(String configName) {
        if (tmProcessor != null) {
            tmProcessor.stopAsync();
        }
        String effectiveInstance = client.getConnectionInfo().getInstance().getName();
        log("Loading remote XTCE db for yamcs instance " + effectiveInstance);
        try {
            byte[] serializedMdb = client.getRestClient()
                    .doRequest("/mdb/" + effectiveInstance + ":exportJava", HttpMethod.GET).get();
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedMdb));
            Object o = ois.readObject();
            xtcedb = (XtceDb) o;
            ois.close();
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
            return false;
        }

        tmProcessor = new XtceTmProcessor(xtcedb, getProcessorConfig());
        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.startAsync();
        packetsTable.setupParameterColumns();

        log("Loaded " + xtcedb.getSequenceContainers().size() + " sequence containers and "
                + xtcedb.getParameterNames().size() + " parameters");

        return true;
    }

    private ProcessorConfig getProcessorConfig() {
        return new ProcessorConfig();
    }

    void loadFile() {
        new SwingWorker<Void, TmPacket>() {
            ProgressMonitor progress;
            int packetCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                try (CountingInputStream reader = new CountingInputStream(new FileInputStream(lastFile))) {
                    PacketInputStream packetInputStream = getPacketInputStream(reader);
                    TmPacket packet;

                    clearWindow();
                    int progressMax = (maxLines == -1) ? (int) (lastFile.length() >> 10) : maxLines;
                    progress = new ProgressMonitor(theApp, String.format("Loading %s", lastFile.getName()), null, 0,
                            progressMax);

                    while (!progress.isCanceled()) {
                        byte[] p = packetInputStream.readPacket();
                        if (p == null) {
                            break;
                        }
                        packet = packetPreprocessor.process(new TmPacket(TimeEncoding.getWallclockTime(), p));

                        if (packet != null) {
                            publish(packet);
                            packetCount++;
                            if (packetCount == maxLines) {
                                break;
                            }
                        } else {
                            log("preprocessor returned null packet");
                        }
                        progress.setProgress((maxLines == -1) ? (int) (reader.getCount() >> 10) : packetCount);
                    }
                    reader.close();
                } catch (EOFException x) {
                    final String msg = String.format("Encountered end of file while loading %s", lastFile.getName());
                    log(msg);
                } catch (Exception x) {
                    x.printStackTrace();
                    final String msg = String.format("Error while loading %s: %s", lastFile.getName(), x.getMessage());
                    log(msg);
                    showError(msg);
                    clearWindow();
                    lastFile = null;
                }
                return null;
            }

            @Override
            protected void process(final List<TmPacket> chunks) {
                for (TmPacket packet : chunks) {
                    packetsTable.packetReceived(packet);
                }
            }

            @Override
            protected void done() {
                if (progress != null) {
                    if (lastFile != null) {
                        if (progress.isCanceled()) {
                            clearWindow();
                            log(String.format("Cancelled loading %s", lastFile.getName()));
                        } else {
                            log(String.format("Loaded %d packet%s from \"%s\"",
                                    packetCount,
                                    packetCount != 1 ? "s" : "", lastFile.getPath()));
                        }
                    }
                    progress.close();
                }
                updateTitle();
            }
        }.execute();
    }

    void clearWindow() {
        SwingUtilities.invokeLater(() -> {
            packetsTable.clear();
            parametersTable.clear();
            hexText.setText(null);
            packetsTable.revalidate();
            parametersTable.revalidate();
            structureRoot.removeAllChildren();
            structureTree.setRootVisible(false);
        });
    }

    void highlightBitRanges(Range[] highlightBits) {
        final int linesize = 5 + 5 * 8 + 16 + 1;
        int n, tmp, textoffset, binHighStart, binHighStop, ascHighStart, ascHighStop;

        // reset styles throughout the document
        hexDoc.setCharacterAttributes(0, hexDoc.getLength(), fixedStyle, true);
        for (int i = 0; i < hexDoc.getLength(); i += linesize) {
            hexDoc.setCharacterAttributes(i, 4, offsetStyle, true);
        }

        // apply style for highlighted parts
        for (Range bitRange : highlightBits) {
            if (bitRange == null) {
                continue;
            }
            final int highlightStartNibble = bitRange.offset / 4;
            final int highlightStopNibble = (bitRange.offset + bitRange.size + 3) / 4;
            for (n = highlightStartNibble / 32 * 32; n < highlightStopNibble; n += 32) {

                binHighStart = 5;
                ascHighStart = 5 + 5 * 8;
                tmp = highlightStartNibble - n;
                if (tmp > 0) {
                    binHighStart += tmp + (tmp / 4);
                    ascHighStart += tmp / 2;
                }

                binHighStop = 5 + 5 * 8 - 1;
                ascHighStop = 5 + 5 * 8 + 16;
                tmp = n + 32 - highlightStopNibble;
                if (tmp > 0) {
                    binHighStop -= tmp + (tmp / 4);
                    ascHighStop -= tmp / 2;
                }

                textoffset = linesize * (n / 32);
                // System.out.println(String.format("setCharacterAttributes %d/%d %d %d %d/%d %d/%d",
                // highlightStartNibble, highlightStopNibble, n, textoffset, binHighStart, binHighStop, ascHighStart,
                // ascHighStop));
                hexDoc.setCharacterAttributes(textoffset + binHighStart, binHighStop - binHighStart, highlightedStyle,
                        true);
                hexDoc.setCharacterAttributes(textoffset + ascHighStart, ascHighStop - ascHighStart, highlightedStyle,
                        true);
            }
        }

        // put the caret into the position of the first item (caret makes itself visible by default)
        final int hexScrollPos = (highlightBits.length == 0 || highlightBits[0] == null) ? 0
                : (linesize * (highlightBits[0].offset / 128));
        hexText.setCaretPosition(hexScrollPos);
    }

    void connectYamcs(YamcsConnectionProperties ycd) {
        disconnect();
        client = YamcsClient.newBuilder(ycd.getHost(), ycd.getPort())
                .withTls(ycd.isTls())
                .withInitialInstance(ycd.getInstance(), true, false)
                .withConnectionAttempts(10)
                .withUserAgent("PacketViewer")
                .build();
        client.addConnectionListener(this);
        try {
            if (ycd.getUsername() == null) {
                client.connectAnonymously();
            } else {
                client.connect(ycd.getUsername(), ycd.getPassword());
            }
        } catch (ClientException e) {
            log.error("Error while connecting", e);
        }

        updateTitle();
    }

    void disconnect() {
        if (client != null) {
            client.close();
        }
        updateTitle();
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        TreePath[] paths = structureTree.getSelectionPaths();
        Range[] bits = null;
        if (paths == null) {
            bits = new Range[0];
        } else {
            bits = new Range[paths.length];
            for (int i = 0; i < paths.length; ++i) {
                Object last = paths[i].getLastPathComponent();
                if (last instanceof TreeEntry) {
                    TreeEntry te = (TreeEntry) last;
                    bits[i] = new Range(te.bitOffset, te.bitSize);
                } else {
                    bits[i] = null;
                }
            }
        }
        highlightBitRanges(bits);
    }

    @Override
    public void update(final Collection<ParameterValue> params) {
        SwingUtilities.invokeLater(new Runnable() {
            Hashtable<String, TreeContainer> containers = new Hashtable<>();

            DefaultMutableTreeNode getTreeNode(SequenceContainer sc) {
                if (sc.getBaseContainer() == null) {
                    return structureRoot;
                }
                TreeContainer tc = containers.get(sc.getOpsName());
                if (tc == null) {
                    tc = new TreeContainer(sc);
                    containers.put(sc.getOpsName(), tc);
                }
                getTreeNode(sc.getBaseContainer()).add(tc);
                return tc;
            }

            @Override
            public void run() {
                parametersTable.clear();
                structureRoot.removeAllChildren();

                for (ParameterValue value : params) {
                    // add new leaf to the structure tree
                    // parameters become leaves, and sequence containers become nodes recursively
                    if (value instanceof ContainerParameterValue) {
                        ContainerParameterValue cpv = (ContainerParameterValue) value;
                        getTreeNode(cpv.getSequenceEntry().getSequenceContainer())
                                .add(new TreeEntry(cpv));
                    }
                    parametersTable.parametersTableModel.addRow(value);
                }

                structureRoot.setUserObject(currentPacket);
                structureModel.nodeStructureChanged(structureRoot);
                structureTree.setRootVisible(true);

                // expand all nodes
                for (TreeContainer tc : containers.values()) {
                    structureTree.expandPath(new TreePath(tc.getPath()));
                }

                // build hexdump text
                currentPacket.hexdump(hexDoc);
                hexText.setCaretPosition(0);

                // select first row
                parametersTable.setRowSelectionInterval(0, 0);
                parametersTable.parametersTableModel.fireTableDataChanged();
            }
        });
    }

    public void setSelectedPacket(ListPacket listPacket) {
        currentPacket = listPacket;
        try {
            currentPacket.load(lastFile);
            TmPacket packet = new TmPacket(TimeEncoding.getWallclockTime(), listPacket.buf);
            tmProcessor.processPacket(packetPreprocessor.process(packet), xtcedb.getRootSequenceContainer());
        } catch (IOException x) {
            final String msg = String.format("Error while loading %s: %s", lastFile.getName(), x.getMessage());
            log(msg);
            showError(msg);
        }
    }

    @SuppressWarnings("serial")
    class TreeContainer extends DefaultMutableTreeNode {
        TreeContainer(SequenceContainer sc) {
            super(sc.getOpsName(), true);
        }
    }

    @SuppressWarnings("serial")
    class TreeEntry extends DefaultMutableTreeNode {
        int bitOffset, bitSize;

        TreeEntry(ContainerParameterValue value) {
            super(String.format("%d/%d %s", value.getAbsoluteBitOffset(), value.getBitSize(),
                    value.getParameter().getOpsName()), false);
            bitOffset = value.getAbsoluteBitOffset();
            bitSize = value.getBitSize();
        }
    }

    protected class Range {
        int offset, size;

        Range(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }
    }

    @Override
    public void connected(String url) {
        try {
            log("connected to " + url);
            if (connectDialog != null) {
                if (connectDialog.getUseServerMdb()) {
                    if (!loadRemoteXtcedb(connectDialog.getServerMdbConfig())) {
                        return;
                    }
                } else {
                    if (!loadLocalXtcedb(connectDialog.getLocalMdbConfig())) {
                        return;
                    }
                }
            } else {
                List<YamcsInstance> list = client.getRestClient().blockingGetYamcsInstances();

                String effectiveInstance = client.getConnectionInfo().getInstance().getName();
                for (YamcsInstance yi : list) {
                    if (effectiveInstance.equals(yi.getName())) {
                        String mdbConfig = yi.getMissionDatabase().getConfigName();
                        if (!loadRemoteXtcedb(mdbConfig)) {
                            return;
                        }
                    }
                }
            }
            WebSocketRequest wsr = new WebSocketRequest("packets", "subscribe " + streamName);
            client.performSubscription(wsr, this, e -> {
                showError("Error subscribing to " + streamName + ": " + e.getMessage());
            });
        } catch (Exception e) {
            log(e.toString());
            e.printStackTrace();
        }

    }

    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        if (data.hasTmPacket()) {
            TmPacketData tm = data.getTmPacket();
            TmPacket pwt = new TmPacket(TimeEncoding.fromProtobufTimestamp(tm.getReceptionTime()),
                    TimeEncoding.fromProtobufTimestamp(tm.getGenerationTime()),
                    tm.getSequenceNumber(), tm.getPacket().toByteArray());
            packetsTable.packetReceived(pwt);
        }
    }

    @Override
    public void connecting(String url) {
        log("connecting to " + url);

    }

    @Override
    public void connectionFailed(String url, ClientException exception) {
        log("connection to " + url + " failed: " + exception);
    }

    @Override
    public void disconnected() {
        log("disconnected");
    }

    /**
     * Returns the recently opened files from preferences Each entry is a String array with the filename on index 0, and
     * the last used XTCE DB for that file on index 1.
     */
    @SuppressWarnings("unchecked")
    public List<String[]> getRecentFiles() {
        List<String[]> recentFiles = null;
        Object obj = PrefsObject.getObject(uiPrefs, "RecentlyOpened");
        if (obj instanceof ArrayList) {
            recentFiles = (ArrayList<String[]>) obj;
        }
        return (recentFiles != null) ? recentFiles : new ArrayList<>();
    }

    private void updateRecentFiles(File file, String xtceDb) {
        String filename = file.getAbsolutePath();
        List<String[]> recentFiles = getRecentFiles();
        boolean exists = false;
        for (int i = 0; i < recentFiles.size(); i++) {
            String[] entry = recentFiles.get(i);
            if (entry[0].equals(filename)) {
                entry[1] = xtceDb;
                recentFiles.add(0, recentFiles.remove(i));
                exists = true;
            }
        }
        if (!exists) {
            recentFiles.add(0, new String[] { filename, xtceDb });
        }
        PrefsObject.putObject(uiPrefs, "RecentlyOpened", recentFiles);

        // Also update JMenu accordingly
        updateMenuWithRecentFiles();
    }

    @SuppressWarnings("unchecked")
    private List<String> getFilterHistory() {
        List<String> history = null;
        Object obj = PrefsObject.getObject(uiPrefs, "FilterHistory");
        if (obj instanceof ArrayList) {
            history = (ArrayList<String>) obj;
        }
        return (history != null) ? history : new ArrayList<>();
    }

    private void updateFilterHistory(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return;
        }

        List<String> history = getFilterHistory();
        boolean exists = false;
        for (int i = 0; i < history.size(); i++) {
            String entry = history.get(i);
            if (entry.equals(filter)) {
                history.add(0, history.remove(i));
                exists = true;
            }
        }
        if (!exists) {
            history.add(0, filter);
        }
        if (history.size() > 10) {
            history = new ArrayList<>(history.subList(0, 10));
        }
        PrefsObject.putObject(uiPrefs, "FilterHistory", history);
    }

    private void removeBorders(JSplitPane splitPane) {
        SplitPaneUI ui = splitPane.getUI();
        if (ui instanceof BasicSplitPaneUI) { // We don't want to mess with other L&Fs
            ((BasicSplitPaneUI) ui).getDivider().setBorder(null);
            splitPane.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private static void printUsageAndExit(boolean full) {
        System.err.println("usage: packetviewer.sh [-h] [-l n] [-x name] [-s name] [file|url]");
        if (full) {
            System.err.println();
            System.err.println("    file       The file to open at startup. Requires the use of -db");
            System.err.println("    url        Connect at startup to the given url");
            System.err.println();
            System.err.println("OPTIONS");
            System.err.println("    -h         Print a help message and exit");
            System.err.println();
            System.err.println("    -l  n      Limit the view to n packets only. If the Packet Viewer is");
            System.err.println("               connected to a live instance, only the last n packets will");
            System.err.println("               be visible. For offline file consulting, only the first n");
            System.err.println("               packets of the file will be displayed.");
            System.err.println("               Defaults to 1000 for realtime connections. There is no");
            System.err.println("               default limitation for viewing offline files.");
            System.err.println();
            System.err.println("    -x  name   Name of the applicable XTCE DB as specified in the");
            System.err.println("               mdb.yaml configuration file.");
            System.err.println();
            System.err.println(
                    "    -s  name  Name of the stream to connect to (if not specified, it connects to tm_realtime");
            System.err.println("EXAMPLES");
            System.err.println("        packetviewer.sh http://localhost:8090/yops");
            System.err.println("        packetviewer.sh -l 50 -x my-db packet-file");
        }
        System.exit(1);
    }

    private static void printArgsError(String message) {
        System.err.println(message);
        printUsageAndExit(false);
    }

    private void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public static void main(String[] args) throws ConfigurationException, URISyntaxException {
        // Scan args
        String fileOrUrl = null;
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if ("-h".equals(args[i])) {
                printUsageAndExit(true);
            } else if ("-l".equals(args[i])) {
                if (i + 1 < args.length) {
                    options.put(args[i], args[++i]);
                } else {
                    printArgsError("Number of lines not specified for -l option");
                }
            } else if ("-x".equals(args[i])) {
                if (i + 1 < args.length) {
                    options.put(args[i], args[++i]);
                } else {
                    printArgsError("Name of XTCE DB not specified for -x option");
                }
            } else if ("-s".equals(args[i])) {
                if (i + 1 < args.length) {
                    options.put(args[i], args[++i]);
                } else {
                    printArgsError("Name of stream not specified for -s option");
                }
            } else if ("--etc-dir".equals(args[i])) {
                if (i + 1 < args.length) {
                    options.put(args[i], args[++i]);
                } else {
                    printArgsError("Directory not specified for --etc-dir option");
                }
            } else if (args[i].startsWith("-")) {
                printArgsError("Unknown option: " + args[i]);
            } else { // i should now be positioned at [file|url]
                if (i == args.length - 1) {
                    fileOrUrl = args[i];
                } else {
                    printArgsError("Too many arguments. Only one file or url can be opened at a time");
                }
            }
        }

        // Do some more preparatory stuff
        if (options.containsKey("-l")) {
            try {
                maxLines = Integer.parseInt((String) options.get("-l"));
            } catch (NumberFormatException e) {
                printArgsError("-l argument must be integer. Got: " + options.get("-l"));
            }
        }
        if (fileOrUrl != null && fileOrUrl.startsWith("http://")) {
            if (!options.containsKey("-l")) {
                maxLines = 1000; // Default for realtime connections
            }
        }
        if (fileOrUrl != null && !fileOrUrl.startsWith("http://")) {
            if (!options.containsKey("-x")) {
                printArgsError("-x argument must be specified when opening a file");
            }
        }
        if (fileOrUrl != null && !fileOrUrl.startsWith("http://")) {
            if (options.containsKey("-s")) {
                printArgsError("-s argument only valid for yamcs connections");
            }
        }

        // Okay, launch the GUI now
        if (options.containsKey("--etc-dir")) {
            Path etcDir = Paths.get(options.get("--etc-dir"));
            YConfiguration.setupTool(etcDir.toFile());
        } else {
            YConfiguration.setupTool();
        }
        theApp = new PacketViewer(maxLines);
        if (fileOrUrl != null) {
            if (fileOrUrl.startsWith("http://")) {
                YamcsConnectionProperties ycd = YamcsConnectionProperties.parse(fileOrUrl);
                String streamName = options.get("-s");
                if (streamName == null) {
                    streamName = "tm_realtime";
                }
                theApp.setStreamName(streamName);
                theApp.connectYamcs(ycd);
            } else {
                File file = new File(fileOrUrl);
                theApp.openFile(file, (String) options.get("-x"));
            }
        }
    }

    public void addParameterToTheLeftTable(Parameter selectedParameter) {
        packetsTable.addParameterColumn(selectedParameter);
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    protected void readConfig(String instance, YConfiguration config) {
        String packetPreprocessorClassName = config.getString(CFG_PREPRO_CLASS, IssPacketPreprocessor.class.getName());
        try {
            if (config.containsKey("packetPreprocessorArgs")) {
                YConfiguration packetPreprocessorArgs = config.getConfig("packetPreprocessorArgs");
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance,
                        packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        } catch (IOException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw new ConfigurationException(e);
        }

        this.packetInputStreamClassName = config.getString("packetInputStreamClassName",
                CcsdsPacketInputStream.class.getName());
        this.packetInputStreamArgs = config.get("packetInputStreamArgs");
    }

    private PacketInputStream getPacketInputStream(InputStream inputStream) throws IOException {
        if (packetInputStreamArgs != null) {
            return YObjectLoader.loadObject(packetInputStreamClassName, inputStream,
                    packetInputStreamArgs);
        } else {
            return YObjectLoader.loadObject(packetInputStreamClassName, inputStream);
        }
    }

}
