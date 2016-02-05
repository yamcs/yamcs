package org.yamcs.ui;

import static org.yamcs.api.Protocol.DATA_TYPE_HEADER_NAME;
import static org.yamcs.api.Protocol.decode;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionParameters;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.PacketFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MdbMappings;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * @author nm
 * GUI for requesting packet dumps
 */
public class PacketRetrievalGui extends JFrame implements MessageHandler, ActionListener {
    JCheckBox withoutCcsds;
    JCheckBox pactsFakeHeaders;
    JCheckBox printHex;
    JButton startStop;
    JFileChooser fileChooser;
    List<String> packetNames;
    long startInstant, stopInstant;
    String archiveInstance;
    Component parent;
    ProgressMonitor progressMonitor;
    private File outputFile;
    private OutputStream outputStream;
    ConnectionParameters connectionParams;
    PacketFormatter packetFormatter;
    YamcsSession ysession;
    YamcsClient yclient;

    /**
     * Creates a new window that requests parameter deliveries
     * @param app
     * @param parent
     * @param packetNames
     * @param startTime
     * @param stopTime
     */
    public PacketRetrievalGui(ConnectionParameters connectionParams, Component parent) {
        super("Dump Telemetry Packets");
        this.connectionParams=connectionParams;
        this.parent=parent;

        Container frameContentPane=getContentPane();
        frameContentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();


        JLabel label=new JLabel("Dumping the selected telemetry packets into a file.");
        gbc.gridx=0;gbc.gridy=0;gbc.gridwidth=GridBagConstraints.REMAINDER;
        //gbc.ipadx=5; gbc.ipady=5;
        gbc.insets=new Insets(5,5,5,5);
        frameContentPane.add(label,gbc);

        //options
        JPanel optionsPanel=new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel,BoxLayout.PAGE_AXIS));
        optionsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Options"),BorderFactory.createEmptyBorder(5,5,5,5)));
        withoutCcsds=new JCheckBox("Remove the CCSDS headers");
        optionsPanel.add(withoutCcsds);
        printHex=new JCheckBox("Print in hexadecimal rather than binary");
        optionsPanel.add(printHex);
        pactsFakeHeaders=new JCheckBox("Add a fake 32 bytes PaCTS header");
        optionsPanel.add(pactsFakeHeaders);
        gbc.gridx=0;gbc.gridy=1;gbc.gridwidth=GridBagConstraints.REMAINDER;gbc.weightx = 1.0;gbc.fill = GridBagConstraints.HORIZONTAL;
        frameContentPane.add(optionsPanel,gbc);
        gbc.insets=new Insets(2,2,2,2);

        fileChooser=new JFileChooser("Select Output Directory");
        fileChooser.setApproveButtonText("Save");
        fileChooser.addActionListener(this);

        gbc.gridy=2;gbc.gridx=0;gbc.gridwidth=GridBagConstraints.REMAINDER;gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;gbc.weightx = 1.0;
        frameContentPane.add(fileChooser,gbc);

        pack();
    }

    public void setValues(String archiveInstance, List<String> packetNames, long start, long stop) {
        this.packetNames=packetNames;
        this.startInstant=start;
        this.stopInstant=stop;
        this.archiveInstance=archiveInstance;

        if (packetNames.size() > 0) {
            final String prefix = packetNames.get(0).split("_", 2)[0]; // use the prefix of the first packet name

            String startWinCompatibleDateTime = TimeEncoding.toWinCompatibleDateTime(startInstant);
            String stopWinCompatibleDateTime  = TimeEncoding.toWinCompatibleDateTime(stopInstant);

            String fileName = String.format("%s_packets_%s_%s.dump"
                    ,prefix
                    ,startWinCompatibleDateTime
                    ,stopWinCompatibleDateTime);
            fileChooser.setSelectedFile(new File(fileChooser.getSelectedFile(), fileName));
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();
        if(cmd.equals("CancelSelection")) {
            setVisible(false);
        } else {
            outputFile=fileChooser.getSelectedFile();
            if(outputFile.exists()) {
                if(JOptionPane.showConfirmDialog(this, "Are you sure you want to overwrite "+outputFile,"Overwrite file?",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)
                        ==JOptionPane.NO_OPTION) {
                    return;
                }
            }
            setVisible(false);
            count = 0;
            downloadSize=0;
            downloadStartTime=System.currentTimeMillis();
            progressMonitor=new ProgressMonitor(parent,"Saving packets","0 packets saved",0,(int)((stopInstant-startInstant)/1000));
            try {
                outputStream=new BufferedOutputStream(new FileOutputStream(outputFile));
                packetFormatter=new PacketFormatter(outputStream);
                packetFormatter.setHex(printHex.isSelected());
                packetFormatter.setWithoutCcsds(withoutCcsds.isSelected());
                packetFormatter.setWithPacts(pactsFakeHeaders.isSelected());

                YamcsConnectData ycd=(YamcsConnectData)connectionParams;
                ycd.instance=archiveInstance;
                ysession=YamcsSession.newBuilder().setConnectionParams(ycd).build();
                yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
                ReplayRequest.Builder rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT)
                        .setStart(startInstant).setStop(stopInstant).setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build());

                PacketReplayRequest.Builder prr=PacketReplayRequest.newBuilder();
                for(String pn:packetNames) {
                    prr.addNameFilter(NamedObjectId.newBuilder().setNamespace(MdbMappings.MDB_OPSNAME).setName(pn));
                }
                rr.setPacketRequest(prr);
                StringMessage answer=(StringMessage) yclient.executeRpc(Protocol.getYarchRetrievalControlAddress(ycd.instance), "createReplay", rr.build(), StringMessage.newBuilder());
                SimpleString replayAddress=new SimpleString(answer.getMessage());

                yclient.dataConsumer.setMessageHandler(this);
                yclient.executeRpc(replayAddress, "start", null, null);
            } catch (FileNotFoundException e1) {
                JOptionPane.showMessageDialog(parent, "Cannot open file: "+e1.getMessage(),"Cannot open file",JOptionPane.ERROR_MESSAGE);
            } catch (YamcsException e) {
                if("InvalidIdentification".equals(e.getType())) {
                    StringBuffer errorMessage = new StringBuffer( "Some packet names are invalid:\n" );
                    try {
                        NamedObjectList nol = (NamedObjectList) e.decodeExtra(NamedObjectList.newBuilder());
                        // Prevent error message causing dialog bigger than the screen
                        int itemNum = 0;
                        for( NamedObjectId noi : nol.getListList() ) {
                            errorMessage.append( noi.getName()+", " );
                            if( itemNum > 4 ) { itemNum = 0; errorMessage.append( '\n' ); }
                            itemNum ++;
                        }
                    } catch (InvalidProtocolBufferException e1) {
                        e1.printStackTrace();
                        errorMessage.append( "Sorry, unable to give more details." );
                    }
                    JOptionPane.showMessageDialog(parent, errorMessage.toString(),"Exception when retrieving data",JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(parent, "Exception when retrieving data: "+e.getMessage(),"Exception when retrieving data",JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parent, "Exception when retrieving data: "+e,"Exception when retrieving data",JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    int count;
    long downloadSize;
    long downloadStartTime;

    @Override
    public void onMessage(ClientMessage msg) {
        int t=msg.getIntProperty(DATA_TYPE_HEADER_NAME);
        ProtoDataType pdt=ProtoDataType.valueOf(t);
        try {
            if(pdt==ProtoDataType.STATE_CHANGE) {
                ReplayStatus status = (ReplayStatus) decode(msg, ReplayStatus.newBuilder());
                if(status.getState()==ReplayState.CLOSED) {
                    replayFinished();
                } else if(status.getState()==ReplayState.ERROR) {
                    exception(new Exception("Got error during retrieval: "+status.getErrorMessage()));
                }
                return;
            } else if(pdt!=ProtoDataType.TM_PACKET) {
                exception(new Exception("Unexpected data type "+t));
                return;
            }
            TmPacketData data;

            data = (TmPacketData)decode(msg, TmPacketData.newBuilder());
            packetReceived(new CcsdsPacket(data.getPacket().asReadOnlyByteBuffer()));
        } catch (YamcsApiException e) {
            exception(e);
            e.printStackTrace();
            return;
        }
    }

    public void packetReceived(CcsdsPacket c) {
        int progr=(int)((c.getInstant()-startInstant)/1000);
        count++;
        downloadSize+=c.getLength();
        if(count%100==0) progressMonitor.setNote(count+" packets received");
        progressMonitor.setProgress(progr);
        try {
            packetFormatter.writePacket(c);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void replayFinished() {
        try {
            yclient.close();
            ysession.close();
        } catch (HornetQException e1) {
            e1.printStackTrace();
        }
        SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            packetFormatter.close();
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(parent, "Error when closing the output file: "+e.getMessage(), "Error when closing the output file", JOptionPane.ERROR_MESSAGE);
                        }
                        if(progressMonitor.isCanceled()) {
                            JOptionPane.showMessageDialog(parent, "Retrieval canceled. "+count+" packets retrieved");
                        } else {
                            progressMonitor.close();
                            float speed=(downloadSize*1000)/(1024*(System.currentTimeMillis()-downloadStartTime));
                            JOptionPane.showMessageDialog(parent, "The packet retrieval finished successfully. "+count+" packets retrieved in "+outputFile+". Retrieval speed: "+speed+" KiB/sec");
                        }
                    }
                });
    }

    public void exception(final Exception e) {
        final String message;
        message=e.toString();
        SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        JOptionPane.showMessageDialog(parent, message, message, JOptionPane.ERROR_MESSAGE);
                        progressMonitor.close();
                    }
                });
    }
}

