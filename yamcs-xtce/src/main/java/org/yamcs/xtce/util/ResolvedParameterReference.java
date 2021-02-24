package org.yamcs.xtce.util;

import java.util.concurrent.CompletableFuture;

import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
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
public class ResolvedParameterReference extends AbstractNameReference implements ParameterReference {
    final Parameter param;
    PathElement[] path;

    public ResolvedParameterReference(String ref, Parameter param) {
        super(ref, Type.PARAMETER);
        this.param = param;
    }
    @Override
    public boolean tryResolve(NameDescription nd) {
        return true;
    }
    
    @Override
    public NameReference addResolvedAction(ResolvedAction action) {
        action.resolved(param);
        return this;
    }
    @Override
    public boolean isResolved() {
        return true;
    }
    
    @Override
    public CompletableFuture<NameDescription> getResolvedFuture() {
        return CompletableFuture.completedFuture(param);
    }

    @Override
    public boolean tryResolve(Parameter param, PathElement[] path) {
        return true;
    }

    @Override
    public ParameterReference addResolvedAction(ParameterResolvedAction action) {
        action.resolved(param, path);
        return this;
    }
}
