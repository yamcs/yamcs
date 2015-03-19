package org.yamcs.derivedvalues;

import java.util.Collection;

/**
 * Provides a collection of derived values. 
 * @author nm
 *
 */
public interface DerivedValuesProvider {
    Collection<DerivedValue> getDerivedValues();
}
