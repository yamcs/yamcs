package org.yamcs.commanding;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.ErrorInCommand;
import org.yamcs.NoPermissionException;
import org.yamcs.Privilege;
import org.yamcs.management.ManagementService;
import org.yamcs.parser.HlclCommandParser;
import org.yamcs.parser.HlclParsedCommand;
import org.yamcs.parser.HlclParsedParameter;
import org.yamcs.parser.ParseException;
import org.yamcs.parser.TokenMgrError;

import org.yamcs.YamcsException;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
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
    public PreparedCommand buildCommand(String source, String origin, int seqNum) throws ErrorInCommand, NoPermissionException, YamcsException {
        log.debug("commandString="+source);
        CommandId.Builder cmdIdBuilder=CommandId.newBuilder().setGenerationTime(TimeEncoding.currentInstant())
            .setOrigin(origin)
            .setSequenceNumber(seqNum);
            
        return buildCommand(source, cmdIdBuilder);
    }
    
	/**
	 * pc is a command whose source is included.
	 * parse the source populate the binary part and the definition.
	 */
	public PreparedCommand buildCommand(String source, CommandId.Builder cmdIdBuilder) throws ErrorInCommand, NoPermissionException, YamcsException {
		log.debug("commandString="+source);
		
		
		HlclParsedCommand cmd=null;
		cmd=parseCommandString(source);
		//check if we know anything about this command
		TcPacketDefinition def=null;
		MetaCommand mc=null;
		if(cmd.commandName!=null) {
		    mc=xtcedb.getMetaCommand(MdbMappings.MDB_OPSNAME, cmd.commandName);
		} else {
		    mc=xtcedb.getMetaCommand(MdbMappings.MDB_PATHNAME, cmd.pathname);
		}
		if((mc==null) || ((def=mc.getTcPacket())==null)) {
			throw new ErrorInCommand("unknown telecommand "+cmd.commandName, source,1,1);
		}
		
		Privilege priv=Privilege.getInstance();
		if(!priv.hasPrivilege(Privilege.Type.TC, def.getOpsName())) {
			log.warn("Throwing InsufficientPrivileges for user "+priv.getCurrentUser()+" because he doesn't have the privilege to send command "+def.getOpsName());
			throw new NoPermissionException("Sorry, you are not authorized to send this command");
		}
		String opsname = def.getOpsName();
		if(opsname == null) {
		    opsname = "unknown";
		}
		cmdIdBuilder.setCommandName(opsname);
		
		PreparedCommand pc=new PreparedCommand(cmdIdBuilder.build());
        pc.setSource(source);
        
		pc.setApid(def.getApid());
		pc.setSid(def.getSid());
		
		Object[] pvalues=null;
		try {
			pvalues=extractParameters(def, cmd);
		} catch (ErrorInCommand e) {
			e.errorSource=pc.source;
			throw e;
		}
		//length of the packet excluding the ccsds headers
		int paramPartLength=computeTcPacketLength(def, pvalues);
		int headerLength=16;
		int totalPacketLength=headerLength+paramPartLength+2;
		if(totalPacketLength>def.getPhPacketLength()+7) {
			throw new ErrorInCommand("packet too long, maximum size according to the ccsds header definition:"+(def.getPhPacketLength()+7)+", resulting size:"+totalPacketLength,pc.source,0,0);
		}
		//strange enough, CIS sends always the packet of the length defined in the database.
		totalPacketLength=def.getPhPacketLength()+7;
		byte[] buffer=new byte[totalPacketLength];
		ByteBuffer bb=ByteBuffer.wrap(buffer);
	

		def.fillInHeaders(bb, 0, totalPacketLength-7, pc.getGenerationTime());
		fillInBitstreamLayout(def, bb, headerLength);
		try {
			fillInParameters(def, pvalues, bb, headerLength);
		} catch(DecalibrationNotSupportedException e) {
			log.warn("caught "+e+" when filling in parameters. Throwing Yamcs exception");
			throw new YamcsException(e.toString());
		}
		pc.binary=buffer;
		return pc;
	}
	
	private void fillInBitstreamLayout(TcPacketDefinition tcDef, ByteBuffer bb, int headerLength) {
		for(BitstreamLayoutDefinition bld: tcDef.getBitstreamLayoutList()) {
			switch(bld.format) {
			case UNSIGNED:
				int bitOffsetToTheEndOfInt=7-(bld.location+bld.getNumberOfBits()-2)%8;
				int intOffset=headerLength+(bld.location+bld.getNumberOfBits()-2)/8-3;//this is the offset of the int(4 bytes) whose last byte contains the last bit of this bitstream layout
				int x=bb.getInt(intOffset);
				x=x|(bld.unsignedIntegerValue<<bitOffsetToTheEndOfInt);
				bb.putInt(intOffset,x);
				break;
			case BINARY:
				for(int j=0;j<bld.getBinaryValue().length;j++){
					bb.put(headerLength+j+(bld.location-1)/8,bld.getBinaryValue()[j]);
				}
				break;
			}
		}
		
	}


	private void fillInParameters(TcPacketDefinition tcDef, Object[] pvalues, ByteBuffer bb, int headerLength) throws DecalibrationNotSupportedException {
		int bitPos=0;	
		
		for(int i=0;i<pvalues.length;i++){
			TcParameterDefinition pDef=tcDef.getParameter(i);
			switch(tcDef.getLocationSpecificationMode()){
			case ABSOLUTE: 
				bitPos=pDef.location-1;
				break;
			case RELATIVE:
				bitPos=bitPos+pDef.location-1;
				break;
			}
			Object rawValue=pDef.decalibrate(pvalues[i]);
			
			switch (pDef.getRawType()) {
			case BYTE_TYPE:
			case INTEGER_TYPE:
			case UNSIGNED_INTEGER_TYPE:
				int bitOffsetToTheEndOfLong=7-(bitPos+pDef.numberOfBits-1)%8;
				int longOffset=headerLength+(bitPos+pDef.numberOfBits-1)/8-7;//this is the offset of the long(8 bytes) whose last byte contains the last bit of this parameter
				long v=(Long)rawValue;
				long x=bb.getLong(longOffset); //we take the long that ends with the last byte of this parameter
				long mask=-1L>>>(64-pDef.numberOfBits);
				x=x|((v&mask)<<bitOffsetToTheEndOfLong);
				bb.putLong(longOffset,x);
				bitPos+=pDef.numberOfBits;
				break;
			case REAL_TYPE:
				bb.putFloat(headerLength+bitPos/8, (float) (double) (Double) rawValue);
				bitPos+=32;
				break;
			case STATE_CODE_TYPE:
			case BYTE_STRING_TYPE:
			case STRING_TYPE:
				byte[] b=(byte[])rawValue;
				switch (tcDef.getStringLengthFieldSize()) {
				case 8:
					bb.put(headerLength+bitPos/8,(byte)b.length);
					bitPos+=8;
					break;
				case 16:
					bb.putShort(headerLength+bitPos/8,(short) b.length);
					bitPos+=16;
					break;
				case 32:
					bb.putInt(headerLength+bitPos/8,b.length);
					bitPos+=32;
					break;
				case 0:
					break;
				default:
					log.error("String Length Field Size "+tcDef.getStringLengthFieldSize()+" not supported");
				}
				for(int j=0;j<b.length;j++){
					bb.put(headerLength+bitPos/8,b[j]);
					bitPos+=8;
				}
				if(pDef.stringType==TcParameterDefinition.StringTypes.FIXED_LENGTH) {
					//for fixed size strings we fill in 0 the rest
					for(int j=0;j<pDef.getMaxLength()-b.length;j++){
						bb.put(headerLength+bitPos/8,(byte)0);
						bitPos+=8;
					}
				}
				break;
			}
		}
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
	 * make an array that includes all the parameter values copying the passed value or the default ones 
	 *	  when there is no passed one
	 */
	private Object[] extractParameters(TcPacketDefinition tcDef,HlclParsedCommand cmd) throws ErrorInCommand {
		ArrayList<TcParameterDefinition> plistDef=tcDef.getParameterList();
		ArrayList<HlclParsedParameter> plistParsed=cmd.parameterList;
		Object[] pvalues=new Object[plistDef.size()];

		int i=0,startNamed=0;
		if(plistParsed!=null) {
			// the unnamed parameters have to mach exactly the beginning of the command
			while((i < plistParsed.size()) && (plistParsed.get(i).name==null)) {
				CheckTypeAndRangeCompatibility(plistDef.get(i),plistParsed.get(i));
				pvalues[i]=plistParsed.get(i).value;
				i++;
			}
			startNamed=i;

			/* Now try to match all the passed named parameters to parameters from the command definition */
			for(int k=startNamed;k<plistParsed.size();k++) {
				boolean found=false;
				HlclParsedParameter p=plistParsed.get(k);
				for (i=0;i<pvalues.length;i++) {
					if(p.name.toUpperCase().equals(plistDef.get(i).name.toUpperCase())) {
						found=true;
						if(pvalues[i]!=null) {
							throw new ErrorInCommand("Formal parameter "+p.name+" already associated",null,p.nameBeginLine,p.nameBeginColumn);
						}
						CheckTypeAndRangeCompatibility(plistDef.get(i),p);
						pvalues[i]=p.value;
						break;
					}
				}
				if(!found) {
					throw new ErrorInCommand("Unknown formal parameter '"+p.name+"'",null,p.nameBeginLine,p.nameBeginColumn);
				}
			}
		}
		/* and finally traverse the pvalues array and for any parameter that doesn't have a value yet, 
		 *  copy the default one or complain if there is no default
		 */
		for(i=startNamed;i<pvalues.length;i++) {
			if(pvalues[i]!=null) continue;
			if(plistDef.get(i).getDefaultValue()==null) {
				throw new ErrorInCommand("Command parameter incomplete: > > "+plistDef.get(i).name,null,0,0);
			}
			pvalues[i]=plistDef.get(i).getDefaultValue();
		}
		return pvalues;
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
