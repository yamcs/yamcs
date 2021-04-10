package org.yamcs.xtce.util;

import java.util.concurrent.CompletableFuture;

import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.PathElement;

/**
 * Reference that is resolved since the beginning - it calls any action immediately.
 * <p>
 * The reason for this class is that we do not want duplicate code paths in the SpreadSheet Loader (or other database
 * loader)
 * <ul>
 * <li>one path for the case when the named entities are found in the current space system</li>
 * <li>one path for the case when they are not found and will be resolved later.</li>
 * </ul>
 * 
 */
public class ResolvedArgumentReference implements ArgumentReference {
    final Argument argument;
    final PathElement[] path;
    final MetaCommand cmd;


    public ResolvedArgumentReference(MetaCommand cmd, Argument argument, PathElement[] path) {
        this.argument = argument;
        this.cmd = cmd;
        this.path = path;
    }
    @Override
    public boolean tryResolve(NameDescription nd) {
        return true;
    }
    
    @Override
    public NameReference addResolvedAction(ResolvedAction action) {
        action.resolved(argument);
        return this;
    }
    @Override
    public boolean isResolved() {
        return true;
    }
    
    @Override
    public CompletableFuture<NameDescription> getResolvedFuture() {
        return CompletableFuture.completedFuture(argument);
    }

    @Override
    public boolean tryResolve(Argument argument, PathElement[] path) {
        return true;
    }

    @Override
    public ArgumentReference addResolvedAction(ArgumentResolvedAction action) {
        action.resolved(argument, path);
        return this;
    }

    @Override
    public MetaCommand getMetaCommand() {
        return cmd;
    }

    @Override
    public String getReference() {
        return ArgumentReference.toString(argument.getName(), path);
    }

    @Override
    public Type getType() {
        return Type.ARGUMENT;
    }

    @Override
    public String getArgName() {
        return argument.getName();
    }

    @Override
    public PathElement[] getPath() {
        return path;
    }
}
