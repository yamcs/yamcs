package org.yamcs.xtce.util;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SpaceSystem;

/**
 * Stores an incomplete type together with some references which if all resolved, will make the type complete.
 * 
 * 
 * @author nm
 *
 */
public class IncompleteType {
    final DataType.Builder<?> typeBuilder;
    final SpaceSystem spaceSystem;
    private List<NameReference> references;

    /**
     * Creates a new incomplete type together with the SpaceSystem where it should add once it is complete.
     * 
     * @param spaceSystem
     * @param typeBuilder
     */
    public IncompleteType(SpaceSystem spaceSystem, DataType.Builder<?> typeBuilder) {
        this.spaceSystem = spaceSystem;
        this.typeBuilder = typeBuilder;
    }

    public DataType.Builder<?> getTypeBuilder() {
        return typeBuilder;
    }

    /**
     * Schedule the addition of the type to the SpaceSystem after all references are resolved
     * <p>
     * If there is no unresolved references, the type will be immediately added to the SpaceSystem
     */
    public void scheduleCompletion() {
        if (references == null || references.isEmpty()) {
            complete();
            return;
        }
        for (NameReference nr : references) {
            nr.addResolvedAction(nd -> {
                tryComplete();
            });
        }
        ;
    }

    private void tryComplete() {
        for (NameReference nr : references) {
            if (!nr.isResolved()) {
                return;
            }
        }
        complete();
    }

    private void complete() {
        DataType type = typeBuilder.build();
        if (type instanceof ParameterType) {
            spaceSystem.addParameterType((ParameterType) type);
        } else {
            spaceSystem.addArgumentType((ArgumentType) type);
        }
    }

    public void addReference(NameReference ref) {
        if (references == null) {
            references = new ArrayList<>();
        }
        references.add(ref);
    }
}
