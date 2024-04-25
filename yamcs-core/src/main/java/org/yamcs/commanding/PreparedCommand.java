package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.Assignment;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.mdb.Mdb;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandAssignment;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueHelper;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Stores command information
 */
public class PreparedCommand {

    private CommandId id;
    private MetaCommand metaCommand;

    // Target stream (may be null, for autoselection)
    private Stream tcStream;

    List<CommandHistoryAttribute> attributes = new ArrayList<>();
    private Map<Argument, ArgumentValue> argAssignment; // Ordered from top entry to bottom entry
    private Set<String> userAssignedArgumentNames;

    // Verifier-specific configuration options (that override the MDB verifier settings)
    private Map<String, VerifierConfig> verifierConfig = new HashMap<>();

    // same as attributes but converted to parameters for usage in verifiers and transmission constraints
    private volatile ParameterValueList cmdParams;

    // column names to use when converting to tuple
    public final static String CNAME_GENTIME = StandardTupleDefinitions.GENTIME_COLUMN;
    public final static String CNAME_SEQNUM = StandardTupleDefinitions.SEQNUM_COLUMN;
    public final static String CNAME_ORIGIN = StandardTupleDefinitions.TC_ORIGIN_COLUMN;
    public final static String CNAME_USERNAME = "username";
    public final static String CNAME_UNPROCESSED_BINARY = "unprocessedBinary";
    public final static String CNAME_BINARY = "binary";
    public final static String CNAME_CMDNAME = "cmdName";
    public final static String CNAME_ASSIGNMENTS = "assignments";
    public final static String CNAME_COMMENT = "comment";
    public final static String CNAME_NO_POSTPROCESSING = "noPostprocessing";
    public final static String CNAME_NO_TRANSMISSION_CONSTRAINTS = "noTransmissionConstraints";
    public final static String CNAME_NO_VERIFIERS = "noVerifiers";
    public final static String CNAME_RAW = "raw";

    private static Set<String> reservedColumns = new HashSet<>();
    static {
        reservedColumns.add(CNAME_GENTIME);
        reservedColumns.add(CNAME_SEQNUM);
        reservedColumns.add(CNAME_ORIGIN);
        reservedColumns.add(CNAME_USERNAME);
        reservedColumns.add(CNAME_UNPROCESSED_BINARY);
        reservedColumns.add(CNAME_BINARY);
        reservedColumns.add(CNAME_CMDNAME);
        reservedColumns.add(CNAME_ASSIGNMENTS);
        reservedColumns.add(CNAME_COMMENT);
        reservedColumns.add(CNAME_NO_POSTPROCESSING);
        reservedColumns.add(CNAME_NO_TRANSMISSION_CONSTRAINTS);
        reservedColumns.add(CNAME_NO_VERIFIERS);
    }

    /**
     * Columns that can't be updated via cmdhist_realtime attributes.
     */
    public static Set<String> protectedColumns = new HashSet<>();
    static {
        protectedColumns.add(CNAME_GENTIME);
        protectedColumns.add(CNAME_SEQNUM);
        protectedColumns.add(CNAME_ORIGIN);
        protectedColumns.add(CNAME_ASSIGNMENTS);
    }

    public PreparedCommand(CommandId id) {
        this.id = id;
    }

    /**
     * Used for testing the uplinkers
     */
    public PreparedCommand(byte[] binary) {
        setBinary(binary);
    }

    public long getGenerationTime() {
        return id.getGenerationTime();
    }

    public void setComment(String comment) {
        setAttribute(CNAME_COMMENT, comment);
    }

    public String getComment() {
        return getStringAttribute(CNAME_COMMENT);
    }

    /**
     * Specify the target TC stream. If unset, a stream is automatically selected.
     */
    public void setTcStream(Stream tcStream) {
        this.tcStream = tcStream;
    }

    public Stream getTcStream() {
        return tcStream;
    }

    public String getCmdName() {
        return id.getCommandName();
    }

    public Boolean getBooleanAttribute(String attrname) {
        CommandHistoryAttribute a = getAttribute(attrname);
        if (a != null) {
            Value v = ValueUtility.fromGpb(a.getValue());
            if (v.getType() == Type.BOOLEAN) {
                return v.getBooleanValue();
            }
        }
        return null;
    }

    public String getStringAttribute(String attrname) {
        CommandHistoryAttribute a = getAttribute(attrname);
        if (a != null) {
            Value v = ValueUtility.fromGpb(a.getValue());
            if (v.getType() == Type.STRING) {
                return v.getStringValue();
            }
        }
        return null;
    }

    public byte[] getBinaryAttribute(String attrname) {
        CommandHistoryAttribute a = getAttribute(attrname);
        if (a != null) {
            Value v = ValueUtility.fromGpb(a.getValue());
            if (v.getType() == Type.BINARY) {
                return v.getBinaryValue();
            }
        }
        return null;
    }

    public String getId() {
        return id.getGenerationTime() + "-" + id.getOrigin() + "-" + id.getSequenceNumber();
    }

    /**
     * String useful for logging. Contains command name and sequence number
     */
    public String getLoggingId() {
        return id.getCommandName() + "-" + id.getSequenceNumber();
    }

    public String getOrigin() {
        return id.getOrigin();
    }

    public int getSequenceNumber() {
        return id.getSequenceNumber();
    }

    public String getCommandName() {
        return id.getCommandName();
    }

    public CommandId getCommandId() {
        return id;
    }

    static public CommandId getCommandId(Tuple t) {
        CommandId cmdId = CommandId.newBuilder()
                .setGenerationTime((Long) t.getColumn(CNAME_GENTIME))
                .setOrigin((String) t.getColumn(CNAME_ORIGIN))
                .setSequenceNumber((Integer) t.getColumn(CNAME_SEQNUM))
                .setCommandName((String) t.getColumn(CNAME_CMDNAME))
                .build();
        return cmdId;
    }

    public Tuple toTuple() {
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(id.getGenerationTime());
        al.add(id.getOrigin());
        al.add(id.getSequenceNumber());
        al.add(id.getCommandName());

        for (CommandHistoryAttribute a : attributes) {
            td.addColumn(a.getName(), ValueUtility.getYarchType(a.getValue().getType()));
            al.add(ValueUtility.getYarchValue(a.getValue()));
        }

        AssignmentInfo.Builder assignmentb = AssignmentInfo.newBuilder();
        if (getArgAssignment() != null) {
            for (Entry<Argument, ArgumentValue> entry : getArgAssignment().entrySet()) {
                assignmentb.addAssignment(Assignment.newBuilder()
                        .setName(entry.getKey().getName())
                        .setValue(ValueUtility.toGbp(entry.getValue().getEngValue()))
                        .setUserInput(userAssignedArgumentNames.contains(entry.getKey().getName()))
                        .build());
            }
        }
        td.addColumn(CNAME_ASSIGNMENTS, DataType.protobuf("org.yamcs.cmdhistory.protobuf.Cmdhistory$AssignmentInfo"));
        al.add(assignmentb.build());

        return new Tuple(td, al.toArray());
    }

    public List<CommandAssignment> getAssignments() {
        List<CommandAssignment> assignments = new ArrayList<>();
        if (getArgAssignment() != null) {
            for (Entry<Argument, ArgumentValue> entry : getArgAssignment().entrySet()) {
                assignments.add(CommandAssignment.newBuilder()
                        .setName(entry.getKey().getName())
                        .setValue(ValueUtility.toGbp(entry.getValue().getEngValue()))
                        .setUserInput(userAssignedArgumentNames.contains(entry.getKey().getName()))
                        .build());
            }
        }
        return assignments;
    }

    public List<CommandHistoryAttribute> getAttributes() {
        return attributes;
    }

    public ParameterValueList getAttributesAsParameters(Mdb mdb) {
        if (cmdParams != null) {
            return cmdParams;
        }
        ParameterValueList pvlist = new ParameterValueList();

        for (CommandHistoryAttribute cha : attributes) {
            String fqn = Mdb.YAMCS_CMD_SPACESYSTEM_NAME + "/" + cha.getName();
            Parameter p = mdb.getParameter(fqn);

            if (p == null) {
                // if it was required in the algorithm, it would be already in the system parameter db
                continue;
            }

            ParameterValue pv = new ParameterValue(p);
            pv.setEngValue(ValueUtility.fromGpb(cha.getValue()));
            pvlist.add(pv);
        }
        cmdParams = pvlist;
        return cmdParams;
    }

    public static PreparedCommand fromTuple(Tuple t, Mdb mdb) {
        CommandId cmdId = getCommandId(t);
        PreparedCommand pc = new PreparedCommand(cmdId);
        pc.setMetaCommand(mdb.getMetaCommand(cmdId.getCommandName()));

        for (int i = 0; i < t.size(); i++) {
            ColumnDefinition cd = t.getColumnDefinition(i);
            String name = cd.getName();
            if (isProtectedColumn(name)) {
                continue;
            }
            Value v = ValueUtility.getColumnValue(cd, t.getColumn(i));
            CommandHistoryAttribute a = CommandHistoryAttribute.newBuilder().setName(name)
                    .setValue(ValueUtility.toGbp(v)).build();
            pc.attributes.add(a);
        }

        AssignmentInfo assignments = (AssignmentInfo) t.getColumn(CNAME_ASSIGNMENTS);
        if (assignments != null) {
            pc.argAssignment = new LinkedHashMap<>();
            for (Assignment assignment : assignments.getAssignmentList()) {
                Argument arg = findArgument(pc.getMetaCommand(), assignment.getName());
                Value v = ValueUtility.fromGpb(assignment.getValue());
                ArgumentValue argv = new ArgumentValue(arg);
                argv.setEngValue(v);
                pc.argAssignment.put(arg, argv);
            }
        }
        return pc;
    }

    private static Argument findArgument(MetaCommand mc, String name) {
        Argument arg = mc.getArgument(name);
        if (arg == null && mc.getBaseMetaCommand() != null) {
            arg = findArgument(mc.getBaseMetaCommand(), name);
        }
        return arg;
    }

    public static PreparedCommand fromCommandHistoryEntry(CommandHistoryEntry che) {
        CommandId cmdId = che.getCommandId();
        PreparedCommand pc = new PreparedCommand(cmdId);

        pc.attributes = che.getAttrList();

        return pc;
    }

    public void addStringAttribute(String name, String value) {
        CommandHistoryAttribute a = CommandHistoryAttribute.newBuilder().setName(name)
                .setValue(ValueHelper.newValue(value)).build();
        attributes.add(a);
    }

    public void addAttribute(CommandHistoryAttribute cha) {
        String name = cha.getName();
        if (isProtectedColumn(name)) {
            throw new IllegalArgumentException("Cannot use '" + name + "' as a command attribute");
        }
        attributes.add(cha);
    }

    public byte[] getBinary() {
        return getBinaryAttribute(CNAME_BINARY);
    }

    public void setBinary(byte[] b) {
        setAttribute(CNAME_BINARY, b);
    }

    public byte[] getUnprocessedBinary() {
        return getBinaryAttribute(CNAME_UNPROCESSED_BINARY);
    }

    public void setUnprocessedBinary(byte[] b) {
        setAttribute(CNAME_UNPROCESSED_BINARY, b);
    }

    public boolean isRaw() {
        Boolean attr = getBooleanAttribute(CNAME_RAW);
        return attr != null ? attr.booleanValue() : false;
    }

    public void setRaw(boolean raw) {
        setAttribute(CNAME_RAW, raw);
    }

    public String getUsername() {
        CommandHistoryAttribute cha = getAttribute(CNAME_USERNAME);
        return cha != null ? cha.getValue().getStringValue() : null;
    }

    public void setUsername(String username) {
        setAttribute(CNAME_USERNAME, username);
    }

    public MetaCommand getMetaCommand() {
        return metaCommand;
    }

    public void setMetaCommand(MetaCommand cmd) {
        this.metaCommand = cmd;
    }

    public void setArgAssignment(Map<Argument, ArgumentValue> argAssignment, Set<String> userAssignedArgumentNames) {
        this.argAssignment = argAssignment;
        this.userAssignedArgumentNames = userAssignedArgumentNames;
    }

    public ArgumentValue getArgAssignment(Argument arg) {
        return argAssignment.get(arg);
    }

    public Map<Argument, ArgumentValue> getArgAssignment() {
        return argAssignment;
    }

    public void disableTransmissionConstraints(boolean b) {
        setAttribute(CNAME_NO_TRANSMISSION_CONSTRAINTS, b);
    }

    /**
     * @return true if the transmission constraints have to be disabled for this command
     */
    public boolean disableTransmissionConstraints() {
        Boolean attr = getBooleanAttribute(CNAME_NO_TRANSMISSION_CONSTRAINTS);
        return attr != null ? attr.booleanValue() : false;
    }

    /**
     * @return true if the command verifiers have to be disabled for this command
     */
    public boolean disableCommandVerifiers() {
        Boolean attr = getBooleanAttribute(CNAME_NO_VERIFIERS);
        return attr != null ? attr.booleanValue() : false;
    }

    public void disableCommandVerifiers(boolean b) {
        setAttribute(CNAME_NO_VERIFIERS, b);
    }

    /**
     * @return true if no post-processing should occur on this command
     */
    public boolean disablePostprocessing() {
        Boolean attr = getBooleanAttribute(CNAME_NO_POSTPROCESSING);
        return attr != null ? attr.booleanValue() : false;
    }

    public void disablePostprocessing(boolean b) {
        setAttribute(CNAME_NO_POSTPROCESSING, b);
    }

    public void addVerifierConfig(String name, VerifierConfig verifierConfig) {
        this.verifierConfig.put(name, verifierConfig);
    }

    /**
     * @return a list of command verifiers options overriding MDB settings.
     */
    public Map<String, VerifierConfig> getVerifierOverride() {
        return verifierConfig;
    }

    public CommandHistoryAttribute getAttribute(String name) {
        for (CommandHistoryAttribute a : attributes) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    public void setAttribute(String name, Object value) {
        int i;
        for (i = 0; i < attributes.size(); i++) {
            CommandHistoryAttribute attr = attributes.get(i);
            if (name.equals(attr.getName())) {
                break;
            }
        }
        CommandHistoryAttribute.Builder attr = CommandHistoryAttribute.newBuilder()
                .setName(name);
        if (value instanceof String) {
            attr.setValue(ValueHelper.newValue((String) value));
        } else if (value instanceof Boolean) {
            attr.setValue(ValueHelper.newValue((Boolean) value));
        } else if (value instanceof byte[]) {
            attr.setValue(ValueHelper.newValue((byte[]) value));
        } else {
            throw new IllegalArgumentException("Unexpected attribute type");
        }
        if (i < attributes.size()) {
            attributes.set(i, attr.build());
        } else {
            attributes.add(attr.build());
        }
    }

    @Override
    public String toString() {
        return "PreparedCommand(" + StringConverter.toString(id) + ")";
    }

    public static boolean isReservedColumn(String columnName) {
        return reservedColumns.contains(columnName);
    }

    public static boolean isProtectedColumn(String columnName) {
        return protectedColumns.contains(columnName);
    }
}
