package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TableDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of table partitions in rocksdb storage. 
 * Types of partitions that are supported:
 *  time and time,value     - creates directories of shape yyyy/ddd/tblname
 *  value                   - creates directories of shape tblname
 * @author nm
 */
public class RdbPartitionManager extends PartitionManager {
	final String dataDir;
	
	private NavigableMap<Long, Interval> intervals=new ConcurrentSkipListMap<Long, Interval>();
	
	//pcache is a cache of the last interval where data has been inserted
	// in case of value based partition, it is basically the list of all partitions
	Interval pcache;
	
	static Logger log=LoggerFactory.getLogger(RdbPartitionManager.class.getName());
	
	public RdbPartitionManager(TableDefinition tableDefinition) {
	    this(tableDefinition, tableDefinition.getDataDir());
	}

	public RdbPartitionManager(TableDefinition tableDefinition, String dataDir) {
		super(tableDefinition);
        this.dataDir=dataDir;        
    }
	/**
	 * 
	 * @return partition filenames (relative to the data directory of the table, and without the .tcb extension)
	 */
    public Collection<String> getPartitionDirectories() {
    	  Set<String> directories=new HashSet<String>();
          for(Entry<Long,Interval> entry:intervals.entrySet()) {
              Interval intv=entry.getValue();
              for(Partition p:intv.getPartitions().values()) {
              	RdbPartition tcp = (RdbPartition)p;
              	directories.add(tcp.dir);
              }           
          }
          return directories;
    }
    
	/**
	 * In RocksDB value based partitioning is based on RocksDB Column Families. Each ColumnFamily is identified by a byte[]
	 * This method makes the conversion between the value and the byte[]		
	 * @param value
	 * @return
	 */
	public byte[] valueToPartition(Object value) {		
		if(value.getClass()==Integer.class) {
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.putInt((Integer)value);
			return bb.array();
		} else if(value.getClass()==Short.class) {
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.putShort((Short)value);
			return bb.array();			
		} else if(value.getClass()==Byte.class) {
			ByteBuffer bb = ByteBuffer.allocate(1);
			bb.put((Byte)value);
			return bb.array();
		} else if(value.getClass()==String.class) {
			return ((String)value).getBytes();
		} else {
			 throw new IllegalArgumentException("partition on values of type "+value.getClass()+" not supported");
		}
	}
	/**
	 * this is the reverse of the {@link #valueToPartition(Object value)}
	 * @param part
	 * @param dt
	 * @return
	 */
	public Object partitionToValue(byte[] part, DataType dt) {
		 switch(dt.val) {
		 case INT:
			 ByteBuffer bb = ByteBuffer.wrap(part);
			 return bb.getInt();            
		 case SHORT:
		 case ENUM: //intentional fall-through
			 bb = ByteBuffer.wrap(part);
			 return bb.getShort();             
         case BYTE:
        	 bb = ByteBuffer.wrap(part);
			 return bb.get();             
         case STRING:
        	 return new String(part);
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
		String tblName = tableDefinition.getName();
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
		                addPartition(year, day, dir+"/"+d+"/"+f, null);
		            }
		        } else {
		            Matcher m=p.matcher(f);
		            if(m.matches()) {
		                String so=m.group(1);
		                DataType reqType=partitioningSpec.valueColumnType;
		                try {//TODO FIX
		                    addPartition(year, day, dir+"/"+d+"/"+f, null);
		                } catch(IllegalArgumentException e) {
		                    log.error("cannot cast string '"+so+"' to type "+reqType+": "+e.getMessage());
		                    continue;
		                }
		            }
		        }
		    }
		}
	}
	
	private void addPartition(int year, int day, String dir, Object v) {
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
			   end=intv.getEnd();
		   }
		
		   Partition p=new RdbPartition(start, end, v, dir);
		   intv.add(v, p);
	}
	
	@Override
	protected Partition createPartition(int year, int doy, Object value) throws IOException {
		return null;
	}

	@Override
	protected Partition createPartition(Object value) {
		// TODO Auto-generated method stub
		return null;
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
