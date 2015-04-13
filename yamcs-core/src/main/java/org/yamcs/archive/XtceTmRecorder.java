package org.yamcs.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.hornetq.api.core.HornetQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.api.YamcsApiException;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmExtractor;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Records XTCE TM sequence containers.
 * 
 * It creates a stream for each sequence container under the root. 
 * TODO: make it more configurable
 * 
 * @author nm
 *
 */
public class XtceTmRecorder extends AbstractExecutionThreadService implements Runnable, StreamSubscriber {
    private long totalNumPackets;
    protected Logger log;
    LinkedBlockingQueue<Tuple> tmQueue=new LinkedBlockingQueue<Tuple>(100000);
    
    boolean giveWarningIfMultipleMatches=true; //print a warning if a packet is saved multiple times
    
    String archiveInstance;
    final Stream realtimeStream, dumpStream;
    final Tuple END_MARK=new Tuple(TmProviderAdapter.TM_TUPLE_DEFINITION, new Object[] {null,  null, null, null});
    protected Stream stream;
    XtceTmExtractor tmExtractor;
    static public final String REALTIME_TM_STREAM_NAME="tm_realtime";
    static public final String DUMP_TM_STREAM_NAME="tm_dump";
    static public final String TABLE_NAME="tm";
    static public final String PNAME_COLUMN="pname";
    
    static public final TupleDefinition RECORDED_TM_TUPLE_DEFINITION=new TupleDefinition();
    static {
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmProviderAdapter.GENTIME_COLUMN, DataType.TIMESTAMP);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmProviderAdapter.SEQNUM_COLUMN, DataType.INT);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmProviderAdapter.RECTIME_COLUMN, DataType.TIMESTAMP);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmProviderAdapter.PACKET_COLUMN, DataType.BINARY);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(PNAME_COLUMN, DataType.ENUM); //packet name 
    }
    
    
    public XtceTmRecorder(String archiveInstance) throws IOException, ConfigurationException, StreamSqlException, ParseException, HornetQException, YamcsApiException {
        this.archiveInstance=archiveInstance;
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+archiveInstance+"]");
        
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        if(ydb.getTable(TABLE_NAME)==null) {
            String query="create table "+TABLE_NAME+"("+RECORDED_TM_TUPLE_DEFINITION.getStringDefinition1()+", primary key(gentime, seqNum)) histogram(pname) partition by time_and_value(gentime, pname) table_format=compressed";
            ydb.execute(query);
        }
        ydb.execute("create stream tm_is"+RECORDED_TM_TUPLE_DEFINITION.getStringDefinition());
        ydb.execute("insert into "+TABLE_NAME+" select * from tm_is");
        
        
        Stream s=ydb.getStream(REALTIME_TM_STREAM_NAME);
        if(s==null) {
           throw new ConfigurationException("Cannot find stream '"+REALTIME_TM_STREAM_NAME+"'");
        }
        realtimeStream=s;
        
        s=ydb.getStream(DUMP_TM_STREAM_NAME);
        if(s==null) {
            throw new ConfigurationException("Cannot find stream '"+DUMP_TM_STREAM_NAME+"'");
        }
        dumpStream=s;
        
        
        stream=ydb.getStream("tm_is");
        XtceDb xtcedb=XtceDbFactory.getInstance(archiveInstance);
        tmExtractor=new XtceTmExtractor(xtcedb);
        SequenceContainer rootsc=xtcedb.getRootSequenceContainer();
        if(rootsc==null) throw new ConfigurationException("XtceDb does not have a root container");
        if( xtcedb.getInheritingContainers(rootsc) == null ) {
        	log.info( "Root container has no inheriting containers, so providing TM from root container: "+rootsc.getName() );
        	tmExtractor.startProviding( rootsc );
        } else {
        	for(SequenceContainer sc:xtcedb.getInheritingContainers(rootsc)) {
            	tmExtractor.startProviding(sc);
            }
        }
    }
    
    @Override
    protected void startUp() {
        Thread.currentThread().setName(this.getClass().getSimpleName()+"["+archiveInstance+"]");
        realtimeStream.addSubscriber(this);
        dumpStream.addSubscriber(this);
    }
    
    @Override
    public void onTuple(Stream istream, Tuple t) {
        try {
            if(istream==realtimeStream) {
                tmQueue.put(t);
            } else {
                //realtime data has priority, if the queue is almost full, delay processing the dump stream
                while(tmQueue.remainingCapacity()<100) Thread.sleep(100);
                tmQueue.put(t);
            }
        } catch (InterruptedException e) {
            log.warn("Got interrupted exception while putting data in the queue: ", e);
        }
    }

    @Override
    public void streamClosed(Stream istream) {
        //shouldn't happen
        log.error("stream "+istream+" closed");
    }

    @Override
    protected void triggerShutdown() {
	quit();
    }
    
    
    @Override
    public void run() {
        try {
            Tuple t;
            while(true) {
                t=tmQueue.take();
                if(t==END_MARK) break;
                saveTuple(t);
            }
        } catch (InterruptedException e) {
            log.warn("Got InteruptedException when waiting for the next tuple ", e);
            Thread.currentThread().interrupt();
        }
    }
    
    public long getNumProcessedPackets() {
	return totalNumPackets;
    }
    
    /**receives a TM tuple. The definition is in  * TmProviderAdapter
     * 
     * it finds the XTCE names and puts them inside the recording
     * @param t
     */
    protected void saveTuple(Tuple t) {
        long gentime=(Long)t.getColumn(0);
        byte[] packet=(byte[])t.getColumn(3);
        totalNumPackets++;
        
        ByteBuffer bb=ByteBuffer.wrap(packet);
        tmExtractor.processPacket(bb, gentime);

        //the result contains a list with all the matching containers, the first one is the root container
        //we should normally have just two elements in the list
        List<ContainerExtractionResult> result=tmExtractor.getContainerResult();

        
        try {
            int k=0;
            if(result.size()>1) k++;
            
            if((giveWarningIfMultipleMatches) && result.size()>2) {
                log.warn("Packet matches multiple sequence containers {}", result.toString());
            }
            List<Object> c=t.getColumns();
            List<Object> columns=new ArrayList<Object>(c.size()+1);
            columns.addAll(c);
            
            columns.add(c.size(), result.get(k).getContainer().getQualifiedName());
            Tuple tp=new Tuple(RECORDED_TM_TUPLE_DEFINITION, columns);
            stream.emitTuple(tp);
        } catch (Exception e) {
            log.error("got exception when saving packet ", e);
        }
    }

    public void quit() {
        try {
            tmQueue.put(END_MARK);
        } catch (InterruptedException e) {
            log.warn("got interrupted while putting the empty buffer in the queue");
            Thread.currentThread().interrupt();
        }
    }
}
