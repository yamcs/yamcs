package org.yamcs.commanding;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.management.ManagementService;
import org.yamcs.parser.HlclCommandParser;
import org.yamcs.parser.HlclParsedCommand;
import org.yamcs.parser.HlclParsedParameter;
import org.yamcs.parser.ParseException;
import org.yamcs.parser.TokenMgrError;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.MetaCommandContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
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
	public PreparedCommand buildCommand(MetaCommand mc, List<ArgumentAssignment> args) throws ErrorInCommand, NoPermissionException, YamcsException {
		log.debug("buildginc command ");
		
		MetaCommandContainer def=null;
		def=mc.getCommandContainer();
		if(def==null) {
			throw new ErrorInCommand("MetaCommand has no container: "+def);
		}
		
		
		TcProcessingContext pcontext = new TcProcessingContext(ByteBuffer.allocate(1000), 0, 0, TimeEncoding.currentInstant());
		addArguments(mc, args, pcontext);
		fillInEntries(mc, pcontext);		
		return null;
	}
	

	/**
	 * convers argument values from String to Value and adds them to the pcontext
	 * @param pcontext
	 * @param args
	 * @throws ErrorInCommand 
	 */
	private void addArguments(MetaCommand mc, List<ArgumentAssignment> args, TcProcessingContext pcontext) throws ErrorInCommand {
		for(ArgumentAssignment aa:args) {
			Argument arg= mc.getArgument(aa.getArgumentName());
			if(arg==null) throw new ErrorInCommand("Command "+mc.getQualifiedName()+" does not have an argument '"+aa.getArgumentName()+"'");
			ArgumentType type = arg.getArgumentType();
			
			Value v = ArgumentTypeProcessor.parse(type, aa.getArgumentValue());
			pcontext.addArgument(arg,  v);
		}
	}
	
	
	private void fillInEntries(MetaCommand tcDef, TcProcessingContext pcontext) {
		MetaCommandContainer container = tcDef.getCommandContainer();
		
		for(SequenceEntry se: container.getEntryList()) {
			if(se instanceof ArgumentEntry) {
				fillInArgumentEntry((ArgumentEntry) se, pcontext);
			} else if (se instanceof FixedValueEntry) {
				fillInFixedValueEntry((FixedValueEntry) se, pcontext);
			}
			
							
		}
	}
	
	private void fillInArgumentEntry(ArgumentEntry argEntry, TcProcessingContext pcontext) {
		Argument arg = argEntry.getArgument();
		Value argValue = pcontext.getArgumentValue(arg);
		if(argValue==null) {
			throw new IllegalStateException("No value for argument "+arg);
		}
		ReferenceLocationType rlt = argEntry.getReferenceLocation();
		switch(rlt) {
		case containerStart: 
			pcontext.bitPosition = argEntry.getLocationInContainerInBits();
			break;
		case previousEntry:
			pcontext.bitPosition += argEntry.getLocationInContainerInBits();
			break;
		}
		
		ArgumentType atype = arg.getArgumentType();
		Value rawValue = ArgumentTypeProcessor.decalibrate(atype, argValue);
		pcontext.deEncoder.encodeRaw(((BaseDataType)atype).getEncoding(), rawValue);		
	}
		
	private void fillInFixedValueEntry(FixedValueEntry fvEntry, TcProcessingContext pcontext) {
		
	}

	private HlclParsedCommand parseCommandString(String commandString) throws ErrorInCommand {
		//first parse the command. CIS puts the entire command on one line, so here we are doing the same :(
		HlclCommandParser parser=new HlclCommandParser(new StringReader(commandString.replace("\n"," ")));
		HlclParsedCommand cmd=null;
		try {
			cmd=parser.CmdString();
		} catch (ParseException e) {
			throw new ErrorInCommand("parse error: " +e.getMessage(),commandString,e.currentToken.beginLine,e.currentToken.endColumn);
		} catch (TokenMgrError e) {
			throw new ErrorInCommand("token manager error: "+e.getMessage(),commandString,e.errorLine,e.errorColumn);
		} catch (NumberFormatException e) {
			throw new ErrorInCommand("Number format exception",commandString,parser.token.beginLine,parser.token.beginColumn);
		}
		return cmd;
	}
	
	
	
	/**
	 * compute the size in bytes of the parameter part by taking the maximum between the 
	 *  bitstream layout definition and the parameter part
	 */
	private int computeTcPacketLength(TcPacketDefinition tcDef, Object[] pvalues) {
		//first we compute the size of the bitstream layout
		int blLength=0;
		for (BitstreamLayoutDefinition bld: tcDef.getBitstreamLayoutList()) {
			int end=0;
			switch (bld.format) {
			case UNSIGNED:
				end=bld.location-1+bld.getNumberOfBits();
				break;
			case BINARY:
				end=bld.location-1+bld.getBinaryValue().length*8;
			}
			if (end>blLength) {
				blLength=end;
			}
		}		
		//now compute the size of the parameters
		int pLength=0;
		for (int i=0;i<pvalues.length;i++) {
			TcParameterDefinition para=tcDef.getParameterList().get(i);
			int end=0;
			switch (para.getRawType()) {
				case BYTE_TYPE:
				case INTEGER_TYPE:
				case UNSIGNED_INTEGER_TYPE:
				case REAL_TYPE:
					switch (tcDef.getLocationSpecificationMode()) {
					case ABSOLUTE:
						end=para.location-1+para.numberOfBits;
						break;
					case RELATIVE:
						end=pLength+para.location-1+para.numberOfBits;
						break;
					}
					break;
				case BYTE_STRING_TYPE:
				case STRING_TYPE:
					int stringLength=0;
					switch(para.stringType) {
					case FIXED_LENGTH:
						stringLength=tcDef.getStringLengthFieldSize()+8*para.getMaxLength();
						break;
					case VARIABLE_LENGTH:
						stringLength=tcDef.getStringLengthFieldSize()+8*((byte [])pvalues[i]).length;
						break;
					}
					switch (tcDef.getLocationSpecificationMode()) {
					case ABSOLUTE:
						end=para.location-1+stringLength;
						break;
					case RELATIVE:
						end=pLength+stringLength;
						break;
					}
					break;
			}
			if(end>pLength) pLength=end;
		}
		int paramSize=1+(((blLength>pLength)?blLength:pLength)-1)/8; //this is some sort of "floor"
		return paramSize;
	}
	
	/**
	 * Checks compatibility in type and range between a parameter definition and a parsed parameter.
	 * Throws an ErrorInCommand exception if they are not compatible.
	 * @throws ErrorInCommand error withouth the commandString filled in
	 */
	private void CheckTypeAndRangeCompatibility(TcParameterDefinition pDef, HlclParsedParameter pParsed) throws ErrorInCommand {
		switch (pDef.getEngType()) {
		case BYTE_TYPE:
		case INTEGER_TYPE:
		case UNSIGNED_INTEGER_TYPE:
			if(pParsed.type!=TcParameterDefinition.SwTypes.INTEGER_TYPE) {
				throw new ErrorInCommand("Incompatible types:\n- expected: "+pDef.getEngType()+"\n- found: "+pParsed.type,null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			if(((Long)pParsed.value	>(Long)pDef.getMaxValue())|| ((Long)pParsed.value<(Long)pDef.getMinValue())) {
				throw new ErrorInCommand("Value violates a constraint",null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			break;
		case REAL_TYPE:
			if(pParsed.type!=pDef.getEngType()) {
				throw new ErrorInCommand("Incompatible types:\n- expected: "+pDef.getEngType()+"\n- found: "+pParsed.type,null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			if(((Double) pParsed.value>(Double)pDef.getMaxValue()) || ((Double) pParsed.value<(Double)pDef.getMinValue())) {
				log.debug("throwing error in command(Value violates a constraint) line:"+pParsed.valueBeginLine+" column:"+pParsed.valueBeginColumn);
				throw new ErrorInCommand("Value violates a constraint",null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			break;
		case BYTE_STRING_TYPE:
		case STRING_TYPE:
			if(pParsed.type!=pDef.getEngType()) {
				throw new ErrorInCommand("Incompatible types:\n- expected: "+pDef.getEngType()+"\n- found: "+pParsed.type,null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			int slen = ((byte[]) pParsed.value).length;
			if(slen > pDef.getMaxLength()) {
				throw new ErrorInCommand("Value violates a constraint (string too long for "+pDef.getName()+", "+slen+">"+pDef.getMaxLength()+")",null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			break;
		case STATE_CODE_TYPE:
			if(pParsed.type!=pDef.getEngType()) {
				throw new ErrorInCommand("Incompatible types:\n- expected: "+pDef.getEngType()+"\n- found: "+pParsed.type,null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
			EnumerationDecalibration dc=(EnumerationDecalibration)pDef.getDecalibration();
			if(dc.getCodes().get((String)pParsed.value)==null) {
				throw new ErrorInCommand("StateCode '"+pParsed.value+"' is not one of "+dc.getCodes(),null,pParsed.valueBeginLine,pParsed.valueBeginColumn);
			}
		}
	}

	/**
	 * sends the command. It uses the passed parameter just for finding back the original command string.
	 * 
	 */
	public void sendCommand(PreparedCommand pc) {
		log.debug("sendCommand commandSource="+pc.source);
		commandQueueManager.addCommand(pc);
	}

	public Channel getChannel() {
		return channel;
	}
}
