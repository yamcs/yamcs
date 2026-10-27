package org.yamcs.tctm;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;

/**
 * A typed, internal stage of a composed data link.
 * <p>
 * Components are owned by one root {@link Link}. They are not registered as links and do not own Yamcs stream wiring,
 * counters or persisted enablement.
 */
public interface DataLinkComponent {

    /** Returns the valid component arguments, or {@code null} when arguments are not validated. */
    default Spec getSpec() {
        return null;
    }

    /** Initializes this component for its owning link. */
    default void init(String yamcsInstance, String linkName, YConfiguration args) {
    }
}
