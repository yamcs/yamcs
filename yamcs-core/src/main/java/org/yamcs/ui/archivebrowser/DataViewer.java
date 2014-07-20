package org.yamcs.ui.archivebrowser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Adds controls to a wrapped {@link org.yamcs.ui.archivebrowser.DataView}
 */
public class DataViewer extends JPanel implements ActionListener {
    private ArchivePanel archivePanel;
    DataView dataView;
    public JToolBar buttonToolbar;

    JButton zoomInButton, zoomOutButton, showAllButton, applyButton, newTagButton;
    boolean replayEnabled;

    // Remember menu states for when changing back to this tab
    private boolean packetRetrievalEnabled;
    private boolean parameterRetrievalEnabled;
    private boolean cmdhistRetrievalEnabled;

    public DataViewer(ArchivePanel archivePanel, boolean replayEnabled) {
        super(new BorderLayout());
        this.archivePanel = archivePanel;
        this.replayEnabled = replayEnabled;

        setBorder(BorderFactory.createEmptyBorder());
        setBackground(Color.WHITE);
        add(createButtonToolbar(), BorderLayout.NORTH);
        dataView = new DataView(archivePanel, this);
        dataView.addActionListener(this);
        add(dataView, BorderLayout.CENTER);
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
        } else if (cmd.equals("zoomout")) {
            dataView.zoomOut();
        } else if (cmd.equals("zoomin")) {
            dataView.zoomIn();
        } else if (cmd.equalsIgnoreCase("completeness_selection_finished")) {
            if(archivePanel.archiveBrowser.indexReceiver.supportsTags()) newTagButton.setEnabled(true);
        } else if (cmd.toLowerCase().endsWith("selection_finished")) {
            if(archivePanel.archiveBrowser.indexReceiver.supportsTags()) newTagButton.setEnabled(true);
            packetRetrievalEnabled = true;
            parameterRetrievalEnabled = true;
            if(cmd.startsWith("pp") | cmd.startsWith("tm")) {
                packetRetrievalEnabled = true;
                parameterRetrievalEnabled = true;
            } else if (cmd.startsWith("cmdhist")) {
                cmdhistRetrievalEnabled = true;
            }
        } else  if(cmd.equalsIgnoreCase("selection_reset")) {
            if (newTagButton != null) newTagButton.setEnabled(false);
            packetRetrievalEnabled = false;
            parameterRetrievalEnabled = false;
            cmdhistRetrievalEnabled = false;
        } else if (cmd.equalsIgnoreCase("new-tag-button")) {
            archivePanel.createNewTag(archivePanel.getSelection());
        } else if(cmd.equalsIgnoreCase("insert-tag")) {
            TagBox.TagEvent te=(TagBox.TagEvent)e;
            archivePanel.archiveBrowser.indexReceiver.insertTag(archivePanel.getInstance(), te.newTag);
        } else if(cmd.equalsIgnoreCase("update-tag")) {
            TagBox.TagEvent te=(TagBox.TagEvent)e;
            archivePanel.archiveBrowser.indexReceiver.updateTag(archivePanel.getInstance(), te.oldTag, te.newTag);
        } else if(cmd.equalsIgnoreCase("delete-tag")) {
            TagBox.TagEvent te=(TagBox.TagEvent)e;
            archivePanel.archiveBrowser.indexReceiver.deleteTag(archivePanel.getInstance(), te.oldTag);
        }
        updateMenuStates();
    }

    public void updateMenuStates() {
        archivePanel.archiveBrowser.packetRetrieval.setEnabled(packetRetrievalEnabled);
        archivePanel.archiveBrowser.parameterRetrieval.setEnabled(parameterRetrievalEnabled);
        archivePanel.archiveBrowser.cmdHistRetrieval.setEnabled(cmdhistRetrievalEnabled);
    }

    public void enableNewTagButton() {
        newTagButton.setVisible(true);
    }
}
