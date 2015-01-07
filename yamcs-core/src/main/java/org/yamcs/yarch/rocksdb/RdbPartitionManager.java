package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TableDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of table partitions in rocksdb storage. 
 * Types of partitions that are supported:
 *  time and time,value     - creates directories of shape yyyy/ddd/tblname
 *  value                   - creates directories of shape tblname
 * @author nm
 */
public class RdbPartitionManager implements PartitionManager {
	final String tblName;
	final PartitioningSpec partitioningSpec;
	final String dataDir;
	
	private NavigableMap<Long, Interval> intervals=new ConcurrentSkipListMap<Long, Interval>();
	
	//pcache is a cache of the last interval where data has been inserted
	// in case of value based partition, it is basically the list of all partitions
	Interval pcache;
	
	static Logger log=LoggerFactory.getLogger(RdbPartitionManager.class.getName());
	
	public RdbPartitionManager(TableDefinition def) {
	    this(def.getName(), def.getPartitioningSpec(), def.getDataDir());
	}

	public RdbPartitionManager(String tblName, PartitioningSpec partitioningSpec, String dataDir) {
        this.tblName=tblName;
        this.partitioningSpec=partitioningSpec;
        this.dataDir=dataDir;
        if(partitioningSpec.type==_type.VALUE) {//pcache never changes in this case
            pcache=new Interval(TimeEncoding.MIN_INSTANT, TimeEncoding.MAX_INSTANT);
            pcache.dir=null;
            intervals.put(TimeEncoding.MIN_INSTANT, pcache);
        }
    }
	/**
	 * 
	 * @return partition filenames (relative to the data directory of the table, and without the .tcb extension)
	 */
    public Collection<String> getPartitions() {
        List<String> filenames=new ArrayList<String>();
        for(Entry<Long,Interval> entry:intervals.entrySet()) {
            Interval intv=entry.getValue();
            if(partitioningSpec.type==_type.TIME) {
                filenames.add(intv.dir+"/"+tblName);
            } else {
                for(Object o:entry.getValue().values) {
                    String fn=((intv.dir!=null) ?intv.dir+"/":"")+tblName+"#"+o; 
                    filenames.add(fn);
                }
            }
        }
        return filenames;
    }
    
	/**
	 * Creates (if not already existing) and returns the partition in which the instance,value (one of them can be invalid) should be written.
	 * It creates all directories necessary.
	 * 
	 * @return an absolute filename, without the ".tcb" extension
	 */
	public synchronized String createAndGetPartition(long instant, Object value) throws IOException {
	    if(partitioningSpec.timeColumn!=null) {
	        if((pcache==null) || (pcache.start>instant) || (pcache.end<=instant)) {
	            Entry<Long, Interval>entry=intervals.floorEntry(instant);
	            if((entry!=null) && (instant<entry.getValue().end)) {
	                pcache=entry.getValue();
	                if(value!=null) pcache.values.add(value);
	            } else {//no partition in this interval. Find the start and end and create the directory
	                DateTimeComponents dtc =TimeEncoding.toUtc(instant);
	                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	                cal.clear();
	                cal.set(Calendar.YEAR, dtc.year);
	                cal.set(Calendar.DAY_OF_YEAR, dtc.doy);
	                long dayStartInstant = TimeEncoding.fromCalendar(cal);
	                cal.add(Calendar.DAY_OF_YEAR, 1);
	                long nextDayStartInstant = TimeEncoding.fromCalendar(cal);

	                pcache=new Interval(dayStartInstant, nextDayStartInstant);
	                pcache.dir=String.format("%4d/%03d", dtc.year, dtc.doy);
	                if(value!=null) pcache.values.add(value);
	                File f=new File(dataDir+"/"+pcache.dir);
	                if(!f.exists()) { 
	                    f.mkdirs(); //we don't check the return of mkdirs because maybe another thread creates the directory in the same time
	                    if(!f.exists()) {
	                        log.error("Failed to create directories for "+f);
	                        throw new IOException("failed to create directories for "+f);
	                    }
	                }
	                intervals.put(pcache.start, pcache);
	            }
	        } else { //instant fits into the existing pcache
	            if(value!=null) pcache.values.add(value);
	        }
	        
	        if(value!=null) {
	            return dataDir+"/"+pcache.dir+"/"+tblName+"#"+valueToPartition(value);
	        } else {
                return dataDir+"/"+pcache.dir+"/"+tblName;
	        }
	    } else { //partitioning based on values only
	        pcache.values.add(value);
	        return dataDir+"/"+tblName+"#"+valueToPartition(value);
	    }
	}
	
	
	private String valueToPartition(Object value) {
		if(value.getClass()==Integer.class) {
			return value.toString();
		} else if(value.getClass()==Short.class) {
			return value.toString();
		} else if(value.getClass()==Byte.class) {
			return value.toString();
		} else if(value.getClass()==String.class) {
			return (String)value;
		} else {
			 throw new IllegalArgumentException("partition on values of type "+value.getClass()+" not supported");
		}
	}
	
	private Object partitionToValue(String part, DataType dt) {
		 switch(dt.val) {
		 case INT:
             return Integer.valueOf(part);
		 case SHORT:
		 case ENUM:
             return Short.valueOf(part);
         case BYTE:
             return Byte.valueOf(part);
         case STRING:
        	 return part;
         default:
             throw new IllegalArgumentException("partition on values of type "+dt+" not supported");
		 }
	}
	/**
	 * Reads the partitions from disk
	 */
	public void readPartitions() {
		String[] years=(new File(dataDir)).list();
		for(String y:years) {
			if(y.matches("\\d{4,5}")) {
			    int year=Integer.parseInt(y);
			    if(year>1900)
			        readYear(y,year);
			}
		}
	}

	private void readYear(String dir, int year) {
		String[] days=new File(dataDir+"/"+dir).list();
		Pattern p=Pattern.compile(tblName+"#([\\-\\s\\w]+)\\.tcb");
		for(String d:days) {
		    if(!d.matches("\\d{3,3}")) continue;
		    int day=Integer.parseInt(d);
		    if((day<0)||day>366)continue;
		    String[] files=new File(dataDir+"/"+dir+"/"+d).list();
		    for(String f:files) {
		        if(partitioningSpec.type==_type.TIME) {
		            if(f.equals(tblName+".tcb")) {
		                addPartition(year, day, dir+"/"+d, null);
		            }
		        } else {
		            Matcher m=p.matcher(f);
		            if(m.matches()) {
		                String so=m.group(1);
		                DataType reqType=partitioningSpec.valueColumnType;
		                try {
		                    Object o=partitionToValue(so, reqType);
		                    addPartition(year, day, dir+"/"+d, o);
		                } catch(IllegalArgumentException e) {
		                    log.error("cannot cast string '"+so+"' to type "+reqType+": "+e.getMessage());
		                    continue;
		                }
		            }
		        }
		    }
		}
	}
	
	private void addPartition(int year, int day, String dir, Object o) {
	    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	    cal.clear();
	    cal.set(Calendar.YEAR, year);
	    cal.set(Calendar.DAY_OF_YEAR, day);
	    long start=TimeEncoding.fromCalendar(cal);
	    Interval intv=intervals.get(start);
	    if(intv==null) {
	        cal.add(Calendar.DAY_OF_YEAR, 1);
	        long end=TimeEncoding.fromCalendar(cal);
	        intv=new Interval(start, end);
	        intervals.put(start, intv);
	   }
	   intv.dir=dir;
	   if(o!=null) intv.values.add(o);
	}
	
	public Iterator<List<String>> iterator(Set<Object> partitionFilter) {
	    PartitionIterator pi=new PartitionIterator(intervals.entrySet().iterator(), partitionFilter);
        return pi;
   }
	
	public Iterator<List<String>> iterator(long start, Set<Object> partitionFilter) {
	    PartitionIterator pi=new PartitionIterator(intervals.entrySet().iterator(), partitionFilter);
	     pi.jumpToStart(start);
	     return pi;
	}

	class PartitionIterator implements Iterator<List<String>> {
	    final Iterator<Entry<Long,Interval>> it;
	    final Set<Object> partitionFilter;
	    List<String> next;
	    long start;
	    boolean jumpToStart=false;
	    
	    PartitionIterator(Iterator<Entry<Long,Interval>> it, Set<Object> partitionFilter){
	        this.it=it;
	        this.partitionFilter=partitionFilter;
	    }
	    
	    void jumpToStart(long startInstant) {
	        this.start=startInstant;
	        jumpToStart=true;
	    }
	 
	    
	    @Override
	    public boolean hasNext() {
	        if(next!=null) return true;
	        next=new ArrayList<String>();
	        
	        while(it.hasNext()) {
                Entry<Long,Interval> entry=it.next();
                Interval intv=entry.getValue();
                if(jumpToStart && (intv.end<=start)) {
                    continue;
                } else {
                    jumpToStart=false;
                }
                String base=dataDir+"/"+(intv.dir!=null?intv.dir+"/":"")+tblName;
                if(partitioningSpec.type==_type.TIME) { 
                    next.add(base);
                } else {
                    for(Object o:entry.getValue().values) {
                        if((partitionFilter==null) || (partitionFilter.contains(o))) {
                            next.add(base+"#"+valueToPartition(o));
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
	    public List<String> next() {
	        List<String> ret=next;
	        next=null;
	        return ret;
	    }

	    @Override
	    public void remove() {
	        throw new UnsupportedOperationException("cannot remove partitions like this");
	    }
	}
}

class Interval {
    long start,  end;
    String dir;
    Set<Object> values=Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());
    
    public Interval(long start, long end) {
        this.start=start;
        this.end=end;
    }
    
    @Override
    public String toString() {
        return "["+TimeEncoding.toString(start)+" - "+TimeEncoding.toString(end)+"] dir:"+dir+" values: "+values;
    }
}
