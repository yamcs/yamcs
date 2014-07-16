package org.yamcs.ui.archivebrowser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Adds controls to a wrapped {@link org.yamcs.ui.archivebrowser.DataView}
 */
public class DataViewer extends JPanel implements ActionListener {
    private ArchivePanel archivePanel;
    DataView dataView;
    public JToolBar buttonToolbar;

    JButton zoomInButton, zoomOutButton, showAllButton, applyButton;
    JFormattedTextField selectionStart, selectionStop;
    boolean replayEnabled;

    public DataViewer(ArchivePanel archivePanel, boolean replayEnabled) {
        super(new BorderLayout());
        this.archivePanel = archivePanel;
        this.replayEnabled = replayEnabled;
        setBorder(BorderFactory.createEmptyBorder());
        setBackground(Color.WHITE);
        add(createFixedContent(), BorderLayout.NORTH);

        dataView = new DataView(archivePanel, this);
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

    private Box createFixedContent() {
        Box fixedTop = Box.createVerticalBox();
        fixedTop.setOpaque(false);
        fixedTop.add(createButtonToolbar());

        Box top=getTopBox();
        top.setAlignmentX(0);
        fixedTop.add(top);
        return fixedTop;
    }

    private Box getTopBox() {
        // status bars on the northern part of the window

        Box top = Box.createHorizontalBox();

        GridBagLayout lay = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel archiveinfo = new JPanel(lay);
        archiveinfo.setOpaque(false);
        top.add(archiveinfo);
        gbc.insets = new Insets(4, 4, 0, 1);

        JLabel lab = new JLabel("Selection:");
        gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);
        InstantFormat iformat=new InstantFormat();
        selectionStart = new JFormattedTextField(iformat);
        selectionStart.setHorizontalAlignment(JTextField.RIGHT);
        selectionStart.setMaximumSize(new Dimension(175, selectionStart.getPreferredSize().height));
        selectionStart.setMinimumSize(selectionStart.getMaximumSize());
        selectionStart.setPreferredSize(selectionStart.getMaximumSize());
        lay.setConstraints(selectionStart, gbc);
        archiveinfo.add(selectionStart);
        selectionStart.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                dataView.updateSelection();
            }
        });

        lab = new JLabel("-");
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);

        selectionStop = new JFormattedTextField(iformat);
        selectionStop.setHorizontalAlignment(JTextField.RIGHT);
        selectionStop.setMaximumSize(selectionStop.getPreferredSize());
        selectionStop.setMaximumSize(new Dimension(175, selectionStop.getPreferredSize().height));
        selectionStop.setMinimumSize(selectionStop.getMaximumSize());
        selectionStop.setPreferredSize(selectionStop.getMaximumSize());
        gbc.gridwidth = GridBagConstraints.REMAINDER; gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(selectionStop, gbc);
        archiveinfo.add(selectionStop);
        selectionStop.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                dataView.updateSelection();
            }
        });

        return top;
    }

    private JToolBar createButtonToolbar() {
        buttonToolbar = new JToolBar("Button Toolbar");
        buttonToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonToolbar.setOpaque(false);
        buttonToolbar.setFloatable(false);

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
        }
    }
}
