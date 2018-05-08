package org.yamcs.ui.yamcsmonitor;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

import org.yamcs.protobuf.Yamcs;
import org.yamcs.utils.TimeEncoding;

@SuppressWarnings("serial")
public class ArchiveProcWidget extends ProcessorWidget {

    String archiveInstance;
    long start, stop;
    JList<String> packetList;
    JLabel startLabel, stopLabel, instanceLabel;
    JCheckBox loopButton;
    JRadioButton speedRealtimeRadio, speedFixedRadio;

    public ArchiveProcWidget(String channelType) {
        super(channelType);
    }

    @Override
    public JComponent createConfigurationPanel() {
        JPanel configurationPanel = new JPanel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        configurationPanel.setLayout(gridbag);

        JButton button = new JButton("Select Range From Archive...");
        button.addActionListener(ae -> YamcsMonitor.theApp.showArchiveBrowserSelector());
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gridbag.setConstraints(button, gbc);
        configurationPanel.add(button);

        JLabel label = new JLabel("Start:", SwingConstants.RIGHT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gridbag.setConstraints(label, gbc);
        configurationPanel.add(label);

        startLabel = new JLabel();
        startLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, startLabel.getMaximumSize().height));
        startLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gridbag.setConstraints(startLabel, gbc);
        configurationPanel.add(startLabel);

        label = new JLabel("Stop:", SwingConstants.RIGHT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 1;
        gridbag.setConstraints(label, gbc);
        configurationPanel.add(label);

        stopLabel = new JLabel();
        stopLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, stopLabel.getMaximumSize().height));
        stopLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gridbag.setConstraints(stopLabel, gbc);
        configurationPanel.add(stopLabel);

        label = new JLabel("Instance:", SwingConstants.RIGHT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 1;
        gridbag.setConstraints(label, gbc);
        configurationPanel.add(label);

        instanceLabel = new JLabel();
        instanceLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gridbag.setConstraints(instanceLabel, gbc);
        configurationPanel.add(instanceLabel);

        // TM packet list

        packetList = new JList<>();
        packetList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        packetList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        packetList.getActionMap().put("delete", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                Vector<String> vec = new Vector<>();
                ListModel<String> lm = packetList.getModel();
                for (int i = 0; i < lm.getSize(); ++i) {
                    if (!packetList.isSelectedIndex(i)) {
                        vec.add(lm.getElementAt(i));
                    }
                }
                packetList.setListData(vec);
            }
        });
        JScrollPane scrollPane = new JScrollPane(packetList);
        scrollPane.setPreferredSize(new Dimension(150, 80));
        scrollPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gridbag.setConstraints(scrollPane, gbc);
        configurationPanel.add(scrollPane);

        // playback speed

        Box hbox = Box.createHorizontalBox();
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gridbag.setConstraints(hbox, gbc);
        configurationPanel.add(hbox);

        label = new JLabel("Speed:");
        hbox.add(label);
        speedRealtimeRadio = new JRadioButton("Realtime");
        speedRealtimeRadio.setToolTipText("Play telemetry at a speed according to their CCSDS timestamps.");
        hbox.add(speedRealtimeRadio);
        speedFixedRadio = new JRadioButton("Fixed");
        speedFixedRadio.setToolTipText("Play telemetry at 1 packet per second, ignoring CCSDS timestamps.");
        speedFixedRadio.setSelected(true);
        hbox.add(speedFixedRadio);
        ButtonGroup group = new ButtonGroup();
        group.add(speedRealtimeRadio);
        group.add(speedFixedRadio);

        // loop replay

        loopButton = new JCheckBox("Loop Replay");
        loopButton.setToolTipText("When checked, replay restarts after it has ended. Otherwise it is stopped.");
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gridbag.setConstraints(loopButton, gbc);
        configurationPanel.add(loopButton);

        // for testing
        // String[] packets = { "SOLAR_Tlm_Pkt_HK" };
        // apply(new Date((long)1101855600 * 1000), new Date((long)1101942000 * 1000), packets);

        return configurationPanel;
    }

    void apply(String archiveInstance, long start, long stop, String[] packets) {
        this.archiveInstance = archiveInstance;
        this.start = start;
        this.stop = stop;

        startLabel.setText(TimeEncoding.toOrdinalDateTime(start));
        stopLabel.setText(TimeEncoding.toOrdinalDateTime(stop));
        instanceLabel.setText(archiveInstance);
        packetList.setListData(packets);

        nameComponent.setText("Archive"); // TODO use setter (?)
    }

    @Override
    public void activate() {
        // ArchiveBrowserSelector can be shared among widgets
        // archiveSelector.setActiveWidget(this);
    }

    @Override
    public boolean requiresArchiveBrowser() {
        return true;
    }

    @Override
    public Yamcs.ReplayRequest getReplayRequest() {
        if (start < 0) {
            YamcsMonitor.theApp.showMessage("Please specify a start date and a stop date first.");
            return null;
        }

        Yamcs.ReplayRequest.Builder rr = Yamcs.ReplayRequest.newBuilder().setEndAction(Yamcs.EndAction.STOP)
                .setStart(start).setStop(stop)
                .setSpeed(Yamcs.ReplaySpeed.newBuilder().setType(Yamcs.ReplaySpeed.ReplaySpeedType.AFAP).build());

        // set end action
        if (loopButton.isSelected()) {
            rr.setEndAction(Yamcs.EndAction.LOOP);
        } else {
            rr.setEndAction(Yamcs.EndAction.STOP);
        }

        // set speed
        Yamcs.ReplaySpeed.Builder rs = Yamcs.ReplaySpeed.newBuilder();
        if (speedRealtimeRadio.isSelected()) {
            rs.setType(Yamcs.ReplaySpeed.ReplaySpeedType.REALTIME);
            rs.setParam(1);
        } else if (speedFixedRadio.isSelected()) {
            rs.setType(Yamcs.ReplaySpeed.ReplaySpeedType.FIXED_DELAY);
            rs.setParam(200);
        } else {
            rs.setType(Yamcs.ReplaySpeed.ReplaySpeedType.AFAP);
        }
        rr.setSpeed(rs.build());

        // list of packets
        ListModel<String> model = packetList.getModel();
        Yamcs.PacketReplayRequest.Builder prr = Yamcs.PacketReplayRequest.newBuilder();
        for (int i = 0; i < model.getSize(); i++) {
            prr.addNameFilter(Yamcs.NamedObjectId.newBuilder().setName(model.getElementAt(i).toString()));
        }
        rr.setPacketRequest(prr);

        return rr.build();
    }
}
