package org.yamcs.commanding;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.management.ManagementService;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.MetaCommandContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ArgumentTypeProcessor;
import org.yamcs.xtceproc.TcProcessingContext;
import org.yamcs.xtceproc.XtceDbFactory;
/**
 * Responsible for parsing and tc packet composition.
 * @author nm
 *
 */
public class CommandingManager {
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	private Channel channel;
	private CommandQueueManager commandQueueManager; 
	XtceDb xtcedb;
	/**
	 * Keeps a reference to the channel and creates the queue manager
	 * @param channel 
	 */
	public CommandingManager(Channel channel) throws ConfigurationException{
		this.channel=channel;
        xtcedb=XtceDbFactory.getInstance(channel.getInstance());
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
		log.debug("building command ");
		
		MetaCommandContainer def=null;
		def=mc.getCommandContainer();
		if(def==null) {
			throw new ErrorInCommand("MetaCommand has no container: "+def);
		}
		Map<Argument, Value> args = new HashMap<Argument,Value>();
		Map<String,String> argAssignment = new HashMap<String, String> ();
		for(ArgumentAssignment aa: argAssignmentList) {
			argAssignment.put(aa.getArgumentName(), aa.getArgumentValue());
		}
		
		collectAndCheckArguments(mc, args, argAssignment);

		TcProcessingContext pcontext = new TcProcessingContext(ByteBuffer.allocate(1000), 0);
		pcontext.argValues = args;
		pcontext.mccProcessor.encode(def);		
		
		
		byte[] b = new byte[pcontext.size];
		CommandId cmdId = CommandId.newBuilder().setCommandName(mc.getQualifiedName()).setOrigin(origin).setSequenceNumber(seq).setGenerationTime(TimeEncoding.currentInstant()).build();
		PreparedCommand pc = new PreparedCommand(cmdId);
		pc.setSource(mc.getName()+argAssignmentList);
		pc.setBinary(b);
		pc.setUsername(username);
		
		return pc;
	}
	
	/**
	 * Builds the argument values args based on the argAssignment (which is basically the user input) 
	 * and on the inheritance assignments
	 * 
	 * The argAssignment is emptied as values are being used so if at the end of the call there are still assignment not used -> invalid argument provided
	 * 
	 * This function is called recursively.
	 * 
	 * @param args
	 * @param argAssignment
	 * @throws ErrorInCommand 
	 */
	private void collectAndCheckArguments(MetaCommand mc, Map<Argument, Value> args, Map<String, String> argAssignment) throws ErrorInCommand {
		//check for each argument that we either have an assignment or a value 
		for(Argument a: mc.getArgumentList()) {
			if(args.containsKey(a)) continue;
			String stringValue;
			
			if(!argAssignment.containsKey(a.getName())) {
				if(a.getInitialValue()==null) {
					throw new ErrorInCommand("No value provided for argument "+a.getName()+" (and the argument has no default value either");
				} else {
					stringValue = a.getInitialValue();
				}
			} else {
				stringValue = argAssignment.remove(a.getName());
			}
			ArgumentType type = a.getArgumentType();
			try {
				Value v = ArgumentTypeProcessor.parseAndCheckRange(type, stringValue);				
				args.put(a,  v);
			} catch (Exception e) {
				throw new ErrorInCommand("Cannot assign value to "+a.getName()+": "+e.getMessage());
			}				
		}
		
		//now, go to the parent
		MetaCommand parent = mc.getBaseMetaCommand();
		if(parent!=null) {
			List<ArgumentAssignment> aaList = mc.getArgumentAssignmentList();
			if(aaList!=null) {
				for(ArgumentAssignment aa:aaList) {
					if(args.containsKey(aa.getArgumentName())) {
						throw new ErrorInCommand("Cannot overwrite the argument "+aa.getArgumentName()+" which is defined in the inheritance assignment list");
					}
					argAssignment.put(aa.getArgumentName(), aa.getArgumentValue());
				}
			}
			collectAndCheckArguments(parent, args, argAssignment);
		}		
	}
	

	public void sendCommand(PreparedCommand pc) {
		log.debug("sendCommand commandSource="+pc.source);
		commandQueueManager.addCommand(pc);
	}

	public Channel getChannel() {
		return channel;
	}
}
