package org.yamcs.xtce;

import java.io.Serializable;

/**
 * A reference to an instance of a Parameter.
 * Used when the value of a parameter is required for a calculation or as an index value.
 * A positive value for instance is forward in time, a negative value for count is backward in time,
 * a 0 value for count means use the current value of the parameter or the first value in a container.
 * 
 * If the parameter is an aggregate or an array, the reference can be made to a member of the aggregate/arraty or more
 * generally to a path inside the aggregate (if a hierarchy of aggregates/arrays)
 * Thus the reference can be something like:
 * g1/g2/a[1]/g4[a3]/p7
 * 
 * @author nm
 *
 */
public class ParameterInstanceRef implements Serializable {
    private static final long serialVersionUID = 200906191236L;
    private Parameter parameter;

    private boolean useCalibratedValue = true;
    private int instance = 0;
    private PathElement[] path;

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

    public boolean useCalibratedValue() {
        return useCalibratedValue;
    }

    public void setUseCalibratedValue(boolean useCalibratedValue) {
        this.useCalibratedValue = useCalibratedValue;
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

    /**
     * If the parameter is an aggregate or an array (or a nested structure of these), return the path
     * to the referenced member inside the structure.
     * 
     * @return the path to the referenced member of the aggregate or array or null if this reference refers to the
     *         parameter itself
     */
    public PathElement[] getMemberPath() {
        return path;
    }

    public void setMemberPath(PathElement[] path) {
        this.path = path;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(parameter!=null) {
            sb.append(parameter.getQualifiedName());
        }
        if(path!=null) {
            sb.append("/");
            sb.append(PathElement.pathToString(path));
        }
        return sb.toString();
    }

}
