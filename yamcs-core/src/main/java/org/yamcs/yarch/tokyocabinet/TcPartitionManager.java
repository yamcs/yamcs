package org.yamcs.yarch.tokyocabinet;

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
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.PartitionManager.Interval;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TableDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of table partitions. 
 * Types of partitions that are supported:
 *  time          - creates files of shape yyyy/ddd/tblname
 *  time,value    - creates files of shape yyyy/ddd/tblname#value
 *  value         - creates files of shape tblname#value
 * @author nm
 */
public class TcPartitionManager extends PartitionManager {
	final String dataDir;
	
	static Logger log=LoggerFactory.getLogger(TcPartitionManager.class.getName());
	
	public TcPartitionManager(TableDefinition def) {
	    this(def.getName(), def.getPartitioningSpec(), def.getDataDir());
	}

	public TcPartitionManager(String tblName, PartitioningSpec partitioningSpec, String dataDir) {
      super(tblName, partitioningSpec);
      this.dataDir = dataDir;
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
                for(Object o:intv.partitions) {
                    String fn=((intv.dir!=null) ?intv.dir+"/":"")+tblName+"#"+o; 
                    filenames.add(fn);
                }
            }
        }
        return filenames;
    }
    /**
	 * Creates (if not already existing) and returns the partition in which the instant,value (one of them can be invalid) should be written.
	 *  
	 * @return a Partition
	 */
	public synchronized Partition createAndGetPartition(long instant, Object value) throws IOException {
	    if(partitioningSpec.timeColumn!=null) {
	        if((pcache==null) || (pcache.start>instant) || (pcache.end<=instant)) {
	            Entry<Long, Interval>entry=intervals.floorEntry(instant);
	            if((entry!=null) && (instant<entry.getValue().end)) {
	                pcache=entry.getValue();		              
	                if(value!=null) {
	                	Partition p = pcache.partitions.co
	                	pcache.partitions.add(value);		                	
	                }
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
	                if(value!=null) pcache.partitions.add(value);
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
	            if(value!=null) pcache.partitions.add(value);
	        }
	        
	        if(value!=null) {
	            return dataDir+"/"+pcache.dir+"/"+tblName+"#"+valueToPartition(value);
	        } else {
                return dataDir+"/"+pcache.dir+"/"+tblName;
	        }
	    } else { //partitioning based on values only
	        pcache.partitions.add(value);
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
	    long end;
	    
	    if(intv==null) {
	        cal.add(Calendar.DAY_OF_YEAR, 1);
	        end=TimeEncoding.fromCalendar(cal);
	        intv=new Interval(start, end);
	        intervals.put(start, intv);
	   } else {
		   end=intv.end;
	   }
	   intv.dir=dir;
	   Partition p=new TcPartition(start, end, o, dir);
	   if(o!=null) intv.partitions.add(p);
	}
	

	
}

