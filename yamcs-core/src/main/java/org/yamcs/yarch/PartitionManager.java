package org.yamcs.yarch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TimePartitionSchema.PartitionInfo;

/**
 * Keeps track of partitions. This class is used and by the engines (TokyoCabinets, RocksDB) where the partitioning is kept track inside yamcs.
 * If the StorageEngine has built-in partitioning (e.g. mysql), there is no need for this.
 * 
 * @author nm
 *
 */
public abstract class PartitionManager {
	final protected TableDefinition tableDefinition;
	final protected PartitioningSpec partitioningSpec;



	protected NavigableMap<Long, Interval> intervals=new ConcurrentSkipListMap<Long, Interval>();
	//pcache is a cache of the last interval where data has been inserted
	// in case of value based partition, it is basically the list of all partitions
	Interval pcache;


	public PartitionManager(TableDefinition tableDefinition) {
		this.tableDefinition=tableDefinition;
		this.partitioningSpec=tableDefinition.getPartitioningSpec();
		if(partitioningSpec.type==_type.VALUE) {//pcache never changes in this case
			pcache=new Interval(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT);
			intervals.put(TimeEncoding.MIN_INSTANT, pcache);
		}
	}
	/**
	 * Returns an iterator which at each step gives the list of partition corresponding to a time interval (so when we do a replay those partitions have to be played in parallel).
	 * The iterator returns intervals sorted on time.
	 * 
	 *  
	 * @param partitionValueFilter - return only partitions whose value are in the filter. If null, return all partitions;
	 * @return
	 */	
	Iterator<List<Partition>> iterator(Set<Object> partitionValueFilter) {
		PartitionIterator pi=new PartitionIterator(intervals.entrySet().iterator(), partitionValueFilter);
		return pi;
	}

	/**
	 * Same as above only start from a specific start time
	 * @param start
	 * @param partitionValueFilter values
	 * 
	 */
	public Iterator<List<Partition>> iterator(long start, Set<Object> partitionValueFilter) {
		PartitionIterator pi=new PartitionIterator(intervals.entrySet().iterator(), partitionValueFilter);
		pi.jumpToStart(start);
		return pi;
	}

	/**
	 * Creates (if not already existing) and returns the partition in which the instant,value should be written.
	 * instant can be invalid (in case value only or no partitioning)
	 * value can be null (in case 
	 *  
	 * @return a Partition
	 */
	public synchronized Partition createAndGetPartition(long instant, Object value) throws IOException {
		Partition partition;
		if(partitioningSpec.timeColumn!=null) {
			if((pcache==null) || (pcache.start>instant) || (pcache.getEnd()<=instant)) {
				Entry<Long, Interval>entry=intervals.floorEntry(instant);
				if((entry!=null) && (instant<entry.getValue().getEnd())) {
					pcache=entry.getValue();		               
				} else {//no partition in this interval.		
					PartitionInfo pinfo = partitioningSpec.timePartitioningSchema.getPartitionInfo(instant);
					pcache=new Interval(pinfo.partitionStart, pinfo.partitionEnd);		                
					intervals.put(pcache.start, pcache);
				}
			}			
		} 
		
		partition = pcache.get(value);
		if(partition == null) {
			if(partitioningSpec.timeColumn!=null) {
				PartitionInfo pinfo = partitioningSpec.timePartitioningSchema.getPartitionInfo(instant);
				partition = createPartition(pinfo, value);
			} else {
				partition = createPartition(value);
			}
			pcache.add(value, partition);
		}		
		return partition;
	}
	
	/**
	 * Gets partition where tuple has to be written. Creates the partition if necessary.
	 * @param t
	 * @return
	 * @throws IOException 
	 */
	public synchronized Partition getPartitionForTuple(Tuple t) throws IOException {
		
	//	  if(partitioningSpec==null) return tableDefinition.getDataDir()+"/"+tableDefinition.getName();
		  
		  long time=TimeEncoding.INVALID_INSTANT;
		  Object value=null;
		  if(partitioningSpec.timeColumn!=null) {
			  time =(Long)t.getColumn(partitioningSpec.timeColumn);
		  }
		  if(partitioningSpec.valueColumn!=null) {
			  value=t.getColumn(partitioningSpec.valueColumn);
			  ColumnDefinition cd=tableDefinition.getColumnDefinition(partitioningSpec.valueColumn);
			  if(cd.getType()==DataType.ENUM) {
				  value = tableDefinition.addAndGetEnumValue(partitioningSpec.valueColumn, (String) value);                
			  }
		  }
		  return createAndGetPartition(time, value);
	}
	/**
	 * Create a partition for time (and possible value) based partitioning
	 * @param year
	 * @param doy
	 * @param value
	 * @return
	 * @throws IOException 
	 */
	protected abstract Partition createPartition(PartitionInfo pinfo, Object value) throws IOException;
	
	/**
	 * Create a partition for value based partitioning
	 * @param value
	 * @return
	 * @throws IOException 
	 */
	protected abstract Partition createPartition(Object value) throws IOException;

	
	/**
	 * returns a collection of all existing partitions
	 * @return
	 */
	public Collection<Partition> getPartitions() {
		List<Partition> plist = new ArrayList<Partition>();
		for(Interval interval: intervals.values()) {
			plist.addAll(interval.partitions.values());
		}
		return plist;
	}
	
	class PartitionIterator implements Iterator<List<Partition>> {
		final Iterator<Entry<Long,Interval>> it;
		final Set<Object> partitionValueFilter;
		List<Partition> next;
		long start;
		boolean jumpToStart=false;

		PartitionIterator(Iterator<Entry<Long,Interval>> it, Set<Object> partitionFilter){
			this.it=it;
			this.partitionValueFilter=partitionFilter;
		}

		void jumpToStart(long startInstant) {
			this.start=startInstant;
			jumpToStart=true;
		}


		@Override
		public boolean hasNext() {
			if(next!=null) return true;
			next=new ArrayList<Partition>();

			while(it.hasNext()) {
				Entry<Long,Interval> entry=it.next();
				Interval intv=entry.getValue();
				if(jumpToStart && (intv.getEnd()<=start)) {
					continue;
				} else {
					jumpToStart=false;
				}
				if(partitioningSpec.type==_type.TIME) { 
					next.add(intv.partitions.values().iterator().next());
				} else {
					for(Partition p:intv.partitions.values()) {
						if((partitionValueFilter==null) || (partitionValueFilter.contains(p.getValue()))) {
							next.add(p);
						}
					}
				}
				if(!next.isEmpty()) {
					break;
				}
			}
			if(next.isEmpty()) {
				next=null;
				return false;
			} else {
				return true;
			}
		}

		@Override
		public List<Partition> next() {
			List<Partition> ret=next;
			next=null;
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("cannot remove partitions like this");
		}
	}

	public static class Interval {
		static final Object NON_NULL=new Object(); //we use this as a key in the ConcurrentHashMap in case value is null (i.e. time only partitioning)

		long start;

		private long end;
		Map<Object, Partition> partitions=new ConcurrentHashMap<Object, Partition>();

		public Interval(long start, long end) {
			this.start=start;
			this.end = end;
		}

		public Partition get(Object value) {
			if(value==null) return partitions.get(NON_NULL);
			else return partitions.get(value);
		}

		public void add(Object value, Partition partition) {
			if(value!=null) {
				partitions.put(value, partition);
			} else {
				partitions.put(NON_NULL, partition);
			}
		}
		
		public Map<Object, Partition> getPartitions() {
			return Collections.unmodifiableMap(partitions);
		}
		
		public long getEnd() {
			return end;
		}

		@Override
		public String toString() {
			return "["+TimeEncoding.toString(start)+" - "+TimeEncoding.toString(getEnd())+"] values: "+partitions;
		}		
	}
}
