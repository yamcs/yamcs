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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.yamcs.api.ConnectionParameters;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.utils.CommandHistoryFormatter;
import org.yamcs.utils.TimeEncoding;


/**
 * @author nm
 * GUI for requesting packet dumps
 */
public class CommandHistoryRetrievalGui extends JFrame implements MessageHandler, ActionListener {
	JButton startStop;
	JFileChooser fileChooser;
	List<String> cmdNames;
	long startInstant, stopInstant;
	String archiveInstance;
	Component parent;
	ProgressMonitor progressMonitor;
	private File outputFile;
	private BufferedWriter writer;
	ConnectionParameters connectionParams;
	CommandHistoryFormatter cmdhistFormatter;
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
	public CommandHistoryRetrievalGui(ConnectionParameters connectionParams, Component parent) {
		super("Save Command History");
		this.connectionParams=connectionParams;
		this.parent=parent;

		Container frameContentPane=getContentPane();
		frameContentPane.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		
		
		JLabel label=new JLabel("Saving the command history for the selected interval into a file.");
		gbc.gridx=0;gbc.gridy=0;gbc.gridwidth=GridBagConstraints.REMAINDER;
		//gbc.ipadx=5; gbc.ipady=5;
		gbc.insets=new Insets(5,5,5,5);
		frameContentPane.add(label,gbc);
		
		//options
		gbc.insets=new Insets(2,2,2,2);
		
		fileChooser=new JFileChooser("Select Output Directory");
		fileChooser.setApproveButtonText("Save");
		fileChooser.addActionListener(this);

		gbc.gridy=2;gbc.gridx=0;gbc.gridwidth=GridBagConstraints.REMAINDER;gbc.fill = GridBagConstraints.BOTH;
		gbc.weighty = 1.0;gbc.weightx = 1.0;
		frameContentPane.add(fileChooser,gbc);
		
		pack();
	}

	public void setValues(String archiveInstance, List<String> cmdNames, long start, long stop) {
		this.cmdNames=cmdNames;
		this.startInstant=start;
		this.stopInstant=stop;
		this.archiveInstance=archiveInstance;
									
        String startWinCompatibleDateTime = TimeEncoding.toWinCompatibleDateTime(startInstant);
        String stopWinCompatibleDateTime  = TimeEncoding.toWinCompatibleDateTime(stopInstant);
                        
		String fileName = String.format("cmdhistory_%s_%s.csv"
													,startWinCompatibleDateTime
													,stopWinCompatibleDateTime);
		fileChooser.setSelectedFile(new File(fileChooser.getSelectedFile(), fileName));
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
			downloadStartTime=System.currentTimeMillis();
			progressMonitor=new ProgressMonitor(parent,"Saving packets","0 packets saved",0,(int)((stopInstant-startInstant)/1000));
			try {
			    writer=new BufferedWriter(new FileWriter(outputFile));
			    cmdhistFormatter=new CommandHistoryFormatter(writer);

			    YamcsConnectData ycd=(YamcsConnectData)connectionParams;
			    ycd.instance=archiveInstance;
			    ysession=YamcsSession.newBuilder().setConnectionParams(ycd).build();
			    yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
			    CommandHistoryReplayRequest chr = CommandHistoryReplayRequest.newBuilder().build();
			    ReplayRequest.Builder rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT)
                    .setStart(startInstant).setStop(stopInstant).setCommandHistoryRequest(chr);
               
			    StringMessage answer=(StringMessage) yclient.executeRpc(Protocol.getYarchRetrievalControlAddress(ycd.instance), "createReplay", rr.build(), StringMessage.newBuilder());
			    SimpleString replayAddress=new SimpleString(answer.getMessage());
                
			    yclient.dataConsumer.setMessageHandler(this);
			    yclient.executeRpc(replayAddress, "start", null, null);
			} catch (FileNotFoundException e1) {
				JOptionPane.showMessageDialog(parent, "Cannot open file: "+e1.getMessage(),"Cannot open file",JOptionPane.ERROR_MESSAGE);
			} catch (Exception e) {
			    e.printStackTrace();
			    JOptionPane.showMessageDialog(parent, "Exception when retrieving data: "+e,"Exception when retrieving data",JOptionPane.ERROR_MESSAGE);
            }
		}
	}
	
	int count;
	long downloadStartTime;
	
	@Override
	public void onMessage(ClientMessage msg) {
	    int t=msg.getIntProperty(DATA_TYPE_HEADER_NAME);
	    ProtoDataType pdt=ProtoDataType.valueOf(t);
	    if(pdt==ProtoDataType.STATE_CHANGE) {
	        replayFinished();
	        return;
	    }
	    if(pdt!=ProtoDataType.CMD_HISTORY) {
	        exception(new Exception("Unexpected data type "+t));
	        return;
	    }
	    try {
	        CommandHistoryEntry che=(CommandHistoryEntry)decode(msg, CommandHistoryEntry.newBuilder());
	        int progr=(int)((che.getCommandId().getGenerationTime() - startInstant)/1000);
	        count++;
	        if(count%100==0) progressMonitor.setNote(count+" packets received");
	        progressMonitor.setProgress(progr);
	        cmdhistFormatter.writeCommand(che);
	    } catch (YamcsApiException e) {
	        exception(e);
	        e.printStackTrace();
	        return;
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
						    cmdhistFormatter.close();
						} catch (IOException e) {
							JOptionPane.showMessageDialog(parent, "Error when closing the output file: "+e.getMessage(), "Error when closing the output file", JOptionPane.ERROR_MESSAGE);
						}
						if(progressMonitor.isCanceled()) {
							JOptionPane.showMessageDialog(parent, "Retrieval canceled. "+count+" packets retrieved");
						} else {
							progressMonitor.close();
							float speed=(count*1000)/(System.currentTimeMillis()-downloadStartTime);
							JOptionPane.showMessageDialog(parent, "The packet retrieval finished successfully. "+count+" commands retrieved in "+outputFile+". Retrieval speed: "+speed+" cmd/sec");
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

