package org.yamcs.ui.packetviewer;


import io.netty.handler.codec.http.HttpMethod;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.prefs.Preferences;

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
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.ui.PrefsObject;
import org.yamcs.ui.YamcsConnector;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.CommandQueueResource;
import org.yamcs.web.websocket.PacketResource;
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

import com.google.protobuf.ByteString;

public class PacketViewer extends JFrame implements ActionListener,
TreeSelectionListener, ParameterRequestManager, ConnectionListener {
    private static final long serialVersionUID = 1L;
    private static final Logger log=LoggerFactory.getLogger(PacketViewer.class);
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
    //used for decoding full packets
    XtceTmProcessor tmProcessor;


    boolean authenticationEnabled = false;
    String streamName;
    private String defaultNamespace;


    public PacketViewer() throws ConfigurationException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        uiPrefs = Preferences.userNodeForPackage(PacketViewer.class);

        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        if(config.containsKey("authenticationEnabled")) {
            authenticationEnabled = config.getBoolean("authenticationEnabled");
        }
        if(config.containsKey("defaultNamespace")) {
            defaultNamespace = config.getString("defaultNamespace");
        }
        // table to the left which shows one row per packet

        packetsTable = new PacketsTable(this);
        packetsTable.setMaxLines(1000);
        JScrollPane packetScrollpane = new JScrollPane(packetsTable);

        // table to the right which shows one row per parameter in the selected packet

        parametersTable = new ParametersTable(this);
        JScrollPane tableScrollpane = new JScrollPane(parametersTable);
        tableScrollpane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (e.getComponent().getWidth() < parametersTable.getPreferredSize().getWidth())
                    parametersTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                else
                    parametersTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
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
        int menuKey=Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

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

        miRecentFiles = new ArrayList<JMenuItem>();
        for (int i = 0; i < 4; i++) {
            menuitem = new JMenuItem();
            menuitem.setMnemonic(KeyEvent.VK_1 + i);
            menuitem.setActionCommand("recent-file-" + i);
            menuitem.addActionListener(this);
            fileMenu.add(menuitem);
            miRecentFiles.add(menuitem);
        }

        updateMenuWithRecentFiles();
        if (!getRecentFiles().isEmpty())
            fileMenu.addSeparator();

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
                    fileRef = "..." + fileRef.substring(slashIndex, slashIndex + maxChars - 2*3) + "...";
                } else {
                    // Output the complete filename, and fill up with initial path segments
                    fileRef = fileRef.substring(0, maxChars - 3 - (fileRef.length() - slashIndex))
                            + "..." + fileRef.substring(slashIndex);
                }
            }

            JMenuItem mi = miRecentFiles.get(i);
            mi.setVisible(true);
            mi.setText((i+1) + " " + fileRef);
            mi.setToolTipText(recentFiles.get(i)[0]);
        }

        for (; i < miRecentFiles.size(); i++)
            miRecentFiles.get(i).setVisible(false);
    }

    static void debugLogComponent(String name, JComponent c) {
        Insets in = c.getInsets();
        System.out.println("component " + name + ": "
                + "min(" + c.getMinimumSize().width + "," + c.getMinimumSize().height + ") "
                + "pref(" + c.getPreferredSize().width + "," + c.getPreferredSize().height + ") "
                + "max(" + c.getMaximumSize().width + "," + c.getMaximumSize().height + ") "
                + "size(" + c.getSize().width + "," + c.getSize().height + ") "
                + "insets(" + in.top + "," + in.left + "," +in.bottom + "," +in.right + ")");
    }

    @Override
    public void log(final String s) {
        SwingUtilities.invokeLater(() -> {
            if(logText!=null) {
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
            if(openFileDialog==null) {
                try {
                    openFileDialog = new OpenFileDialog();
                } catch (ConfigurationException e) {
                    showError("Cannot load local mdb config: "+e.getMessage());
                    return;
                }
            }
            int returnVal = openFileDialog.showDialog(this);
            if (returnVal == OpenFileDialog.APPROVE_OPTION) {
                openFile(openFileDialog.getSelectedFile(), openFileDialog.getSelectedDbConfig());
            }
        } else if (cmd.equals("connect-yamcs")) {
            if(connectDialog==null) {
                connectDialog = new ConnectDialog(this, authenticationEnabled, true, true, true);
            }
            int ret = connectDialog.showDialog();
            if(ret==ConnectDialog.APPROVE_OPTION) {
                streamName = connectDialog.getStreamName();
                connectYamcs(connectDialog.getConnectData());
            }
        } else if (cmd.startsWith("recent-file-")) {
            JMenuItem mi = (JMenuItem) ae.getSource();
            for (String[] recentFile : getRecentFiles())
                if (recentFile[0].equals(mi.getToolTipText()))
                    openFile(new File(recentFile[0]), recentFile[1]);
        }
    }

    private void openFile(File file, String xtceDb) {
        if (!file.exists() || !file.isFile()) {
            JOptionPane.showMessageDialog(null, "File not found: " + file, "File not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        disconnect();
        lastFile = file;
        if(loadLocalXtcedb(xtceDb))
            loadFile();
        updateRecentFiles(lastFile, xtceDb);
    }

    private static class ShortReadException extends Exception{
        public ShortReadException(long needed,  long read, long offset) {
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
        if(tmProcessor!=null) tmProcessor.stopAsync();
        log("Loading local XTCE db "+configName);
        try {
            xtcedb=XtceDbFactory.createInstance(configName);
        } catch (ConfigurationException e) {
            log.error(e.toString(), e);
            showError(e.getMessage());
            return false;
        } catch (DatabaseLoadException e) {
            log.error(e.toString(), e);
            showError(e.getMessage());
            return false;
        }

        tmProcessor=new XtceTmProcessor(xtcedb);

        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.startAsync();
        log(String.format("Loaded definition of %d sequence container%s and %d parameter%s"
                , xtcedb.getSequenceContainers().size(), (xtcedb.getSequenceContainers().size() != 1 ? "s":"")
                , xtcedb.getParameterNames().size(), (xtcedb.getParameterNames().size() != 1 ? "s":"")));

        packetsTable.setupParameterColumns();
        return true;
    }

    private boolean loadRemoteXtcedb(String configName) {
        if(tmProcessor!=null) tmProcessor.stopAsync();
        log("Loading remote XTCE db for yamcs instance "+connectionParams.getInstance());
        RestClient restClient = new RestClient(connectionParams);
        try {
            restClient.setAcceptMediaType(MediaType.JAVA_SERIALIZED_OBJECT);
            restClient.setMaxResponseLength(10*1024*1024);//TODO make this configurable
            byte[] serializedMdb = restClient.doRequest("/mdb/"+connectionParams.getInstance(), HttpMethod.GET).get();
            ObjectInputStream ois=new ObjectInputStream(new ByteArrayInputStream(serializedMdb));
            Object o=ois.readObject();
            xtcedb=(XtceDb) o;
            ois.close();
        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
            return false;
        }


        tmProcessor=new XtceTmProcessor(xtcedb);
        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.startAsync();
        packetsTable.setupParameterColumns();

        log("Loaded "+xtcedb.getSequenceContainers().size()+" sequence containers and "+xtcedb.getParameterNames().size()+" parameters");

        return true;
    }


    void loadFile() {
        new SwingWorker<Void, TmPacketData>() {
            ProgressMonitor progress;
            @Override
            protected Void doInBackground() throws Exception {
                boolean isPacts=false;
                long r;

                try {
                    FileInputStream reader = new FileInputStream(lastFile);
                    byte[] fourb = new byte[4];
                    TmPacketData packet;
                    ByteBuffer buf;
                    int res;
                    int len, offset = 0;

                    clearWindow();
                    int progressMax = (maxLines == -1) ? (int)(lastFile.length()>>10) : maxLines;
                progress = new ProgressMonitor(theApp, String.format("Loading %s", lastFile.getName()),  null, 0, progressMax);

                int packetCount = 0;
                while (!progress.isCanceled()) {
                    res = reader.read(fourb, 0, 4);
                    if (res != 4) break;
                    buf = ByteBuffer.allocate(16);
                    long gentime = TimeEncoding.INVALID_INSTANT;
                    int seqcount = -1;
                    if ((fourb[0] & 0xe8) == 0x08) {// CCSDS packet
                        buf.put(fourb, 0, 4);
                        if((r=reader.read(buf.array(), 4, 12))!=12) throw new ShortReadException(16, r, offset);
                        gentime = CcsdsPacket.getInstant(buf);
                        seqcount = CcsdsPacket.getSequenceCount(buf);
                    } else if((fourb[2]==0) && (fourb[3]==0)) { //hrdp packet - first 4 bytes are packet size in little endian
                        if((r=reader.skip(6))!=6) throw new ShortReadException(6, r, offset);
                        offset+=10;
                        if((r=reader.read(buf.array()))!=16) throw new ShortReadException(16, r, offset);
                    } else {//pacts packet
                        isPacts=true;
                        //System.out.println("pacts packet");
                        // read ASCII header up to the second blank
                        int i, j;
                        StringBuffer hdr = new StringBuffer();
                        j = 0;
                        for(i=0;i<4;i++) {
                            hdr.append((char)fourb[i]);
                            if ( fourb[i] == 32 ) ++j;
                        }
                        offset+=4;
                        while((j < 2) && (i < 20)) {
                            int c = reader.read();
                            if(c==-1)throw new ShortReadException(1, 0, offset);
                            offset++;
                            hdr.append((char)c);
                            if ( c == 32 ) ++j;
                            i++;
                        }
                        if((r=reader.read(buf.array()))!=16) throw new ShortReadException(16,r,offset);
                    }
                    len = CcsdsPacket.getCccsdsPacketLength(buf) + 7;
                    if(len<16) {
                        log("Short packet read: length: "+len);
                        break;
                    }
                    byte[] bufn = new byte[len];
                    System.arraycopy(buf.array(), 0, bufn, 0, 16);
                    r=reader.read(bufn, 16, len-16);
                    if (r != len - 16) throw new ShortReadException(len-16, r, offset);

                    TmPacketData.Builder packetb = TmPacketData.newBuilder().setPacket(ByteString.copyFrom(bufn))
                            .setReceptionTime(TimeEncoding.getWallclockTime());
                    if (gentime != TimeEncoding.INVALID_INSTANT) {
                        packetb.setGenerationTime(gentime);
                    }
                    if (seqcount >= 0) {
                        packetb.setSequenceNumber(seqcount);
                    }
                    packet = packetb.build();


                    offset += len;
                    if(isPacts) {
                        if(reader.skip(1)!=1) throw new ShortReadException(1, 0, offset);
                        offset+=1;
                    }
                    publish(packet);

                    packetCount++;
                    if (packetCount == maxLines) break;
                    progress.setProgress((maxLines == -1) ? (int)(offset>>10) : packetCount);
                }
                reader.close();
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
                for (TmPacketData packet:chunks) {
                    packetsTable.packetReceived(packet);
                }
            }

            @Override
            protected void done() {
                if (progress != null) {
                    if (progress.isCanceled()) {
                        clearWindow();
                        log(String.format("Cancelled loading %s", lastFile.getName()));
                    } else {
                        log(String.format("Loaded %d packet%s from \"%s\"",
                                packetsTable.getRowCount(),
                                packetsTable.getRowCount()!=1?"s":"", lastFile.getPath()));
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

        for (Range bitRange:highlightBits) {
            if (bitRange == null) continue;
            final int highlightStartNibble = bitRange.offset / 4;
            final int highlightStopNibble = (bitRange.offset + bitRange.size + 3) / 4;
            for (n = highlightStartNibble / 32 * 32 ; n < highlightStopNibble; n += 32) {

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
                //System.out.println(String.format("setCharacterAttributes %d/%d %d %d %d/%d %d/%d",
                //  highlightStartNibble, highlightStopNibble, n, textoffset, binHighStart, binHighStop, ascHighStart, ascHighStop));
                hexDoc.setCharacterAttributes(textoffset + binHighStart, binHighStop - binHighStart, highlightedStyle, true);
                hexDoc.setCharacterAttributes(textoffset + ascHighStart, ascHighStop - ascHighStart, highlightedStyle, true);
            }
        }

        // put the caret into the position of the first item (caret makes itself visible by default)
        final int hexScrollPos = (highlightBits.length == 0 || highlightBits[0] == null) ? 0 : (linesize * (highlightBits[0].offset / 128));
        hexText.setCaretPosition(hexScrollPos);
    }


    void connectYamcs(YamcsConnectionProperties ycd) {
        disconnect();
        connectionParams = ycd;
        yconnector = new YamcsConnector("PacketViewer");
        yconnector.addConnectionListener(this);
        yconnector.connect(ycd);
        updateTitle();
    }


    void disconnect() {
        if(yconnector!=null) {
            yconnector.disconnect();
        }
        connectionParams = null;
        updateTitle();
    }

    @Override
    public void valueChanged(TreeSelectionEvent e)	{
        TreePath[] paths = structureTree.getSelectionPaths();
        Range[] bits=null;
        if(paths==null) {
            bits=new Range[0];
        } else {
            bits = new Range[paths.length];
            for (int i = 0; i < paths.length; ++i) {
                Object last = paths[i].getLastPathComponent();
                if (last instanceof TreeEntry) {
                    TreeEntry te = (TreeEntry)last;
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
            Hashtable<String,TreeContainer> containers = new Hashtable<String,TreeContainer>();
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
                String name;

                parametersTable.clear();
                structureRoot.removeAllChildren();

                for (ParameterValue value:params) {

                    // add new leaf to the structure tree
                    // parameters become leaves, and sequence containers become nodes recursively

                    name = value.getParameter().getOpsName();
                    getTreeNode(value.getParameterEntry().getSequenceContainer()).add(new TreeEntry(value));

                    // add new row for parameter table

                    vec[0] = value.getParameter();
                    vec[1] = value.getEngValue().toString();
                    vec[2] = value.getRawValue().toString();

                    vec[3] = value.getWarningRange() == null ? "" : Double.toString(value.getWarningRange().getMinInclusive());
                    vec[4] = value.getWarningRange() == null ? "" : Double.toString(value.getWarningRange().getMaxInclusive());;


                    vec[5] = value.getCriticalRange() == null ? "" : Double.toString(value.getCriticalRange().getMinInclusive());
                    vec[6] = value.getCriticalRange() == null ? "" : Double.toString(value.getCriticalRange().getMaxInclusive());
                    vec[7] = String.valueOf(value.getAbsoluteBitOffset());
                    vec[8] = String.valueOf(value.getBitSize());

                    paramtype = value.getParameter().getParameterType();
                    if (paramtype instanceof EnumeratedParameterType) {
                        vec[9] = paramtype;
                    } else if (paramtype instanceof BaseDataType) {
                        encoding = ((BaseDataType)paramtype).getEncoding();
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
                for (TreeContainer tc:containers.values()) {
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
            tmProcessor.processPacket(new PacketWithTime(TimeEncoding.currentInstant(), currentPacket.getGenerationTime(), b));
        } catch (IOException x) {
            final String msg = String.format("Error while loading %s: %s", lastFile.getName(), x.getMessage());
            log(msg);
            showError(msg);
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
            super(String.format("%d/%d %s", value.getAbsoluteBitOffset(), value.getBitSize(), value.getParameter().getOpsName()), false);
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
            log("connected to "+url);
            if(connectDialog!=null) {
                if(connectDialog.getUseServerMdb()) {
                    if(!loadRemoteXtcedb(connectDialog.getServerMdbConfig())) return;
                } else {
                    if(!loadLocalXtcedb(connectDialog.getLocalMdbConfig())) return;
                }
            } else {
                RestClient restclient = new RestClient(connectionParams);
                List<YamcsInstance> list = restclient.blockingGetYamcsInstances();
                
                for(YamcsInstance yi:list) {
                    if(connectionParams.getInstance().equals(yi.getName())) {
                        String mdbConfig = yi.getMissionDatabase().getConfigName();
                        if(!loadRemoteXtcedb(mdbConfig)) return;
                    }
                }

            }
            WebSocketRequest wsr = new WebSocketRequest(PacketResource.RESOURCE_NAME, CommandQueueResource.OP_subscribe+" "+streamName);
            yconnector.performSubscription(wsr,
                data -> {
                    if(data.hasTmPacket()) {
                        TmPacketData tm = data.getTmPacket();
                        packetsTable.packetReceived(tm);
                    }
                }, e-> {
                    showError("Error subscribing to "+streamName+": "+e.getMessage());
                });
        } catch(Exception e) {
            log(e.toString());
            e.printStackTrace();
        }

    }

    @Override
    public void connecting(String url) {
        log("connecting to "+url);

    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        log("connection to "+url+" failed: "+exception);
    }

    @Override
    public void disconnected() {
        log("disconnected");
    }

    /**
     * Returns the recently opened files from preferences
     * Each entry is a String array with the filename on
     * index 0, and the last used XTCE DB for that file on
     * index 1.
     */
    @SuppressWarnings("unchecked")
    public List<String[]> getRecentFiles() {
        List<String[]> recentFiles = null;
        Object obj = PrefsObject.getObject(uiPrefs, "RecentlyOpened");
        if(obj instanceof ArrayList)
            recentFiles = (ArrayList<String[]>)obj;
        return (recentFiles != null) ? recentFiles : new ArrayList<String[]>();
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
        if (!exists) recentFiles.add(0, new String[] { filename, xtceDb });
        PrefsObject.putObject(uiPrefs, "RecentlyOpened", recentFiles);

        // Also update JMenu accordingly
        updateMenuWithRecentFiles();
    }

    private void removeBorders(JSplitPane splitPane) {
        SplitPaneUI ui = splitPane.getUI();
        if(ui instanceof BasicSplitPaneUI) { // We don't want to mess with other L&Fs
            ((BasicSplitPaneUI)ui).getDivider().setBorder(null);
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
            System.err.println("    -s  name  Name of the stream to connect to (if not specified, it connects to tm_realtime");
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
        Map<String,String> options = new HashMap<String,String>();
        for (int i = 0; i < args.length; i++) {
            if ("-h".equals(args[i])) {
                printUsageAndExit(true);
            } else if ("-l".equals(args[i])) {
                if (i+1 < args.length) {
                    options.put(args[i], args[++i]);
                } else {
                    printArgsError("Number of lines not specified for -l option");
                }
            } else if ("-x".equals(args[i])) {
                if (i+1 < args.length) {
                    options.put(args[i], args[++i]);
                } else {
                    printArgsError("Name of XTCE DB not specified for -x option");
                }
            } else if ("-s".equals(args[i])) {
                if (i+1 < args.length) {
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
        theApp = new PacketViewer();
        if (fileOrUrl != null) {
            if (fileOrUrl.startsWith("http://")) {
                YamcsConnectionProperties ycd = YamcsConnectionProperties.parse(fileOrUrl);
                String streamName =options.get("-s");
                if(streamName==null) {
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
}
