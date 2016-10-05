package org.yamcs.ui.archivebrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.ui.CommandHistoryRetrievalGui;
import org.yamcs.ui.PacketRetrievalGui;
import org.yamcs.ui.ParameterRetrievalGui;
import org.yamcs.ui.UiColors;
import org.yamcs.ui.YamcsConnector;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * Adds controls to a wrapped {@link org.yamcs.ui.archivebrowser.DataView}
 */
public abstract class DataViewer extends NavigatorItem implements ActionListener {

    private ArchivePanel archivePanel;
    private DataView dataView;
    public JToolBar buttonToolbar;

    private JMenu selectionMenu;
    private JMenuItem packetRetrieval, parameterRetrieval, cmdHistRetrieval;

    JButton zoomInButton, zoomOutButton, showAllButton, newTagButton;
    boolean replayEnabled;

    private JFormattedTextField mouseLocator;
    private JFormattedTextField selectionStart;
    private JFormattedTextField selectionStop;

    private JLabel mouseLocatorLabel;
    private JLabel dottedSquare;

    PacketRetrievalGui packetGui;
    CommandHistoryRetrievalGui cmdHistGui;
    ParameterRetrievalGui parameterGui;

    private static Logger log = LoggerFactory.getLogger(DataViewer.class);

    public DataViewer(YamcsConnector yconnector, ArchiveIndexReceiver indexReceiver, ArchivePanel archivePanel, boolean replayEnabled) {
        super(yconnector, indexReceiver);
        this.archivePanel = archivePanel;
        this.replayEnabled = replayEnabled;

        // Add custom menu items
        selectionMenu = new JMenu("Selection");
        selectionMenu.setVisible(false);
        archivePanel.archiveBrowser.menuBar.add(selectionMenu);

        packetRetrieval = new JMenuItem("Export Packets...");
        packetRetrieval.setToolTipText("Start packet retrieval of the selected packets");
        packetRetrieval.addActionListener(this);
        packetRetrieval.setActionCommand("start-packet-retrieval");
        packetRetrieval.setEnabled(false);
        selectionMenu.add(packetRetrieval);

        parameterRetrieval = new JMenuItem("Export Parameters...");
        parameterRetrieval.setToolTipText("Start parameter retrieval for the selected time interval");
        parameterRetrieval.addActionListener(this);
        parameterRetrieval.setActionCommand("start-parameter-retrieval");
        parameterRetrieval.setEnabled(false);
        selectionMenu.add(parameterRetrieval);

        cmdHistRetrieval = new JMenuItem("Export Command History...");
        cmdHistRetrieval.setToolTipText("Start command history retrieval for the selected time interval");
        cmdHistRetrieval.addActionListener(this);
        cmdHistRetrieval.setActionCommand("start-cmdhist-retrieval");
        cmdHistRetrieval.setEnabled(false);
        selectionMenu.add(cmdHistRetrieval);
    }

    @Override
    public JComponent createContentPanel() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder());
        contentPanel.setBackground(Color.WHITE);
        contentPanel.add(createButtonToolbar(), BorderLayout.NORTH);
        dataView = new DataView(archivePanel, this);
        dataView.addActionListener(this);
        contentPanel.add(dataView, BorderLayout.CENTER);
        return contentPanel;
    }

    @Override
    public void onOpen() {
        selectionMenu.setVisible(true);
    }

    @Override
    public void onClose() {
        selectionMenu.setVisible(false);
    }

    /**
     * Includes a date range for showing the selected interval,
     * and a field that follows the mouse position (similar to
     * TT, but without day of the year formatting.)
     */
    @Override
    public JComponent createNavigatorInset() {
        Box vbox = Box.createVerticalBox();
        Border outsideBorder = BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(0, 10, 0, 10);
        vbox.setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));

        InstantFormat iformat=new InstantFormat();

        Box mouseBox = Box.createHorizontalBox();
        mouseLocatorLabel = new JLabel("\u27a5");
        mouseLocatorLabel.setForeground(Color.LIGHT_GRAY);
        mouseLocatorLabel.setToolTipText("Mouse position");
        mouseBox.add(mouseLocatorLabel);
        mouseLocator = new JFormattedTextField(iformat);
        mouseLocator.setHorizontalAlignment(JTextField.CENTER);
        mouseLocator.setEditable(false);
        mouseLocator.setMaximumSize(new Dimension(150, mouseLocator.getPreferredSize().height));
        mouseLocator.setMinimumSize(mouseLocator.getMaximumSize());
        mouseLocator.setPreferredSize(mouseLocator.getMaximumSize());
        mouseLocator.setFont(mouseLocator.getFont().deriveFont(mouseLocator.getFont().getSize2D() - 3));
        mouseBox.add(mouseLocator);

        Box selectionStartBox = Box.createHorizontalBox();
        dottedSquare = new JLabel("\u2b1a");
        dottedSquare.setForeground(Color.GRAY);
        dottedSquare.setToolTipText("Selected date range");
        selectionStartBox.add(dottedSquare);
        selectionStart = new JFormattedTextField(iformat);
        selectionStart.setHorizontalAlignment(JTextField.CENTER);
        selectionStart.setEditable(false);
        selectionStart.setMaximumSize(new Dimension(150, selectionStart.getPreferredSize().height));
        selectionStart.setMinimumSize(selectionStart.getMaximumSize());
        selectionStart.setPreferredSize(selectionStart.getMaximumSize());
        selectionStart.setFont(selectionStart.getFont().deriveFont(selectionStart.getFont().getSize2D() - 3));
        selectionStartBox.add(selectionStart);
        selectionStart.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                dataView.updateSelection((Long) selectionStart.getValue(), (Long) selectionStop.getValue());
            }
        });

        Box selectionStopBox = Box.createHorizontalBox();
        selectionStop = new JFormattedTextField(iformat);
        selectionStop.setHorizontalAlignment(JTextField.CENTER);
        selectionStop.setEditable(false);
        selectionStop.setMaximumSize(new Dimension(150, selectionStop.getPreferredSize().height));
        selectionStop.setMinimumSize(selectionStop.getMaximumSize());
        selectionStop.setPreferredSize(selectionStop.getMaximumSize());
        selectionStop.setFont(selectionStop.getFont().deriveFont(selectionStop.getFont().getSize2D() - 3));
        selectionStopBox.add(selectionStop);
        selectionStop.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                dataView.updateSelection((Long) selectionStart.getValue(), (Long) selectionStop.getValue());
            }
        });

        mouseBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        vbox.add(mouseBox);
        selectionStartBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        vbox.add(selectionStartBox);
        selectionStopBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        vbox.add(selectionStopBox);

        return vbox;
    }

    public void signalMousePosition(long instant) {
        mouseLocatorLabel.setForeground((instant== TimeEncoding.INVALID_INSTANT) ? Color.LIGHT_GRAY : Color.GRAY);
        mouseLocator.setValue(instant);
    }

    public void signalSelectionChange(Selection selection) {
        if (selection != null) {
            signalSelectionStartChange(selection.getStartInstant());
            signalSelectionStopChange(selection.getStopInstant());
            if(selection.getStartInstant()!=TimeEncoding.INVALID_INSTANT
                    || selection.getStopInstant()!=TimeEncoding.INVALID_INSTANT) {
                dottedSquare.setForeground(Color.BLUE);
            } else {
                dottedSquare.setForeground(Color.GRAY);
            }
            if(replayEnabled) {
                archivePanel.replayPanel.applySelectionButton.setEnabled(true);
            }
        } else {
            signalSelectionStartChange(TimeEncoding.INVALID_INSTANT);
            signalSelectionStopChange(TimeEncoding.INVALID_INSTANT);
            if(dottedSquare!=null) { // FIXME gui set-up should not need resetSelection() call
                dottedSquare.setForeground(Color.GRAY);
            }
            if(replayEnabled) {
                archivePanel.replayPanel.applySelectionButton.setEnabled(false);
            }
        }
    }

    public void signalSelectionStartChange(long startInstant) {
        if(selectionStart!=null) { // FIXME Can be null during gui set-up.
            selectionStart.setEditable((startInstant != TimeEncoding.INVALID_INSTANT));
            selectionStart.setValue(startInstant);
        }
    }

    public void signalSelectionStopChange(long stopInstant) {
        if(selectionStop!=null) { // FIXME Can be null during gui set-up.
            selectionStop.setEditable((stopInstant != TimeEncoding.INVALID_INSTANT));
            selectionStop.setValue(stopInstant);
        }
    }

    public void addIndex(String tableName, String name) {
        addIndex(tableName, name, -1);
    }

    public void addIndex(String tableName, String name, int mergeTime) {
        dataView.addIndex(tableName, name, mergeTime);
        if (replayEnabled && "tm".equals(tableName)) {
            // TODO move up
            archivePanel.replayPanel.setDataViewer(this);
        }
    }

    public void addVerticalGlue() {
        dataView.addVerticalGlue();
    }

    private JToolBar createButtonToolbar() {
        buttonToolbar = new JToolBar("Button Toolbar");
        buttonToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonToolbar.setBackground(Color.WHITE);
        buttonToolbar.setFloatable(false);
        buttonToolbar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        zoomInButton = new JButton("Zoom In");
        zoomInButton.setActionCommand("zoomin");
        zoomInButton.addActionListener(this);
        zoomInButton.setEnabled(false);
        buttonToolbar.add(zoomInButton);

        zoomOutButton = new JButton("Zoom Out");
        zoomOutButton.setActionCommand("zoomout");
        zoomOutButton.addActionListener(this);
        zoomOutButton.setEnabled(false);
        buttonToolbar.add(zoomOutButton);

        showAllButton = new JButton("Show All");
        showAllButton.setActionCommand("showall");
        showAllButton.addActionListener(this);
        showAllButton.setEnabled(false);
        buttonToolbar.add(showAllButton);

        newTagButton=new JButton("New Tag");
        newTagButton.setVisible(archivePanel.archiveBrowser.indexReceiver.supportsTags());
        newTagButton.setEnabled(false);
        newTagButton.setToolTipText("Define a new tag for the current selection");
        newTagButton.addActionListener(this);
        newTagButton.setActionCommand("new-tag-button");
        newTagButton.setVisible(false);
        buttonToolbar.add(newTagButton);
        return buttonToolbar;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("showall")) {
            dataView.showAll();
            zoomOutButton.setEnabled(false);
        } else if (cmd.equals("zoomout")) {
            dataView.zoomOut();
            zoomOutButton.setEnabled(dataView.zoomStack.size() > 1);
        } else if (cmd.equals("zoomin")) {
            dataView.zoomIn();
            zoomOutButton.setEnabled(true);
        } else if (cmd.equalsIgnoreCase("completeness_selection_finished")) {
            if(indexReceiver.supportsTags()) newTagButton.setEnabled(true);
        } else if (cmd.toLowerCase().endsWith("selection_finished")) {
            if(indexReceiver.supportsTags()) newTagButton.setEnabled(true);
            if(cmd.startsWith("pp") || cmd.startsWith("tm")) {
                packetRetrieval.setEnabled(true);
                parameterRetrieval.setEnabled(true);
            } else if (cmd.startsWith("cmdhist")) {
                cmdHistRetrieval.setEnabled(true);
            }
        } else  if(cmd.equalsIgnoreCase("selection_reset")) {
            if (newTagButton != null) newTagButton.setEnabled(false);
            packetRetrieval.setEnabled(false);
            parameterRetrieval.setEnabled(false);
            cmdHistRetrieval.setEnabled(false);
        } else if (cmd.equalsIgnoreCase("new-tag-button")) {
            Selection sel = dataView.getSelection();
            dataView.headerPanel.tagBox.createNewTag(sel.getStartInstant(), sel.getStopInstant());
        } else if(cmd.equalsIgnoreCase("insert-tag")) {
            TagBox.TagEvent te=(TagBox.TagEvent)e;
            indexReceiver.insertTag(archivePanel.archiveBrowser.getInstance(), te.newTag);
        } else if(cmd.equalsIgnoreCase("update-tag")) {
            TagBox.TagEvent te=(TagBox.TagEvent)e;
            indexReceiver.updateTag(archivePanel.archiveBrowser.getInstance(), te.oldTag, te.newTag);
        } else if(cmd.equalsIgnoreCase("delete-tag")) {
            TagBox.TagEvent te=(TagBox.TagEvent)e;
            indexReceiver.deleteTag(archivePanel.archiveBrowser.getInstance(), te.oldTag);
        }  else if (cmd.equals("start-packet-retrieval")) {
            List<String> packets = Collections.emptyList();
            if(dataView.indexBoxes.containsKey("tm")) {
                packets = dataView.getSelectedPackets("tm");
            }
            Selection sel = dataView.getSelection();
            if(packetGui==null) {
                packetGui=new PacketRetrievalGui(yconnector.getConnectionParams(), getContentPanel());
            }
            packetGui.setValues(archivePanel.archiveBrowser.getInstance(), packets, sel.getStartInstant(), sel.getStopInstant());
            packetGui.setVisible(true);

        } else if (cmd.equals("start-parameter-retrieval")) {
            Selection sel = dataView.getSelection();
            if(parameterGui==null) {
                parameterGui = new ParameterRetrievalGui(yconnector.getConnectionParams(), getContentPanel());
            }
            parameterGui.setValues(archivePanel.archiveBrowser.getInstance(), sel.getStartInstant(), sel.getStopInstant());
            parameterGui.setVisible(true);
        } else if (cmd.equals("start-cmdhist-retrieval")) {

            Selection sel = dataView.getSelection();
            if (cmdHistGui == null) {
                    cmdHistGui = new CommandHistoryRetrievalGui(yconnector.getConnectionParams(), getContentPanel());
            }
            cmdHistGui.setValues(archivePanel.archiveBrowser.getInstance(), null, sel.getStartInstant(), sel.getStopInstant());
            cmdHistGui.setVisible(true);
        }
    }

    public DataView getDataView() {
        return dataView;
    }

    @Override
    public void startReloading() {
        log.trace("startReloading. EDT? {} (should be false)", EventQueue.isDispatchThread());
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                zoomInButton.setEnabled(false);
                zoomOutButton.setEnabled(false);
                showAllButton.setEnabled(false);
                if (replayEnabled) {
                    archivePanel.replayPanel.applySelectionButton.setEnabled(false);
                }
            }
        });

        for(IndexBox ib:dataView.indexBoxes.values()) {
            ib.startReloading();
        }
        dataView.headerPanel.tagBox.tags.clear();
    }

    @Override
    public void windowResized() {
        if(dataView.zoomStack.isEmpty() || dataView.zoomStack.size() == 1) {
            dataView.refreshDisplay(true);
            if(!dataView.zoomStack.isEmpty()) {
                dataView.setViewLocationFromZoomstack();
            }
        }
    }

    @Override
    public void receiveArchiveRecords(Yamcs.IndexResult ir) {
        log.trace("receiveArchiveRecords. EDT? {} (should be false)", EventQueue.isDispatchThread());
        if("completeness".equals(ir.getType())) {
            if(dataView.indexBoxes.containsKey("completeness")) {
                dataView.indexBoxes.get("completeness").receiveArchiveRecords(ir.getRecordsList());
            }
        } else if("histogram".equals(ir.getType())) {
            String tableName=ir.getTableName();
            if(dataView.indexBoxes.containsKey(tableName)) {
                dataView.indexBoxes.get(tableName).receiveArchiveRecords(ir.getRecordsList());
            }
        } else {
            System.out.println("Received archive records of type " + ir.getType() + " don't know what to do with them");
        }
    }

    @Override
    public void archiveLoadFinished() {
        log.trace("archiveLoadFinished. EDT? {} (should be false)", EventQueue.isDispatchThread());
        dataView.archiveLoadFinished();
    }

    @Override
    public void receiveTags(List<Yamcs.ArchiveTag> tagList) {
        dataView.headerPanel.tagBox.addTags(tagList);
    }

    @Override
    public void tagAdded(Yamcs.ArchiveTag ntag) {
        dataView.headerPanel.tagBox.addTag(ntag);
    }

    @Override
    public void tagRemoved(Yamcs.ArchiveTag rtag) {
        dataView.headerPanel.tagBox.removeTag(rtag);
    }

    @Override
    public void tagChanged(Yamcs.ArchiveTag oldTag, Yamcs.ArchiveTag newTag) {
        dataView.headerPanel.tagBox.updateTag(oldTag, newTag);
    }
}
