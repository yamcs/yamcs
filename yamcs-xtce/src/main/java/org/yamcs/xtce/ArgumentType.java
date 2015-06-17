package org.yamcs.xtce;

import java.util.List;

/**
 * Interface to be implemented by all the argument types
 * @author nm
 *
 */
public interface ArgumentType {
    /**
     * String which represents the type.
     * This string will be presented to the users of the system.
     * @return
     */
    String getTypeAsString();

    /**
     * 
     * @return the list of units
     */
    public List<UnitType> getUnitSet();
}
