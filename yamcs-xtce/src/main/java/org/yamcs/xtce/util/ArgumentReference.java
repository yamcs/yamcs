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
public interface ArgumentReference extends NameReference {
    @FunctionalInterface
    public interface ArgumentResolvedAction extends ResolvedAction {
        public boolean resolved(Argument parameter, PathElement[] path);

        default boolean resolved(NameDescription nd) {
            return resolved((Argument) nd, null);
        }

    }

    public boolean tryResolve(Argument argument, PathElement[] path);

    public ArgumentReference addResolvedAction(ArgumentResolvedAction action);

    public MetaCommand getMetaCommand();

    public String getArgName();

    public PathElement[] getPath();

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
        while (arg == null && tmpcmd !=null) {
            arg = tmpcmd.getArgument(argName);
            tmpcmd = tmpcmd.getBaseMetaCommand();
        }
        if (arg == null || arg.getArgumentType() == null
                || (path != null && !ReferenceFinder.verifyPath(arg.getArgumentType(), path))) {
            UnresolvedArgumentReference ref = new UnresolvedArgumentReference(metaCmd, argName, path);
            return ref;
        } else {
            return new ResolvedArgumentReference(metaCmd, arg, path);
        }

    }

}
