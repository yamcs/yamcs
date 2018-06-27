package org.yamcs.xtce.util;

import org.yamcs.xtce.NameDescription;

/**
 * Reference that is resolved since the beginning - it calls any action immediately.
 * The reason for this class is that we do not want duplicate code paths in the SpreadSheet Loader (or other database loader)
 *   - one path for the case when the named entities are found in the current space system
 *   - one path for the case when they are not found and will be resolved later.
 * 
 */
public class ResolvedNameReference extends NameReference {
    final NameDescription nd;
    public ResolvedNameReference(String ref, Type type, NameDescription nd) {
        super(ref, type);
        this.nd = nd;
    }
    @Override
    public boolean resolved(NameDescription nd) {
        return true;
    }
    
    @Override
    public NameReference addResolvedAction(ResolvedAction action) {
        action.resolved(nd);
        return this;
    }
}
