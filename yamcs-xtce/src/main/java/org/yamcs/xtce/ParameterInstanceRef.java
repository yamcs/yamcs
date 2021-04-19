package org.yamcs.xtce;

/**
 * A reference to an instance of a Parameter.
 * <p>
 * Used when the value of a parameter is required for a calculation or as an index value.
 * <p>
 * A positive value for instance is forward in time, a negative value for count is backward in time,
 * a 0 value for count means use the current value of the parameter or the first value in a container.
 * <p>
 * If the parameter is an aggregate or an array, the reference can be made to a member of the aggregate/array or more
 * generally to a path inside the aggregate (if a hierarchy of aggregates/arrays)
 * <p>
 * Thus the reference can be something like:
 * g1/g2/a[1]/g4[a3]/p7
 * 
 * @author nm
 *
 */
public class ParameterInstanceRef extends ParameterOrArgumentRef {
    private static final long serialVersionUID = 1;
    private Parameter parameter;
    private int instance = 0;

    /**
     * Constructor to be used when the parameter is not yet known.
     * The parameter will have to be set later with setParameter()
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
     * A positive value for instance is forward in time, a negative value for count is backward in time,
     * a 0 value for count means use the current value of the parameter or the first value in a container.
     * 
     * @return instance of the parameter that is required
     */
    public int getInstance() {
        return instance;
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
        return sb.toString();
    }
}
