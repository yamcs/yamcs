package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
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
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.YamcsConnector;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.tctm.CcsdsPacketInputStream;
import org.yamcs.tctm.IssPacketPreprocessor;
import org.yamcs.tctm.PacketInputStream;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.ui.PrefsObject;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

import com.google.common.io.CountingInputStream;
import com.google.protobuf.ByteString;

import io.netty.handler.codec.http.HttpMethod;

public class PacketViewer extends JFrame implements ActionListener,
        TreeSelectionListener, ParameterListener, ConnectionListener, WebSocketClientCallback {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(PacketViewer.class);
    static PacketViewer theApp;
    static int maxLines = -1;
    XtceDb xtcedb;

    File lastFile;
    JSplitPane hexSplit;
    JTextPane hexText;
    StyledDocument hexDoc;
    Style fixedStyle, highlightedStyle;
    JMenu fileMenu;
    List<JMenuItem> miRecentFiles;
    JMenuItem miAutoScroll, miAutoSelect;
    JTextArea logText;
    JScrollPane logScrollpane;
    PacketsTable packetsTable;
    ParametersTable parametersTable;
    JTree structureTree;
    DefaultMutableTreeNode structureRoot;
    DefaultTreeModel structureModel;
    JSplitPane mainsplit;
    FindParameterBar findBar;
    ListPacket currentPacket;
    OpenFileDialog openFileDialog;
    static Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    static final SimpleDateFormat dateTimeFormatFine = new SimpleDateFormat("yyyy.MM.dd/DDD HH:mm:ss.SSS");
    YamcsConnector yconnector;
    ConnectDialog connectDialog;
    GoToPacketDialog goToPacketDialog;
    Preferences uiPrefs;

    YamcsConnectionProperties connectionParams;
    // used for decoding full packets
    XtceTmProcessor tmProcessor;

    String streamName;
    private String defaultNamespace;
    private PacketPreprocessor realtimePacketPreprocessor;

    private Map<String, FileFormat> fileFormats = new LinkedHashMap<>();
    private FileFormat currentFileFormat; // null if listening to server

    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";

    public PacketViewer(int maxLines) throws ConfigurationException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        uiPrefs = Preferences.userNodeForPackage(PacketViewer.class);

        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        if (config.containsKey("defaultNamespace")) {
            defaultNamespace = config.getString("defaultNamespace");
        }
        readConfig(null, config);

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

        getContentPane().add(logsplit, BorderLayout.CENTER);

        clearWindow();
        updateTitle();
        pack();
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

        /*menuitem = new JMenuItem("Preferences", KeyEvent.VK_COMMA);
        menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuKey));
        menu.add(menuitem);
        menu.addSeparator();*/

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
            if (connectionParams != null) {
                title.append(" [").append(connectionParams.getUrl()).append("]");
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
                    openFileDialog = new OpenFileDialog(fileFormats);
                } catch (ConfigurationException e) {
                    showError("Cannot load local mdb config: " + e.getMessage());
                    return;
                }
            }
            int returnVal = openFileDialog.showDialog(this);
            if (returnVal == OpenFileDialog.APPROVE_OPTION) {
                FileFormat fileFormat = openFileDialog.getSelectedFileFormat();
                openFile(openFileDialog.getSelectedFile(), fileFormat, openFileDialog.getSelectedDbConfig());
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
                    if (recentFile.length == 3) {
                        FileFormat fileFormat = fileFormats.get(recentFile[2]);
                        if (fileFormat != null) {
                            openFile(new File(recentFile[0]), fileFormat, recentFile[1]);
                            break;
                        }
                    }

                    FileFormat fileFormat = fileFormats.values().iterator().next();
                    openFile(new File(recentFile[0]), fileFormat, recentFile[1]);
                    break;
                }
            }
        }
    }

    private void openFile(File file, FileFormat fileFormat, String xtceDb) {
        if (!file.exists() || !file.isFile()) {
            JOptionPane.showMessageDialog(null, "File not found: " + file, "File not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        disconnect();
        lastFile = file;
        if (loadLocalXtcedb(xtceDb)) {
            loadFile(fileFormat);
        }
        updateRecentFiles(lastFile, fileFormat, xtceDb);
        currentFileFormat = fileFormat;
    }

    private static class ShortReadException extends Exception {
        public ShortReadException(long needed, long read, long offset) {
            super();
            this.needed = needed;
            this.offset = offset;
            this.read = read;
        }

        long needed;
        long read;
        long offset;

        @Override
        public String toString() {
            return String.format("short seek %d/%d at offset %d", read, needed, offset);
        }
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

        tmProcessor = new XtceTmProcessor(xtcedb);

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
        log("Loading remote XTCE db for yamcs instance " + connectionParams.getInstance());
        RestClient restClient = new RestClient(connectionParams);
        try {
            restClient.setAcceptMediaType(MediaType.JAVA_SERIALIZED_OBJECT);
            restClient.setMaxResponseLength(10 * 1024 * 1024);// TODO make this configurable
            byte[] serializedMdb = restClient.doRequest("/mdb/" + connectionParams.getInstance(), HttpMethod.GET).get();
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedMdb));
            Object o = ois.readObject();
            xtcedb = (XtceDb) o;
            ois.close();
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
            return false;
        }

        tmProcessor = new XtceTmProcessor(xtcedb);
        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.startAsync();
        packetsTable.setupParameterColumns();

        log("Loaded " + xtcedb.getSequenceContainers().size() + " sequence containers and "
                + xtcedb.getParameterNames().size() + " parameters");

        return true;
    }

    void loadFile(FileFormat fileFormat) {
        new SwingWorker<Void, TmPacketData>() {
            ProgressMonitor progress;
            int packetCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                try (CountingInputStream reader = new CountingInputStream(new FileInputStream(lastFile))) {
                    PacketInputStream packetInputStream = fileFormat.newPacketInputStream(reader);
                    PacketWithTime packet;

                    clearWindow();
                    int progressMax = (maxLines == -1) ? (int) (lastFile.length() >> 10) : maxLines;
                    progress = new ProgressMonitor(theApp, String.format("Loading %s", lastFile.getName()), null, 0,
                            progressMax);

                    while (!progress.isCanceled()) {
                        byte[] p = packetInputStream.readPacket();
                        if (p == null) {
                            break;
                        }
                        PacketPreprocessor packetPreprocessor = fileFormat.getPacketPreprocessor();
                        packet = packetPreprocessor.process(p);

                        if (packet != null) {
                            TmPacketData.Builder packetb = TmPacketData.newBuilder()
                                    .setPacket(ByteString.copyFrom(packet.getPacket()))
                                    .setGenerationTime(packet.getGenerationTime())
                                    .setReceptionTime(packet.getReceptionTime())
                                    .setSequenceNumber(packet.getSeqCount());
                            publish(packetb.build());
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
            protected void process(final List<TmPacketData> chunks) {
                for (TmPacketData packet : chunks) {
                    packetsTable.packetReceived(packet);
                }
            }

            @Override
            protected void done() {
                System.out.println("lastFile : " + lastFile);
                if (progress != null) {
                    if (progress.isCanceled()) {
                        clearWindow();
                        log(String.format("Cancelled loading %s", lastFile.getName()));
                    } else {
                        log(String.format("Loaded %d packet%s from \"%s\"",
                                packetsTable.getRowCount(),
                                packetsTable.getRowCount() != 1 ? "s" : "", lastFile.getPath()));
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

        hexDoc.setCharacterAttributes(0, hexDoc.getLength(), fixedStyle, true); // reset styles throughout the document

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
        connectionParams = ycd;
        yconnector = new YamcsConnector("PacketViewer");
        yconnector.addConnectionListener(this);
        yconnector.connect(ycd);
        currentFileFormat = null;
        updateTitle();
    }

    void disconnect() {
        if (yconnector != null) {
            yconnector.disconnect();
        }
        connectionParams = null;
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
                Object[] vec = new Object[parametersTable.getColumnCount()];
                DataEncoding encoding;
                Calibrator calib;
                Object paramtype;

                parametersTable.clear();
                structureRoot.removeAllChildren();

                for (ParameterValue value : params) {

                    // add new leaf to the structure tree
                    // parameters become leaves, and sequence containers become nodes recursively

                    getTreeNode(value.getSequenceEntry().getSequenceContainer()).add(new TreeEntry(value));

                    // add new row for parameter table

                    vec[0] = value.getParameter();

                    vec[1] = (value.getEngValue() != null) ? value.getEngValue().toString() : null;
                    vec[2] = (value.getRawValue() != null) ? value.getRawValue().toString() : null;

                    vec[3] = value.getWarningRange() == null ? "" : Double.toString(value.getWarningRange().getMin());
                    vec[4] = value.getWarningRange() == null ? "" : Double.toString(value.getWarningRange().getMax());

                    vec[5] = value.getCriticalRange() == null ? "" : Double.toString(value.getCriticalRange().getMin());
                    vec[6] = value.getCriticalRange() == null ? "" : Double.toString(value.getCriticalRange().getMax());
                    vec[7] = String.valueOf(value.getAbsoluteBitOffset());
                    vec[8] = String.valueOf(value.getBitSize());

                    paramtype = value.getParameter().getParameterType();
                    if (paramtype instanceof EnumeratedParameterType) {
                        vec[9] = paramtype;
                    } else if (paramtype instanceof BaseDataType) {
                        encoding = ((BaseDataType) paramtype).getEncoding();
                        calib = null;
                        if (encoding instanceof IntegerDataEncoding) {
                            calib = ((IntegerDataEncoding) encoding).getDefaultCalibrator();
                        } else if (encoding instanceof FloatDataEncoding) {
                            calib = ((FloatDataEncoding) encoding).getDefaultCalibrator();
                        }
                        vec[9] = calib == null ? "" : calib.toString();
                    }

                    parametersTable.addRow(vec);
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
            }
        });
    }

    public void setSelectedPacket(ListPacket listPacket) {
        currentPacket = listPacket;
        try {
            currentPacket.load(lastFile);
            byte[] b = currentPacket.getBuffer();
            PacketPreprocessor packetPreprocessor = getCurrentPacketPreprocessor();
            SequenceContainer rootContainer = getCurrentRootContainer();
            tmProcessor.processPacket(packetPreprocessor.process(b), rootContainer);
        } catch (IOException x) {
            final String msg = String.format("Error while loading %s: %s", lastFile.getName(), x.getMessage());
            log(msg);
            showError(msg);
        }
    }

    SequenceContainer getCurrentRootContainer() {
        if (currentFileFormat != null && currentFileFormat.getRootContainer() != null) {
            return xtcedb.getSequenceContainer(currentFileFormat.getRootContainer());
        } else {
            return xtcedb.getRootSequenceContainer();
        }
    }

    private PacketPreprocessor getCurrentPacketPreprocessor() {
        if (currentFileFormat != null) {
            return currentFileFormat.getPacketPreprocessor();
        } else {
            return realtimePacketPreprocessor;
        }
    }

    class TreeContainer extends DefaultMutableTreeNode {
        TreeContainer(SequenceContainer sc) {
            super(sc.getOpsName(), true);
        }
    }

    class TreeEntry extends DefaultMutableTreeNode {
        int bitOffset, bitSize;

        TreeEntry(ParameterValue value) {
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
        connectionParams = yconnector.getConnectionParams();
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
                RestClient restclient = new RestClient(connectionParams);
                List<YamcsInstance> list = restclient.blockingGetYamcsInstances();

                for (YamcsInstance yi : list) {
                    if (connectionParams.getInstance().equals(yi.getName())) {
                        String mdbConfig = yi.getMissionDatabase().getConfigName();
                        if (!loadRemoteXtcedb(mdbConfig)) {
                            return;
                        }
                    }
                }

            }
            WebSocketRequest wsr = new WebSocketRequest("packets", "subscribe " + streamName);
            yconnector.performSubscription(wsr, this, e -> {
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
            packetsTable.packetReceived(tm);
        }
    }

    @Override
    public void connecting(String url) {
        log("connecting to " + url);

    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
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
        // Remove outdated entries
        recentFiles = recentFiles.stream()
                .filter(f -> f.length == 3)
                .filter(f -> fileFormats.get(f[2]) != null)
                .collect(Collectors.toList());
        return (recentFiles != null) ? recentFiles : new ArrayList<>();
    }

    private void updateRecentFiles(File file, FileFormat fileFormat, String xtceDb) {
        String filename = file.getAbsolutePath();
        List<String[]> recentFiles = getRecentFiles();
        boolean exists = false;
        for (int i = 0; i < recentFiles.size(); i++) {
            String[] entry = recentFiles.get(i);
            if (entry[0].equals(filename)) {
                entry[1] = xtceDb;
                entry[2] = fileFormat.getName();
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
        YConfiguration.setup();
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
                FileFormat fileFormat = theApp.fileFormats.values().iterator().next();
                theApp.openFile(file, fileFormat, (String) options.get("-x"));
            }
        }
    }

    public void addParameterToTheLeftTable(Parameter selectedParameter) {
        packetsTable.addParameterColumn(selectedParameter);
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    private PacketPreprocessor loadPacketPreprocessor(String instance, Map<String, Object> config) {
        String packetPreprocessorClassName = YConfiguration.getString(config, CFG_PREPRO_CLASS,
                IssPacketPreprocessor.class.getName());
        try {
            if (config.containsKey("packetPreprocessorArgs")) {
                Map<String, Object> packetPreprocessorArgs = YConfiguration.getMap(config, "packetPreprocessorArgs");
                PacketPreprocessor preprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance,
                        packetPreprocessorArgs);
                return preprocessor;
            } else {
                PacketPreprocessor preprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance);
                return preprocessor;
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        } catch (IOException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw new UncheckedIOException(e);
        }
    }

    protected void readConfig(String instance, YConfiguration config) {
        realtimePacketPreprocessor = loadPacketPreprocessor(instance, config.getRoot());

        if (config.containsKey("fileFormats")) {
            List<Map<String, Object>> fileFormatsConfig = config.getList("fileFormats");
            for (Map<String, Object> fileFormatConfig : fileFormatsConfig) {
                String name = YConfiguration.getString(fileFormatConfig, "name");
                String packetInputStreamClassName = YConfiguration.getString(fileFormatConfig,
                        "packetInputStreamClassName");
                Map<String, Object> packetInputStreamArgs = Collections.emptyMap();
                if (fileFormatConfig.containsKey("packetInputStreamArgs")) {
                    packetInputStreamArgs = YConfiguration.getMap(fileFormatConfig, "packetInputStreamArgs");
                }

                PacketPreprocessor filePacketPreprocessor = realtimePacketPreprocessor;
                if (fileFormatConfig.containsKey(CFG_PREPRO_CLASS)) {
                    filePacketPreprocessor = loadPacketPreprocessor(instance, fileFormatConfig);
                }

                FileFormat fileFormat = new FileFormat(name, packetInputStreamClassName, packetInputStreamArgs,
                        filePacketPreprocessor);
                fileFormat.setRootContainer(YConfiguration.getString(fileFormatConfig, "rootContainer", null));
                fileFormats.put(name, fileFormat);
            }
        } else {
            String defaultFormatName = "CCSDS Packets";
            String defaultPacketInputStreamClassName = CcsdsPacketInputStream.class.getName();
            Map<String, Object> defaultPacketInputStreamArgs = Collections.emptyMap();
            fileFormats.put(defaultFormatName, new FileFormat(
                    defaultFormatName, defaultPacketInputStreamClassName, defaultPacketInputStreamArgs,
                    realtimePacketPreprocessor));
        }
    }
}
