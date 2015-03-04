package org.yamcs.commanding;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.management.ManagementService;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtceproc.MetaCommandProcessor;
/**
 * Responsible for parsing and tc packet composition.
 * @author nm
 *
 */
public class CommandingManager {
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	private Channel channel;
	private CommandQueueManager commandQueueManager; 

	/**
	 * Keeps a reference to the channel and creates the queue manager
	 * @param channel 
	 */
	public CommandingManager(Channel channel) throws ConfigurationException{
		this.channel=channel;
		this.commandQueueManager=new CommandQueueManager(this);
		ManagementService.getInstance().registerCommandQueueManager(channel.getInstance(), channel.getName(), commandQueueManager);
	}
	
	public CommandQueueManager getCommandQueueManager() {
		return commandQueueManager;
	}
	
    
	/**
	 * pc is a command whose source is included.
	 * parse the source populate the binary part and the definition.
	 */
	public PreparedCommand buildCommand(MetaCommand mc, List<ArgumentAssignment> argAssignmentList, String origin, int seq, String username) throws ErrorInCommand, NoPermissionException, YamcsException {
		log.debug("building command {} with arguments", mc, argAssignmentList);
		
		byte[] b = MetaCommandProcessor.buildCommand(mc,  argAssignmentList); 
		
		CommandId cmdId = CommandId.newBuilder().setCommandName(mc.getQualifiedName()).setOrigin(origin).setSequenceNumber(seq).setGenerationTime(TimeEncoding.currentInstant()).build();
		PreparedCommand pc = new PreparedCommand(cmdId);
		pc.setSource(mc.getName()+argAssignmentList);
		pc.setBinary(b);
		pc.setUsername(username);
		
		return pc;
	}

	public void sendCommand(PreparedCommand pc) {
		log.debug("sendCommand commandSource="+pc.source);
		commandQueueManager.addCommand(pc);
	}

	public Channel getChannel() {
		return channel;
	}
}
