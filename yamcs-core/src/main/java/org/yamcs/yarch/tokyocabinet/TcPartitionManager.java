package org.yamcs.yarch.tokyocabinet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TimePartitionSchema.PartitionInfo;
import org.yamcs.yarch.TableDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Keeps track of table partitions. 
 * Types of partitions that are supported:
 *  time          - creates files of shape yyyy/ddd/tblname
 *  time,value    - creates files of shape yyyy/ddd/tblname#value
 *  value         - creates files of shape tblname#value
 *  
 *  yyyy/ddd can also be yyyy/mm depending on the {@link TimePartitioningSchema}
 * @author nm
 */
public class TcPartitionManager extends PartitionManager {
    static Logger log=LoggerFactory.getLogger(TcPartitionManager.class.getName());

    public TcPartitionManager(TableDefinition tableDefinition) {
	super(tableDefinition);
    }
    /**
     * 
     * @return partition filenames (relative to the data directory of the table)
     */
    public List<String> getPartitionFilenames() {
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
    protected Partition createPartition(PartitionInfo pinfo, Object value) throws IOException {
	String dataDir = tableDefinition.getDataDir();
	File f=new File(dataDir+"/"+pinfo.dir);
	if(!f.exists()) { 
	    f.mkdirs(); //we don't check the return of mkdirs because maybe another thread creates the directory in the same time
	    if(!f.exists()) {
		log.error("Failed to create directories for "+f);
		throw new IOException("failed to create directories for "+f);
	    }
	}
	if(value==null) {
	    return new TcPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, pinfo.dir+"/"+tableDefinition.getName()+".tcb");
	} else {
	    return new TcPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, pinfo.dir+"/"+tableDefinition.getName()+"#"+valueToPartition(value)+".tcb");
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
	readDir("");		
    }

    private void readDir(String dir) {		
	String tblName = tableDefinition.getName();
	String dataDir = tableDefinition.getDataDir();
	String[] files=new File(dataDir+"/"+dir).list();
	Pattern p=Pattern.compile(tblName+"#([\\-\\s\\w]+)\\.tcb");
	for(String s:files) {
	    File f = new File(dataDir +"/"+dir+"/"+s);
	    if(f.isDirectory()) {
		if(dir.isEmpty()) {
		    readDir(s);
		} else {
		    readDir(dir+"/"+s);	
		}
	    } else {
		Matcher m=p.matcher(s);
		if(!m.matches())  continue;
		PartitionInfo pinfo = partitioningSpec.timePartitioningSchema.parseDir(dir);
		if(pinfo==null) continue;

		String so=m.group(1);
		DataType reqType=partitioningSpec.getValueColumnType();
		try {
		    Object o=partitionToValue(so, reqType);
		    addPartition(pinfo, dir+"/"+s, o);
		} catch(IllegalArgumentException e) {
		    log.error("cannot cast string '"+so+"' to type "+reqType+": "+e.getMessage());
		    continue;
		}
	    }
	}			
    }

    private void addPartition(PartitionInfo pinfo, String filename, Object v) {	   	   
	Interval intv=intervals.get(pinfo.partitionStart);	  

	if(intv==null) {	    
	    intv=new Interval(pinfo.partitionStart, pinfo.partitionEnd);
	    intervals.put(pinfo.partitionStart, intv);
	}
	Partition p=new TcPartition(pinfo.partitionStart, pinfo.partitionEnd, v, filename);
	intv.add(v, p);
    }		
}

