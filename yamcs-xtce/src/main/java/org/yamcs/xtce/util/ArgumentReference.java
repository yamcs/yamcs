package org.yamcs.xtce.util;

import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.PathElement;

/**
 * Argument references are used in algorithms or match criteria expressions part of transmission constraints or command
 * verifiers. Most references are solved locally, i.e. they refer to arguments directly defined in the command to which
 * the verifier is attached.
 * <p>
 * However sometimes the argument is part of a parent command which is only found when assembling the whole MDB. In
 * those cases, this class is used to store the reference and will be solved at the time of loading the database.
 * <p>
 * Note that the argument reference is different from the other {@link NameReference} because it does not contain an
 * absolute path. The Argument references are local to a specific command and its parent hierarchy, not absolute like
 * for a parameter or container.
 *
 */
public class ArgumentReference extends NameReference {

    @FunctionalInterface
    public interface ArgumentResolvedAction extends ResolvedAction {
        public boolean resolved(Argument parameter, PathElement[] path);

        default void resolved(NameDescription nd) {
            resolved((Argument) nd, null);
        }

    }

    PathElement[] path;
    final MetaCommand metaCmd;

    public ArgumentReference(MetaCommand metaCmd, String argName, PathElement[] path) {
        super(argName, Type.ARGUMENT);
        this.metaCmd = metaCmd;
        this.path = path;
    }

    public ArgumentReference(MetaCommand metaCmd, Argument arg, PathElement[] path) {
        this(metaCmd, arg.getName(), path);
        this.result = arg;
    }

    public void resolved(Argument argument, PathElement[] path) {
        result = argument;

        for (ResolvedAction ra : actions) {
            if (ra instanceof ArgumentResolvedAction) {
                ((ArgumentResolvedAction) ra).resolved(argument, path);
            } else {
                ra.resolved(argument);
            }
        }
        actions.clear();
    }

    public ArgumentReference addResolvedAction(ArgumentResolvedAction action) {
        actions.add(action);
        if (result != null) {
            if (!action.resolved((Argument) result, path)) {
                actions.add(action);
            }
        }

        return this;
    }

    public MetaCommand getMetaCommand() {
        return metaCmd;
    }

    public String getArgName() {
        return ref;
    }

    public PathElement[] getPath() {
        return path;
    }

    static String toString(String argName, PathElement[] path) {
        return path == null ? argName : argName + "." + AggregateTypeUtil.toString(path);
    }

    public static ArgumentReference getReference(MetaCommand metaCmd, String argRef) {
        int pos = argRef.indexOf('.');
        if (pos == -1) {
            pos = argRef.indexOf('/');
        }
        String argName;
        PathElement[] path;
        if (pos == -1) {
            argName = argRef;
            path = null;
        } else {
            argName = argRef.substring(0, pos);
            path = AggregateTypeUtil.parseReference(argRef.substring(pos));
        }
        MetaCommand tmpcmd = metaCmd;
        Argument arg = null;
        while (arg == null && tmpcmd != null) {
            arg = tmpcmd.getArgument(argName);
            tmpcmd = tmpcmd.getBaseMetaCommand();
        }
        if (arg == null || arg.getArgumentType() == null
                || (path != null && !ReferenceFinder.verifyPath(arg.getArgumentType(), path))) {
            return new ArgumentReference(metaCmd, argName, path);
        } else {
            return new ArgumentReference(metaCmd, arg, path);
        }

    }

}
