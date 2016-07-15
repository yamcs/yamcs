package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.parameter.Value;
import org.yamcs.tctm.TcUplinkerAdapter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;


/** 
 * This class is to keep track of a command binary and source included
 * @author nm
 *
 */
public class PreparedCommand {
    private byte[] binary;
    private CommandId id;
    private MetaCommand metaCommand;
    private final UUID uuid; // Used in REST API as an easier single-field ID. Not persisted.
    
    //used when a command has a transmissionConstraint with timeout
    // when the command is ready to go, but is waiting for a transmission constraint, this is set to true
    private boolean pendingTransmissionConstraint;    
     
    // this is the time when the clock starts ticking for fullfilling the transmission constraint
    // -1 means it has not been set yet
    private long transmissionContraintCheckStart = -1;
    
    List<CommandHistoryAttribute> attributes=new ArrayList<>();
    private Map<Argument, Value> argAssignment;

    //column names to use when converting to tuple
    public final static String CNAME_GENTIME = "gentime";
    public final static String CNAME_SEQNUM = "seqNum";
    public final static String CNAME_ORIGIN = "origin";
    public final static String CNAME_USERNAME = "username";
    public final static String CNAME_BINARY = "binary";
    public final static String CNAME_CMDNAME = "cmdName";
    public final static String CNAME_SOURCE = "source";

    public PreparedCommand(CommandId id) {
        this.id=id;
        uuid=UUID.randomUUID();
    }

    /**
     * Used for testing the uplinkers
     * @param binary
     */
    public PreparedCommand(byte[] binary) {
        this.setBinary(binary);
        uuid=UUID.randomUUID();
    }

    public long getGenerationTime() {
        return id.getGenerationTime();
    }

    public void setSource(String source) {
        setStringAttribute(CNAME_SOURCE, source);
    }

    public String getSource() {
        return getStringAttribute(CNAME_SOURCE);
    }

    public String getCmdName() {
        return id.getCommandName();
    }

    public String getStringAttribute(String attrname) {
        CommandHistoryAttribute a=getAttribute(attrname);
        Value v = ValueUtility.fromGpb(a.getValue());
        if((a!=null) && (v.getType()==Type.STRING)) return v.getStringValue();
        return null;
    }

    public CommandHistoryAttribute getAttribute(String name) {
        for(CommandHistoryAttribute a:attributes) {
            if(name.equals(a.getName())) return a;
        }
        return null;
    }

    public CommandId getCommandId() {
        return id;
    }
    
    public UUID getUUID() {
        return uuid;
    }

    static public CommandId getCommandId(Tuple t) {
        CommandId cmdId=CommandId.newBuilder()
                .setGenerationTime((Long)t.getColumn(CNAME_GENTIME))
                .setOrigin((String)t.getColumn(CNAME_ORIGIN))
                .setSequenceNumber((Integer)t.getColumn(CNAME_SEQNUM))
                .setCommandName((String)t.getColumn(CNAME_CMDNAME))
                .build();
        return cmdId;
    }



    public Tuple toTuple() {
        TupleDefinition td=TcUplinkerAdapter.TC_TUPLE_DEFINITION.copy();
        ArrayList<Object> al=new ArrayList<Object>();
        al.add(id.getGenerationTime());
        al.add(id.getOrigin());
        al.add(id.getSequenceNumber());
        al.add(id.getCommandName());
        
        
        if(getBinary()!=null) {
            td.addColumn(CNAME_BINARY, DataType.BINARY);
            al.add(getBinary());
        }
        
        for(CommandHistoryAttribute a:attributes) {
            td.addColumn(a.getName(), ValueUtility.getYarchType(a.getValue().getType()));
            al.add(ValueUtility.getYarchValue(a.getValue()));
        }
        Tuple t =  new Tuple(td, al.toArray());
        return t;
    }
    
    public void setBinary(byte[] b) {
        this.binary =b;
    }

    public String getUsername() {
        CommandHistoryAttribute cha = getAttribute(CNAME_USERNAME);
        if(cha==null) return null;

        return cha.getValue().getStringValue();
    }

    public List<CommandHistoryAttribute> getAttributes() {
        return attributes;
    }

    public static PreparedCommand fromTuple(Tuple t) {
        CommandId cmdId = getCommandId(t);
        PreparedCommand pc=new PreparedCommand(cmdId);
        for(int i=0;i<t.size();i++) {
            ColumnDefinition cd=t.getColumnDefinition(i);
            String name=cd.getName();
            Value v = ValueUtility.getColumnValue(cd, t.getColumn(i));
            if(CNAME_GENTIME.equals(name) || CNAME_ORIGIN.equals(name) || CNAME_SEQNUM.equals(name)) continue;
            CommandHistoryAttribute a=CommandHistoryAttribute.newBuilder()
                    .setName(name)
                    .setValue(ValueUtility.toGbp(v))
                    .build();
            pc.attributes.add(a);
        }
        pc.setBinary((byte[])t.getColumn(CNAME_BINARY));
        
        return pc;
    }
    public static PreparedCommand fromCommandHistoryEntry(CommandHistoryEntry che) {
        CommandId cmdId = che.getCommandId();
        PreparedCommand pc = new PreparedCommand(cmdId);
        
        pc.attributes = che.getAttrList();
        
        return pc;
    }
    
    public CommandHistoryEntry toCommandHistoryEntry() {
        CommandHistoryEntry.Builder cheb = CommandHistoryEntry.newBuilder().setCommandId(id);
        cheb.addAllAttr(attributes);
        return cheb.build();
    }
    
    public void setStringAttribute(String name, String value) {
        int i;
        for(i =0; i<attributes.size(); i++) {
            CommandHistoryAttribute a = attributes.get(i);
            if(name.equals(a.getName())) break;
        }
        CommandHistoryAttribute a=CommandHistoryAttribute.newBuilder()
                .setName(name)
                .setValue(ValueHelper.newValue(value))
                .build();
        if(i<attributes.size()) {
            attributes.set(i, a);
        } else {
            attributes.add(a);
        }
    }

    public void addStringAttribute(String name, String value) {
        CommandHistoryAttribute a=CommandHistoryAttribute.newBuilder()
                .setName(name)
                .setValue(ValueHelper.newValue(value))
                .build();
        attributes.add(a);
    }

    public void addAttribute(CommandHistoryAttribute cha) {
        attributes.add(cha);
    }
    
    public byte[] getBinary() {
        return binary;
    }

    public void setUsername(String username) {
        setStringAttribute(CNAME_USERNAME, username);
    }

    public MetaCommand getMetaCommand() {
        return metaCommand;
    }

    public void setMetaCommand(MetaCommand cmd) {
        this.metaCommand = cmd;
    }

    public boolean isPendingTransmissionConstraints() {
        return pendingTransmissionConstraint;
    }

    public void  setPendingTransmissionConstraints(boolean b) {
        this.pendingTransmissionConstraint = b;
    }

    public long getTransmissionContraintCheckStart() {
        return transmissionContraintCheckStart;
    }

    public void setTransmissionContraintCheckStart(long transmissionContraintCheckStart) {
        this.transmissionContraintCheckStart = transmissionContraintCheckStart;
    }

    public void setArgAssignment(Map<Argument, Value> argAssignment) {
        this.argAssignment = argAssignment;
    }
    
    public Map<Argument, Value> getArgAssignment() {
        return argAssignment;
    }
}
