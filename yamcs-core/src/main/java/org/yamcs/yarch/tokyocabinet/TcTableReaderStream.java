package org.yamcs.yarch.tokyocabinet;

import java.io.IOException;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.AbstractTableReaderStream;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.IndexFilter;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;


/**
 * Reads Tokyo Cabinet tables
 * @author nm
 *
 */
public class TcTableReaderStream extends AbstractTableReaderStream implements Runnable, DbReaderStream {
    static AtomicInteger count=new AtomicInteger(0);
    
    final PartitioningSpec partitioningSpec;
    final TcPartitionManager partitionManager;
    
    public TcTableReaderStream(YarchDatabase ydb, TableDefinition tblDef, TcPartitionManager partitionManager, boolean ascending, boolean follow) {
        super(ydb, tblDef, partitionManager, ascending, follow);
        partitioningSpec=tblDef.getPartitioningSpec();
        this.partitionManager = partitionManager;
    }

    @Override 
    public void start() {
        (new Thread(this, "TcTableReader["+getName()+"]")).start();
    } 

    /*
     * reads a file, sending data only that conform with the start and end filters. 
     * returns true if the stop condition is met
     */
    @Override
    protected boolean runPartitions(List<Partition> partitions, IndexFilter range) throws IOException {
        byte[] rangeStart=null;
        boolean strictStart=false;
        byte[] rangeEnd=null;
        boolean strictEnd=false;
        
        if(range!=null) {
            ColumnDefinition cd=tableDefinition.getKeyDefinition().getColumn(0);
            ColumnSerializer cs=tableDefinition.getColumnSerializer(cd.getName());
            if(range.keyStart!=null) {
                strictStart=range.strictStart;
                rangeStart=cs.getByteArray(range.keyStart);
            }
            if(range.keyEnd!=null) {
                strictEnd=range.strictEnd;
                rangeEnd=cs.getByteArray(range.keyEnd);
            }
        }
        
        
        log.debug("running partitions "+partitions);
        PriorityQueue<TcRawTuple> orderedQueue=new PriorityQueue<TcRawTuple>();
        TCBFactory tcbf=ydb.getTCBFactory();
        try {
            int i=0;
            //first open all the partitions and collect a tuple from each
            for(Partition p:partitions) {
            	TcPartition tp= (TcPartition)p;
                log.debug("opening partition "+tp);
                YBDB db=tcbf.getTcb(tableDefinition.getDataDir()+"/"+tp.filename, tableDefinition.isCompressed(), false);
                YBDBCUR cursor=db.openCursor();
                boolean found=true;
                if(rangeStart!=null) {
                    if(cursor.jump(rangeStart)) {
                        if((strictStart)&&(compare(rangeStart, cursor.key())==0)) {
                            //if filter condition is ">" we skip the first record if it is equal to the key
                            found=cursor.next();
                        }
                    } else {
                        found=false;
                    }
                    if(!found) log.debug("no record corresponding to the StartFilter");
                } else {                
                    if(!cursor.first()) {
                        log.debug("tcb contains no record");
                        found=false;
                    }
                }
                if(!found) {
                    db.closeCursor(cursor);
                    tcbf.dispose(db);
                } else {
                    orderedQueue.add(new TcRawTuple(cursor.key(), cursor.val(), db, cursor, i++));
                }
            }
            log.debug("got one tuple from each partition, starting the business");

            //now continue publishing the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                TcRawTuple rt=orderedQueue.poll();
                if(!emitIfNotPastStop(rt, rangeEnd, strictEnd)) {
                    return true;
                }
                if(rt.cursor.next()) {
                   rt.key=rt.cursor.key();
                   rt.value=rt.cursor.val();
                   orderedQueue.add(rt);
                } else {
                    log.debug(rt.db.path()+" finished");
                    rt.cursor.close();
                    tcbf.dispose(rt.db);
                }
            }
            return false;
        } catch (Exception e){
           e.printStackTrace();
           return false;
        } finally {
            for(RawTuple rt:orderedQueue) {
            	TcRawTuple tcrt =(TcRawTuple)rt; 
                tcrt.cursor.close();
                tcbf.dispose(tcrt.db);
            }
        }
    }

  
    
    
    class TcRawTuple extends RawTuple {       
		int index;//used for sorting tuples with equals keys
        YBDB db;
        YBDBCUR cursor;
        
        public TcRawTuple(byte[] key, byte[] value, YBDB db, YBDBCUR cursor, int index) {
        	super(key,value, index);
            this.db = db;
            this.cursor = cursor;
            this.index=index;
        }
    }
}
