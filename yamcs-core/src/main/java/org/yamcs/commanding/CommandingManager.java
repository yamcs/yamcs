package org.yamcs.commanding;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.management.ManagementService;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtceproc.MetaCommandProcessor;

import com.google.common.util.concurrent.AbstractService;
/**
 * Responsible for parsing and tc packet composition.
 * @author nm
 *
 */
public class CommandingManager extends AbstractService {
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	private YProcessor channel;
	private CommandQueueManager commandQueueManager;

	/**
	 * Keeps a reference to the channel and creates the queue manager
	 * @param channel
	 */
	public CommandingManager(YProcessor channel) throws ConfigurationException{
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
	public PreparedCommand buildCommand(MetaCommand mc, List<ArgumentAssignment> argAssignmentList, String origin, int seq, AuthenticationToken authToken) throws ErrorInCommand, NoPermissionException, YamcsException {
		log.debug("building command {} with arguments", mc, argAssignmentList);

		byte[] b = MetaCommandProcessor.buildCommand(mc,  argAssignmentList);

		CommandId cmdId = CommandId.newBuilder().setCommandName(mc.getQualifiedName()).setOrigin(origin).setSequenceNumber(seq).setGenerationTime(TimeEncoding.currentInstant()).build();
		PreparedCommand pc = new PreparedCommand(cmdId);

		pc.setBinary(b);
		String username = (authToken !=null && authToken.getPrincipal() != null)?authToken.getPrincipal().toString():"anonymous";
		pc.setUsername(username);
		// TODO: privileges for building this command
		pc.setMetaCommand(mc);

		return pc;
	}

	public void sendCommand(AuthenticationToken authToken, PreparedCommand pc) {
		log.debug("sendCommand commandSource="+pc.getSource());
		commandQueueManager.addCommand(authToken, pc);
	}

	public YProcessor getChannel() {
		return channel;
	}

	@Override
	protected void doStart() {
		notifyStarted();
	}

	@Override
	protected void doStop() {
		notifyStopped();
	}
}