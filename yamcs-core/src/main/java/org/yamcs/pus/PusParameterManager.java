package org.yamcs.pus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.AbstractProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.http.ForbiddenException;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.PartialParameterValue;
import org.yamcs.parameter.SoftwareParameterManager;
import org.yamcs.parameter.Value;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ValueEnumeration;

/// Makes the on-board parameters managed by the PUS ST\[20] parameter management service editable from Yamcs, in the
/// same way as the other externally managed parameters. Editing the value of the parameter (e.g. via the web UI edit
/// button) sends a TC\[20,3] "set parameter values" telecommand.
///
/// An on-board parameter is defined in the MDB as:
/// - having the same data source as the one configured for the PusParameterManager service (default `EXTERNAL1`)
/// - having an alias in the configured namespace (default `PUS20_Parameter_ID`)
///     - the alias must be the label of the ST\[20] parameter ID in the configured `parameterIdMapping` enum
///     - this is required because TM\[20,2] and TC\[20,3] take parameter IDs
///     - the `parameterIdMapping` defines the conversion between parameter ID and name/label
///
///  The value size in TC\[20,3] is deduced from the data encoding of each parameter (i.e., from the MDB).
///
/// The values of the parameters are not changed by the edit; they are updated by the TM\[20,2] parameter value reports
/// received as a result of TC\[20,1] "report parameter values" commands. If the option `reportAfterSet` is enabled (the
/// default), such a TC\[20,1] for the updated parameters is sent automatically after the TC\[20,3]. The command is the
/// MDB command `reportCommand`, which has to have the arguments `num_parameters` (the number of parameters) and
/// `parameter_ids` (an array of parameter ID enumeration labels).
///
/// The command sent is the MDB command `setCommand`, which has to have a single binary argument `instructions`,
/// containing the application data: the number of instructions N, followed by N x (parameter ID, value). All the values
/// of one update are sent in one command.
///
/// Limitation: only parameters without calibration and with integer, float, enumerated or boolean types are supported.
public class PusParameterManager extends AbstractProcessorService implements SoftwareParameterManager {
    static final String ARG_INSTRUCTIONS = "instructions";
    static final String ARG_NUM_PARAMETERS = "num_parameters";
    static final String ARG_PARAMETER_IDS = "parameter_ids";

    private String aliasNamespace;
    private String setCommand;
    private String reportCommand;
    private boolean reportAfterSet;
    private MetaCommand reportMetaCommand;
    private EnumeratedParameterType parameterIdType;
    private int parameterIdBits;
    private int numberOfInstructionsBits;
    private MetaCommand setMetaCommand;

    private static void putUnsigned(ByteBuffer bb, long v, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) {
            bb.put((byte) (v >>> (8 * i)));
        }
    }

    static byte[] encodeValue(Parameter p, Value engValue) {
        ParameterType ptype = p.getParameterType();
        if (ptype == null) {
            throw new IllegalArgumentException(p.getQualifiedName() + " has no type");
        }
        if (!(ptype instanceof org.yamcs.xtce.BaseDataType)) {
            throw new IllegalArgumentException("Cannot set " + p.getQualifiedName() + ": unsupported type " + ptype);
        }
        DataEncoding de = ((org.yamcs.xtce.BaseDataType) ptype).getEncoding();
        if (de == null) {
            throw new IllegalArgumentException(p.getQualifiedName() + " has no data encoding");
        }
        if (de.getDefaultCalibrator() != null
                || (de.getContextCalibratorList() != null && !de.getContextCalibratorList().isEmpty())) {
            throw new IllegalArgumentException(
                    "Cannot set " + p.getQualifiedName() + ": parameters with calibration are not supported");
        }

        if (ptype instanceof EnumeratedParameterType) {
            String label = engValue.getStringValue();
            ValueEnumeration ve = ((EnumeratedParameterType) ptype).enumValue(label);
            if (ve == null) {
                throw new IllegalArgumentException(
                        "Invalid label '" + label + "' for " + p.getQualifiedName());
            }
            return encodeInteger(p, de, ve.getValue());
        } else if (ptype instanceof BooleanParameterType) {
            return encodeInteger(p, de, engValue.getBooleanValue() ? 1 : 0);
        } else if (de instanceof IntegerDataEncoding) {
            return encodeInteger(p, de, toLong(p, engValue));
        } else if (de instanceof FloatDataEncoding) {
            return encodeFloat(p, (FloatDataEncoding) de, toDouble(p, engValue));
        } else {
            throw new IllegalArgumentException(
                    "Cannot set " + p.getQualifiedName() + ": unsupported encoding " + de);
        }
    }

    private static long toLong(Parameter p, Value v) {
        return switch (v.getType()) {
            case SINT32 -> v.getSint32Value();
            case SINT64 -> v.getSint64Value();
            case UINT32 -> v.getUint32Value() & 0xFFFFFFFFL;
            case UINT64 -> v.getUint64Value();
            case BOOLEAN -> v.getBooleanValue() ? 1 : 0;
            default -> throw new IllegalArgumentException(
                    "Invalid value type " + v.getType() + " for " + p.getQualifiedName());
        };
    }

    private static double toDouble(Parameter p, Value v) {
        return switch (v.getType()) {
            case FLOAT -> v.getFloatValue();
            case DOUBLE -> v.getDoubleValue();
            default -> toLong(p, v);
        };
    }

    private static byte[] encodeInteger(Parameter p, DataEncoding de, long v) {
        if (!(de instanceof IntegerDataEncoding ide)) {
            throw new IllegalArgumentException("Cannot set " + p.getQualifiedName() + ": unsupported encoding " + de);
        }
        int bits = ide.getSizeInBits();
        if (bits <= 0 || bits > 64 || (bits & 7) != 0) {
            throw new IllegalArgumentException(
                    "Cannot set " + p.getQualifiedName() + ": unsupported size in bits " + bits);
        }
        switch (ide.getEncoding()) {
            case UNSIGNED:
                if (bits < 64 && (v < 0 || v >= (1L << bits))) {
                    throw new IllegalArgumentException("Value " + v + " out of range for " + p.getQualifiedName());
                }
                break;
            case TWOS_COMPLEMENT:
                if (bits < 64 && (v < -(1L << (bits - 1)) || v >= (1L << (bits - 1)))) {
                    throw new IllegalArgumentException("Value " + v + " out of range for " + p.getQualifiedName());
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Cannot set " + p.getQualifiedName() + ": unsupported integer encoding " + ide.getEncoding());
        }
        int n = bits / 8;
        byte[] b = new byte[n];
        boolean little = de.getByteOrder() == ByteOrder.LITTLE_ENDIAN;
        for (int i = 0; i < n; i++) {
            int shift = 8 * (little ? i : n - 1 - i);
            b[i] = (byte) (v >>> shift);
        }
        return b;
    }

    private static byte[] encodeFloat(Parameter p, FloatDataEncoding fde, double v) {
        if (fde.getEncoding() != FloatDataEncoding.Encoding.IEEE754_1985) {
            throw new IllegalArgumentException(
                    "Cannot set " + p.getQualifiedName() + ": unsupported float encoding " + fde.getEncoding());
        }
        ByteBuffer bb;
        switch (fde.getSizeInBits()) {
            case 32:
                bb = ByteBuffer.allocate(4).order(fde.getByteOrder());
                bb.putFloat((float) v);
                break;
            case 64:
                bb = ByteBuffer.allocate(8).order(fde.getByteOrder());
                bb.putDouble(v);
                break;
            default:
                throw new IllegalArgumentException(
                        "Cannot set " + p.getQualifiedName() + ": unsupported float size " + fde.getSizeInBits());
        }
        return bb.array();
    }

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("dataSource", OptionType.STRING).withDefault(DataSource.EXTERNAL1.name())
                .withDescription("The data source of the parameters managed by this service. Has to be one "
                        + "that is writable from the web interface: EXTERNAL1, EXTERNAL2 or EXTERNAL3");
        spec.addOption("aliasNamespace", OptionType.STRING).withDefault("PUS20_Parameter_ID")
                .withDescription("The alias namespace of the parameters containing the ST[20] parameter ID");
        spec.addOption("parameterIdMapping", OptionType.STRING).withDefault("/PUS20/parameter_id")
                .withDescription("The enumerated parameter that gives the names (the labels, also used as alias of "
                        + "the on-board parameters) and the values of the ST[20] parameter IDs");
        spec.addOption("setCommand", OptionType.STRING).withDefault("/PUS20/SET_PARAMETER_VALUES")
                .withDescription("The fully qualified name of the TC[20,3] command, having one binary argument "
                        + ARG_INSTRUCTIONS);
        spec.addOption("reportAfterSet", OptionType.BOOLEAN).withDefault(true)
                .withDescription("Send a TC[20,1] to report the values of the updated parameters after sending "
                        + "the TC[20,3], such that the new values are shown right away");
        spec.addOption("reportCommand", OptionType.STRING).withDefault("/PUS20/REPORT_PARAMETER_VALUES")
                .withDescription("The fully qualified name of the TC[20,1] command, having the arguments "
                        + ARG_NUM_PARAMETERS + " and " + ARG_PARAMETER_IDS + ". Only used if reportAfterSet is true");
        spec.addOption("parameterIdBits", OptionType.INTEGER).withDefault(8)
                .withDescription("Size in bits of the parameter ID in TC[20,3]");
        spec.addOption("numberOfInstructionsBits", OptionType.INTEGER).withDefault(8)
                .withDescription("Size in bits of the number of instructions (N) in TC[20,3]");
        return spec;
    }

    @Override
    public void init(Processor processor, YConfiguration config, Object spec) {
        super.init(processor, config, spec);
        try {
            config = getSpec().validate(config);
        } catch (ValidationException e) {
            throw new ConfigurationException(e);
        }
        DataSource dataSource;
        try {
            dataSource = DataSource.valueOf(config.getString("dataSource"));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid dataSource " + config.getString("dataSource"));
        }
        aliasNamespace = config.getString("aliasNamespace");
        setCommand = config.getString("setCommand");
        parameterIdBits = config.getInt("parameterIdBits");
        numberOfInstructionsBits = config.getInt("numberOfInstructionsBits");
        for (int bits : new int[]{parameterIdBits, numberOfInstructionsBits}) {
            if (bits <= 0 || bits > 64 || (bits & 7) != 0) {
                throw new ConfigurationException("Sizes have to be a multiple of 8 between 8 and 64: " + bits);
            }
        }

        Mdb mdb = MdbFactory.getInstance(processor.getInstance());
        String idParam = config.getString("parameterIdMapping");
        Parameter idp = mdb.getParameter(idParam);
        if (idp == null) {
            throw new ConfigurationException("Parameter " + idParam + " not found in the MDB");
        }
        if (!(idp.getParameterType() instanceof EnumeratedParameterType)) {
            throw new ConfigurationException(idParam + " is not an enumerated parameter");
        }
        parameterIdType = (EnumeratedParameterType) idp.getParameterType();
        setMetaCommand = mdb.getMetaCommand(setCommand);
        if (setMetaCommand == null) {
            throw new ConfigurationException("Command " + setCommand + " not found in the MDB");
        }
        reportAfterSet = config.getBoolean("reportAfterSet");
        if (reportAfterSet) {
            reportCommand = config.getString("reportCommand");
            reportMetaCommand = mdb.getMetaCommand(reportCommand);
            if (reportMetaCommand == null) {
                throw new ConfigurationException("Command " + reportCommand + " not found in the MDB");
            }
        }
        processor.getParameterProcessorManager().addSoftwareParameterManager(dataSource, this);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    /**
     * Update not performed via the API (no user): the command is sent as the system user.
     */
    @Override
    public void updateParameters(List<ParameterValue> pvals) {
        updateParameters(YamcsServer.getServer().getSecurityStore().getSystemUser(), getName(), pvals);
    }

    /**
     * When updating an onboard parameter, send a PUS TC[20,3] as if the user had sent the command via the regular
     * commanding flow. Hence, we need to include the user (to check the privileges) and the origin (to construct the
     * command).
     */
    @Override
    public void updateParameters(User user, String origin, List<ParameterValue> pvals) {
        // throws IllegalArgumentException (translated by the API into a bad request) if the values cannot be encoded
        byte[] instructions = encodeInstructions(pvals);

        // Check that the user can normally send that command
        if (!user.hasObjectPrivilege(ObjectPrivilegeType.Command, setMetaCommand.getQualifiedName())) {
            throw new ForbiddenException("No " + ObjectPrivilegeType.Command + " authorization for '"
                    + setMetaCommand.getQualifiedName() + "'");
        }
        log.info("Sending {} with {} parameter value(s) on behalf of {}", setCommand, pvals.size(),
                user.getName());

        // Construct and send the command
        try {
            var cmdManager = processor.getCommandingManager();
            var pc = cmdManager.buildCommand(setMetaCommand, Map.of(ARG_INSTRUCTIONS, instructions), origin, 0, user);
            cmdManager.sendCommand(user, pc);
        } catch (YamcsException e) {
            throw new IllegalArgumentException("Cannot build command " + setCommand + ": " + e.getMessage(), e);
        }

        if (reportAfterSet) {
            requestReport(user, origin, pvals);
        }
    }

    /**
     * Sends a TC[20,1] for the updated parameters. The set has already been sent, so problems are only logged.
     */
    private void requestReport(User user, String origin, List<ParameterValue> pvals) {
        if (!user.hasObjectPrivilege(ObjectPrivilegeType.Command, reportMetaCommand.getQualifiedName())) {
            log.warn("Not sending {}: no {} authorization for user {}", reportCommand, ObjectPrivilegeType.Command,
                    user.getName());
            return;
        }
        try {
            List<String> labels = pvals.stream()
                    .map(ParameterValue::getParameter)
                    .map(p -> p.getAlias(aliasNamespace))
                    .toList();
            var cmdManager = processor.getCommandingManager();
            var pc = cmdManager.buildCommand(reportMetaCommand,
                    Map.of(ARG_NUM_PARAMETERS, labels.size(), ARG_PARAMETER_IDS, labels), origin, 0, user);
            cmdManager.sendCommand(user, pc);
        } catch (YamcsException | IllegalArgumentException e) {
            log.warn("Cannot send {} to report the updated parameters: {}", reportCommand, e.getMessage());
        }
    }

    private String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Encodes the application data of a TC[20,3]: N followed by N x (parameter ID, value).
     */
    byte[] encodeInstructions(List<ParameterValue> pvals) {
        if (pvals.isEmpty()) {
            throw new IllegalArgumentException("No parameter value to set");
        }
        // last value wins if a parameter appears several times
        var ordered = new LinkedHashMap<Long, byte[]>();
        for (ParameterValue pv : pvals) {
            Parameter p = pv.getParameter();
            if (pv instanceof PartialParameterValue) {
                throw new IllegalArgumentException(
                        "Cannot set a member of " + p.getQualifiedName() + ", only whole parameters");
            }
            long id = getParameterId(p);
            ordered.remove(id);
            ordered.put(id, encodeValue(p, pv.getEngValue()));
        }

        long maxN = numberOfInstructionsBits == 64 ? Long.MAX_VALUE : (1L << numberOfInstructionsBits) - 1;
        if (ordered.size() > maxN) {
            throw new IllegalArgumentException("Too many parameters in one update: " + ordered.size());
        }

        int size = numberOfInstructionsBits / 8;
        for (byte[] v : ordered.values()) {
            size += parameterIdBits / 8 + v.length;
        }
        ByteBuffer bb = ByteBuffer.allocate(size);
        putUnsigned(bb, ordered.size(), numberOfInstructionsBits / 8);
        for (var e : ordered.entrySet()) {
            putUnsigned(bb, e.getKey(), parameterIdBits / 8);
            bb.put(e.getValue());
        }
        return bb.array();
    }

    // Get the PUS ST[20] ID associated with the parameter
    long getParameterId(Parameter p) {
        String label = p.getAlias(aliasNamespace);
        if (label == null) {
            throw new IllegalArgumentException(
                    p.getQualifiedName() + " is not an on-board parameter: it has no alias in the namespace "
                            + aliasNamespace);
        }
        ValueEnumeration ve = parameterIdType.enumValue(label);
        if (ve == null) {
            throw new IllegalArgumentException(
                    "Unknown parameter ID '" + label + "' for " + p.getQualifiedName());
        }
        long id = ve.getValue();
        if (id < 0 || (parameterIdBits < 64 && id >= (1L << parameterIdBits))) {
            throw new IllegalArgumentException(
                    "Parameter ID " + id + " of " + p.getQualifiedName() + " does not fit in " + parameterIdBits
                            + " bits");
        }
        return id;
    }

    /**
     * Used by tests that cannot create a full processor
     */
    void configure(String aliasNamespace, EnumeratedParameterType parameterIdType, int parameterIdBits,
                   int numberOfInstructionsBits) {
        this.aliasNamespace = aliasNamespace;
        this.parameterIdType = parameterIdType;
        this.parameterIdBits = parameterIdBits;
        this.numberOfInstructionsBits = numberOfInstructionsBits;
    }

}