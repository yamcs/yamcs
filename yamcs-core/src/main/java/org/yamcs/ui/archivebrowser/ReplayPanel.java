package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.YamcsManagement.ChannelInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.TmStatistics;
import org.yamcs.ui.ChannelControlClient;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Panel containing the replay controls (start/stop, start/stop/current time)
 * @author nm
 *
 */
public class ReplayPanel extends JPanel {
    protected JLabel replayStartLabel, replayCurrentLabel, replayStopLabel, channelNameLabel, replayStatusLabel, replaySpeedLabel;
    protected ImageIcon replayStartIcon, replayStopIcon;
    protected JButton playStopButton;
    public JButton applySelectionButton;
    protected ChannelInfo currentChannelInfo;
    int replayButtonFunction;
    static final int STOP = 0;
    static final int PLAY = 1;
    DataViewer dataViewer;
    
    ChannelControlClient channelControl;
    long currentInstant;
    
    public ReplayPanel() {
        super(new BorderLayout());

        GridBagLayout lay = new GridBagLayout();
        JPanel centerPanel = new JPanel(lay);

     // playing/stopped status
        GridBagConstraints gbc=new GridBagConstraints();
        replayStatusLabel = new JLabel();
        gbc.weightx = 1.0; gbc.gridwidth = 1;
        lay.setConstraints(replayStatusLabel, gbc);
        centerPanel.add(replayStatusLabel);

        // replay: channel name

        JLabel lab = new JLabel("Name:");
        gbc.weightx = 0.0; gbc.gridwidth = 1; gbc.gridheight = 1;
        gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0; gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        centerPanel.add(lab);

        channelNameLabel = new JLabel();
        channelNameLabel.setPreferredSize(new Dimension(150, channelNameLabel.getPreferredSize().height));
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(channelNameLabel, gbc);
        centerPanel.add(channelNameLabel);

        // play/stop button

        replayStartIcon = ArchivePanel.getIcon("start.gif");
        replayStopIcon = ArchivePanel.getIcon("stop.gif");
        playStopButton = new JButton(replayStopIcon); // the Play/Stop button
        playStopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent ae ) {
                playOrStopPressed();
            }
        });
        gbc.weightx = 0.0; gbc.weighty = 1.0; gbc.gridwidth = 1; gbc.gridheight = 4; gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.NONE;
        lay.setConstraints(playStopButton, gbc);
        centerPanel.add(playStopButton);

        // replay: start time

        lab = new JLabel("Start:");
        gbc.weightx = 0.0; gbc.gridwidth = 1; gbc.gridheight = 1;
        gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0; gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        centerPanel.add(lab);

        replayStartLabel = new JLabel();
        replayStartLabel.setPreferredSize(new Dimension(150, replayStartLabel.getPreferredSize().height));
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(replayStartLabel, gbc);
        centerPanel.add(replayStartLabel);

        // replay: current time

        lab = new JLabel("Current:");
        gbc.weightx = 0.0; gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        centerPanel.add(lab);

        replayCurrentLabel = new JLabel();
        replayCurrentLabel.setPreferredSize(new Dimension(150, replayCurrentLabel.getPreferredSize().height));
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(replayCurrentLabel, gbc);
        centerPanel.add(replayCurrentLabel);

        // replay: stop time

        lab = new JLabel("Stop:");
        gbc.weightx = 0.0; gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        centerPanel.add(lab);

        replayStopLabel = new JLabel();
        replayStopLabel.setPreferredSize(new Dimension(150, replayStopLabel.getPreferredSize().height));
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(replayStopLabel, gbc);
        centerPanel.add(replayStopLabel);

        // replay: speed

        lab = new JLabel("Speed:");
        gbc.weightx = 0.0; gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        centerPanel.add(lab);

        replaySpeedLabel = new JLabel();
        replaySpeedLabel.setPreferredSize(new Dimension(150, replaySpeedLabel.getPreferredSize().height));
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(replaySpeedLabel, gbc);
        centerPanel.add(replaySpeedLabel);

        add(centerPanel, BorderLayout.CENTER);

        Box buttonPanel = Box.createVerticalBox();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 50));
        applySelectionButton = new JButton("Apply Selection");
        applySelectionButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        applySelectionButton.setEnabled(false);
        applySelectionButton.setToolTipText("Apply the selection to the replay");
        applySelectionButton.setActionCommand("apply");

        buttonPanel.add(Box.createVerticalGlue());
        buttonPanel.add(applySelectionButton);
        buttonPanel.add(Box.createVerticalGlue());

        add(buttonPanel, BorderLayout.EAST);
    }

    public void setDataViewer(DataViewer dataViewer) {
        this.dataViewer=dataViewer;
    }
    
    public void setChannelControlClient(ChannelControlClient cc) {
        this.channelControl=cc;
    }

    void playOrStopPressed() {
        if ( currentChannelInfo != null ) {
            if ( replayButtonFunction == STOP ) {
                pauseReplay();
            } else {
                resumeReplay();
            }
        }
    }
    
    public void clearReplayPanel() {
        currentChannelInfo = null;

        for ( Component c:getComponents() ) {
            c.setEnabled(false);
        }

        replayStartLabel.setText("");
        replayStopLabel.setText("");
        replayCurrentLabel.setText("");
        //replayStartLabel.setPreferredSize(new Dimension(150, replayStartLabel.getPreferredSize().height));

        dataViewer.dataView.setStartLocator(dataViewer.dataView.DO_NOT_DRAW);
        dataViewer.dataView.setStopLocator(dataViewer.dataView.DO_NOT_DRAW);
        dataViewer.dataView.setCurrentLocator(dataViewer.dataView.DO_NOT_DRAW);
    }
    /**
     * called by the yamcs monitor when a channelinfo update is received from the server
     * @param ci
     */
    public void updateChannelInfol(ChannelInfo ci) {
        if((currentChannelInfo==null)
                || !ci.getInstance().equals(currentChannelInfo.getInstance()) 
                || !ci.getName().equals(currentChannelInfo.getName())
                || !ci.hasReplayRequest()) return ;
        currentChannelInfo = ci;
        updateReplayPanel();
    }

    /**
     * called by yamcs monitor when the selected channel has changed
     * @param ci
     */
    public void setupReplayPanel(ChannelInfo ci) {
        if(ci.hasReplayRequest()) {
            currentChannelInfo = ci;
            if ( isVisible() ) {
                playStopButton.setEnabled(false);
                replayCurrentLabel.setText("");
                dataViewer.dataView.setCurrentLocator(dataViewer.dataView.DO_NOT_DRAW);
                for ( Component c:getComponents() ) {
                    c.setEnabled(true);
                }
                updateReplayPanel();
            }
        } else {
            clearReplayPanel();
        }
    }

    private void updateReplayPanel() {
        if ( currentChannelInfo.getReplayState()==ReplayState.RUNNING ) {
            if ( replayStopIcon == null ) {
                playStopButton.setText("Stop");
            } else {
                playStopButton.setIcon(replayStopIcon);
            }
            playStopButton.setToolTipText("Stop replay and remain at the current position.");
            replayButtonFunction = STOP;
        } else {
            if ( replayStartIcon == null ) {
                playStopButton.setText("Play");
            } else {
                playStopButton.setIcon(replayStartIcon);
            }
            playStopButton.setToolTipText("Start replay from the current position.");
            replayButtonFunction = PLAY;
        }

        ReplayRequest rr=currentChannelInfo.getReplayRequest();
        replayStartLabel.setText(TimeEncoding.toString(rr.getStart()));
        replayStopLabel.setText(TimeEncoding.toString(rr.getStop()));
        replaySpeedLabel.setText(getSpeedLabel(rr.getSpeed()));
        channelNameLabel.setText(currentChannelInfo.getName());

        // draw start/stop locators
        dataViewer.dataView.setStartLocator(rr.getStart());
        dataViewer.dataView.setStopLocator(rr.getStop());
    }
    
    private String getSpeedLabel(ReplaySpeed speed) {
        switch(speed.getType()) {
        case AFAP:
            return "As fast as possible";
        case FIXED_DELAY:
            return "Fixed delay "+(int)speed.getParam()+" ms";
        case REALTIME:
            return "Realtime x"+speed.getParam();
        }
        return "unknown";
    }
    
    public void updateStatistics(Statistics stats) {
        // invoked frequently by YamcsMonitor.updateTmStatsTable()

        if ((currentChannelInfo != null) &&
            currentChannelInfo.hasReplayRequest() &&
            currentChannelInfo.getInstance().equals(stats.getInstance()) &&
            currentChannelInfo.getName().equals(stats.getChannelName())) {

            // find the timestamp of the most recent packet received
            long pos = 0;
            for( TmStatistics ts:stats.getTmstatsList()) {
                pos = Math.max(pos, ts.getLastPacketTime());
            }
            currentInstant=pos;

            replayCurrentLabel.setText(TimeEncoding.toString(currentInstant));
            dataViewer.dataView.setCurrentLocator(currentInstant);
        }
    }

    

    void pauseReplay() {
        try {
            channelControl.pauseArchiveReplay(currentChannelInfo.getInstance(), currentChannelInfo.getName());
        } catch (Exception e) {
            debugLog("exception when stopping replay for channel "+currentChannelInfo.getName()+" :"+e.getMessage());
        }
    }

   
    void resumeReplay() {
        if ( currentInstant == TimeEncoding.INVALID_INSTANT ) {
            debugLog("start replay from " + TimeEncoding.toString(currentChannelInfo.getReplayRequest().getStart()));
        } else {
            debugLog("start replay from " + TimeEncoding.toString(currentInstant));
        }
        try {
            channelControl.resumeArchiveReplay(currentChannelInfo.getInstance(), currentChannelInfo.getName());
        } catch (Exception e) {
            debugLog("exception when starting replay for channel "+currentChannelInfo.getInstance()+"."+currentChannelInfo.getName()+" :"+e.getMessage());
        }
    }

    void seekReplay(long newPosition) {
        debugLog("seeking replay to " + TimeEncoding.toString(newPosition));
        try {
            channelControl.seekArchiveReplay(currentChannelInfo.getInstance(), currentChannelInfo.getName(), newPosition);
        } catch (Exception e) {
            debugLog("exception when starting replay for channel "+currentChannelInfo.getInstance()+"."+currentChannelInfo.getName()+" :"+e.getMessage());
        }
    }
    
    private void debugLog(String string) {
        System.err.println(string);
        
    }
    public void channelStateChanged(ChannelInfo ci) {
        currentChannelInfo=ci;
        updateReplayPanel();
    }
}