package org.yamcs.yarch;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public interface PartitionManager {
	Iterator<List<String>> iterator(long start, Set<Object> partitionFilter);

	Iterator<List<String>> iterator(Set<Object> partitionFilter);
}
