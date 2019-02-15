package org.yamcs.tctm;

import java.util.List;

/**
 * A data link that has multiple sub-links
 * @author nm
 *
 */
public interface AggregatedDataLink extends Link {
    List<Link> getSubLinks();
}
