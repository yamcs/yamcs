package org.yamcs.yarch.tokyocabinet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.TableDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	    this(def, def.getDataDir());
	}

	public TcPartitionManager(TableDefinition tableDefinition, String dataDir) {
      super(tableDefinition);
      this.dataDir = dataDir;
    }
	/**
	 * 
	 * @return partition filenames (relative to the data directory of the table)
	 */
    public Collection<String> getPartitionFilenames() {
        List<String> filenames=new ArrayList<String>();
        for(Entry<Long,Interval> entry:intervals.entrySet()) {
            Interval intv=entry.getValue();
            for(Partition p:intv.getPartitions().values()) {
            	TcPartition tcp = (TcPartition)p;
            	filenames.add(tcp.filename);
            }           
        }
        return filenames;
    }
    
    

	@Override
	protected Partition createPartition(int year, int doy, Object value) throws IOException {
         Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
         cal.clear();
         cal.set(Calendar.YEAR, year);
         cal.set(Calendar.DAY_OF_YEAR, doy);
         long dayStartInstant = TimeEncoding.fromCalendar(cal);
         cal.add(Calendar.DAY_OF_YEAR, 1);
         long nextDayStartInstant = TimeEncoding.fromCalendar(cal);

         String dir=String.format("%4d/%03d", year, doy);
         File f=new File(dataDir+"/"+dir);
         if(!f.exists()) { 
             f.mkdirs(); //we don't check the return of mkdirs because maybe another thread creates the directory in the same time
             if(!f.exists()) {
                 log.error("Failed to create directories for "+f);
                 throw new IOException("failed to create directories for "+f);
             }
         }
 		if(value==null) {
			return new TcPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, dir+"/"+tableDefinition.getName()+".tcb");
		} else {
			return new TcPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, dir+"/"+tableDefinition.getName()+"#"+valueToPartition(value)+".tcb");
		}
	}

	@Override
	protected Partition createPartition(Object value) {
		if(value==null) {
			return new TcPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, tableDefinition.getName()+".tcb");
		} else {
			return new TcPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, tableDefinition.getName()+"#"+valueToPartition(value)+".tcb");
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
		                try {
		                    Object o=partitionToValue(so, reqType);
		                    addPartition(year, day, dir+"/"+d+"/"+f, o);
		                } catch(IllegalArgumentException e) {
		                    log.error("cannot cast string '"+so+"' to type "+reqType+": "+e.getMessage());
		                    continue;
		                }
		            }
		        }
		    }
		}
	}
	
	private void addPartition(int year, int day, String filename, Object v) {
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
	
	   Partition p=new TcPartition(start, end, v, filename);
	   intv.add(v, p);
	}		
}

