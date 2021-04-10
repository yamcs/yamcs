package org.yamcs.xtce.util;

import java.util.Iterator;

import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.PathElement;

/**
 * unresolved reference to arguments.
 * <p>
 * in addition to {@link UnresolvedNameReference} this class can resolve to aggregate members
 * 
 * @author nm
 *
 */
public class UnresolvedArgumentReference extends UnresolvedNameReference implements ArgumentReference {
    MetaCommand cmd;
    String argName;
    PathElement[] path;

    public UnresolvedArgumentReference(MetaCommand cmd, String argName, PathElement[] path) {
        super(ArgumentReference.toString(argName, path), Type.ARGUMENT);
        this.cmd = cmd;
        this.argName = argName;
        this.path = path;
    }

    public boolean tryResolve(Argument argument, PathElement[] path) {
        Iterator<ResolvedAction> it = actions.iterator();
        while (it.hasNext()) {
            ResolvedAction ra = it.next();
            boolean b = false;
            if (ra instanceof ArgumentResolvedAction) {
                b = ((ArgumentResolvedAction) ra).resolved(argument, path);
            } else {
                b = ra.resolved(argument);
            }
            if (b) {
                it.remove();
            }
        }
        if (actions.isEmpty()) {
            cf.complete(argument);
            return true;
        } else {
            return false;
        }
    }

    public UnresolvedArgumentReference addResolvedAction(ArgumentResolvedAction action) {
        actions.add(action);
        return this;
    }

    @Override
    public MetaCommand getMetaCommand() {
        return cmd;
    }

    @Override
    public String getArgName() {
        return argName;
    }

    @Override
    public PathElement[] getPath() {
        return path;
    }

}
