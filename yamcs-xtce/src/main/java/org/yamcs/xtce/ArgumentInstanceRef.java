package org.yamcs.xtce;

/**
 * A reference to a command argument or to a member of an argument of type aggregate
 *
 */
public class ArgumentInstanceRef extends ParameterOrArgumentRef {
    private static final long serialVersionUID = 1;
    private Argument argument;

    /**
     * Constructor to be used when the parameter is not yet known.
     * The parameter will have to be set later with setParameter()
     */
    public ArgumentInstanceRef() {
        super();
    }

    public ArgumentInstanceRef(Argument arg) {
        this.argument = arg;
    }

    public ArgumentInstanceRef(Argument arg, PathElement[] path) {
        this.argument = arg;
        this.path = path;
    }

    public ArgumentInstanceRef(Argument arg, boolean useCalibratedValue) {
        this.argument = arg;
        this.useCalibratedValue = useCalibratedValue;
    }

    public void setArgument(Argument arg) {
        this.argument = arg;
    }

    public Argument getArgument() {
        return argument;
    }


    @Override
    public String getName() {
        return argument == null ? null : argument.getName();
    }

    @Override
    public DataType getDataType() {
        return argument == null ? null : argument.getArgumentType();

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (argument != null) {
            sb.append(argument.getName());
        }
        if (path != null) {
            sb.append("/");
            sb.append(PathElement.pathToString(path));
        }
        return sb.toString();
    }

}
