package org.yamcs.ui;


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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

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

import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.PacketFormatter;
import org.yamcs.utils.TimeEncoding;

/**
 * @author nm
 * GUI for requesting packet dumps
 */
public class PacketRetrievalGui extends JFrame implements ActionListener {
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
    YamcsConnectionProperties connectionParams;
    PacketFormatter packetFormatter;
    private CompletableFuture<Void> completableFuture;

    /**
     * Creates a new window that requests parameter deliveries
     * 
     */
    public PacketRetrievalGui(YamcsConnectionProperties connectionParams, Component parent) {
        super("Dump Telemetry Packets");
        this.connectionParams = connectionParams;
        this.parent = parent;

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
                outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                packetFormatter=new PacketFormatter(outputStream);
                packetFormatter.setHex(printHex.isSelected());
                packetFormatter.setWithoutCcsds(withoutCcsds.isSelected());
                packetFormatter.setWithPacts(pactsFakeHeaders.isSelected());

                YamcsConnectionProperties ycd = (YamcsConnectionProperties)connectionParams;
                ycd.setInstance(archiveInstance);
                RestClient restClient = new RestClient(ycd);
                StringBuilder sb = new StringBuilder();
                sb.append("/archive/").append(archiveInstance).append("/downloads/packets")
                  .append("?start=").append(TimeEncoding.toString(startInstant))
                  .append("&stop=").append(TimeEncoding.toString(stopInstant))
                  .append("&name=");
                boolean first = true;
                for(String pn:packetNames) {
                    if(first) {
                        first = false;
                    } else {
                        sb.append(",");
                    }
                    sb.append(pn);
                }
                completableFuture = restClient.doBulkGetRequest(sb.toString(), (data) -> {
                    TmPacketData tmpacket;
                    try {
                        tmpacket = TmPacketData.parseFrom(data);
                        packetReceived(new CcsdsPacket(tmpacket.getPacket().asReadOnlyByteBuffer()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                 });
                
                completableFuture.whenComplete((v, t) ->{
                    if((t==null) || (t instanceof CancellationException)) {
                        replayFinished();
                    } else {
                        exception(t);
                    }
                });
                
            } catch (FileNotFoundException e1) {
                JOptionPane.showMessageDialog(parent, "Cannot open file: "+e1.getMessage(),"Cannot open file",JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent, "Exception when retrieving data: "+e.getMessage(),"Exception when retrieving data",JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    int count;
    long downloadSize;
    long downloadStartTime;


    public void packetReceived(CcsdsPacket c) {
        int progr=(int)((c.getInstant()-startInstant)/1000);
        count++;
        downloadSize+=c.getLength();
        if(count%100==0) progressMonitor.setNote(count+" packets received");
        progressMonitor.setProgress(progr);
        if(progressMonitor.isCanceled()) completableFuture.cancel(true);
        
        try {
            packetFormatter.writePacket(c);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void replayFinished() {
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

    public void exception(final Throwable e) {
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

