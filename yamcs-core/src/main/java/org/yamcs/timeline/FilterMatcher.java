package org.yamcs.timeline;

import org.yamcs.protobuf.ItemFilter;
import org.yamcs.protobuf.ItemFilter.FilterCriterion;

public abstract class FilterMatcher<T> {
    boolean matches(RetrievalFilter filter, T item) {
        var itemFilters = filter.getItemFilters();

        if (itemFilters == null || itemFilters.isEmpty()) {
            return true;
        }
        
        for (ItemFilter f : itemFilters) {
            if (!match(f, item)) {
                return false;
            }
        }
        return true;
    }

    private boolean match(ItemFilter f, T item) {
        for (var c : f.getCriteriaList()) {
            if (criterionMatch(c, item)) {
                return true;
            }
        }
        return false;
    }

    protected abstract boolean criterionMatch(FilterCriterion criterion, T item);
}
