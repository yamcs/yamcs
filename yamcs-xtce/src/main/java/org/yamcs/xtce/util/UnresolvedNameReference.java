package org.yamcs.xtce.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.xtce.NameDescription;

/**
 * Stores actions related to unresolved references and calls them once the reference has been resolved.
 * 
 * 
 * @author nm
 *
 */
public class UnresolvedNameReference extends NameReference {
    List<ResolvedAction> actions = new ArrayList<>();
    CompletableFuture<NameDescription> cf = new CompletableFuture<NameDescription>();
    
    public UnresolvedNameReference(String ref, Type type) {
        super(ref, type);
    }

    @Override
    public boolean tryResolve(NameDescription nd) {
        Iterator<ResolvedAction> it = actions.iterator();
        while(it.hasNext()) {
            ResolvedAction ra = it.next();
            if(ra.resolved(nd)) {
                it.remove();
            } 
        }
        if(actions.isEmpty()) {
            cf.complete(nd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public NameReference addResolvedAction(ResolvedAction action) {
        actions.add(action);
        return this;
    }
    
    @Override
    public boolean isResolved() {
        return actions.isEmpty();
    }

    @Override
    public CompletableFuture<NameDescription> getResolvedFuture() {
        return cf;
    }
}
