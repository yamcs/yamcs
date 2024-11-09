package org.yamcs.xtce;

/**
 * A reference to an instance of a Parameter.
 * <p>
 * Used when the value of a parameter is required for a calculation or as an index value.
 * <p>
 * Starting with Yamcs 5.10.4 the {@link #relativeTo} field has been introduced to better qualify what the instance
 * refers to exactly.
 * <p>
 * If the parameter is an aggregate or an array, the reference can be made to a member of the aggregate/array or more
 * generally to a path inside the aggregate (if a hierarchy of aggregates/arrays)
 * <p>
 * Thus the reference can be something like: g1/g2/a[1]/g4[a3]/p7
 *
 */
public class ParameterInstanceRef extends ParameterOrArgumentRef {
    private static final long serialVersionUID = 4;
    private Parameter parameter;
    private int instance = 0;

    public static enum InstanceRelativeTo {
        /**
         * The instance field counts positively from the beginning of the packet.
         * <p>
         * Negative values are not allowed.
         */
        PACKET_START_WITHIN_PACKET,
        /**
         * The instance field counts positively from the beginning of the packet.
         * <p>
         * Negative values means previously received data (with the possibility of missing packets, which may result in
         * retrieving older values than intended)
         *
         */
        PACKET_START_ACROSS_PACKETS,
        /**
         * The instance field counts negatively from the current entry. 0 means the last instance. Strictly positive
         * values are not allowed.
         * <p>
         * The lookup stops in the current packet, values from previous packets are not considered.
         */
        CURRENT_ENTRY_WITHIN_PACKET,
        /**
         * This is the same as CURRENT_ENTRY_WITHIN_PACKET but not restricted to the current packet.
         * <p>
         * The lookup continues to the previous packets (with the possibility of missing packets, which may result in
         * retrieving older values than intended).
         */
        CURRENT_ENTRY_ACROSS_PACKETS,
    }

    InstanceRelativeTo relativeTo = InstanceRelativeTo.CURRENT_ENTRY_WITHIN_PACKET;

    /**
     * Constructor to be used when the parameter is not yet known. The parameter will have to be set later with
     * setParameter()
     */
    public ParameterInstanceRef() {
        super();
    }

    public ParameterInstanceRef(Parameter para) {
        this.parameter = para;
    }

    public ParameterInstanceRef(Parameter para, PathElement[] path) {
        this.parameter = para;
        this.path = path;
    }

    public ParameterInstanceRef(Parameter para, boolean useCalibratedValue) {
        this.parameter = para;
        this.useCalibratedValue = useCalibratedValue;
    }

    public ParameterInstanceRef(boolean useCalibratedValue) {
        this.useCalibratedValue = useCalibratedValue;
    }

    public void setParameter(Parameter para) {
        this.parameter = para;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public void setInstance(int instance) {
        this.instance = instance;
    }

    /**
     * The interpretation of instance depends on the {@link #relativeTo}
     */
    public int getInstance() {
        return instance;
    }

    /**
     * 
     * @return true if the instance can reference values from older packets
     */
    public boolean requireOldValues() {
        return instance <= 0 && (relativeTo == InstanceRelativeTo.CURRENT_ENTRY_ACROSS_PACKETS
                || relativeTo == InstanceRelativeTo.PACKET_START_ACROSS_PACKETS);
    }

    public InstanceRelativeTo getRelativeTo() {
        return relativeTo;
    }

    public void setRelativeTo(InstanceRelativeTo relativeTo) {
        this.relativeTo = relativeTo;
    }

    public DataType getDataType() {
        return parameter == null ? null : parameter.getParameterType();
    }

    @Override
    public String getName() {
        return parameter == null ? null : parameter.getQualifiedName();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (parameter != null) {
            sb.append(parameter.getQualifiedName());
        }
        if (path != null) {
            sb.append("/");
            sb.append(PathElement.pathToString(path));
        }
        if (instance != 0) {
            sb.append("[" + instance + "]");
        }
        sb.append("(relativeTo: "+relativeTo+")");

        return sb.toString();
    }
}
