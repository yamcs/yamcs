package org.yamcs.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.tctm.TmDataLinkInitialiser;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmExtractor;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Records XTCE TM sequence containers.
 * 
 * It creates a stream for each sequence container under the root. 
 * TODO: make it more configurable
 * 
 * @author nm
 *
 */
public class XtceTmRecorder extends AbstractService {
    private long totalNumPackets;
    protected Logger log;

    String yamcsInstance;
    final Tuple END_MARK=new Tuple(TmDataLinkInitialiser.TM_TUPLE_DEFINITION, new Object[] {null,  null, null, null});
    XtceTmExtractor tmExtractor;
    static public String REALTIME_TM_STREAM_NAME = "tm_realtime";
    static public String DUMP_TM_STREAM_NAME = "tm_dump";
    static public final String TABLE_NAME = "tm";
    static public final String PNAME_COLUMN = "pname";
    XtceDb xtceDb;
    
    private final List<StreamRecorder> recorders = new ArrayList<StreamRecorder>();

    static public final TupleDefinition RECORDED_TM_TUPLE_DEFINITION=new TupleDefinition();
    static {
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmDataLinkInitialiser.GENTIME_COLUMN, DataType.TIMESTAMP);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmDataLinkInitialiser.SEQNUM_COLUMN, DataType.INT);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmDataLinkInitialiser.RECTIME_COLUMN, DataType.TIMESTAMP);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(TmDataLinkInitialiser.PACKET_COLUMN, DataType.BINARY);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(PNAME_COLUMN, DataType.ENUM); //container name (XTCE qualified name) 
    }

    public XtceTmRecorder(String yamcsInstance) throws IOException, ConfigurationException, StreamSqlException, ParseException, YamcsApiException {
        this(yamcsInstance, null);
    }
    final TimeService timeService;
    
    /**
     * old constructor for compatibility with older configuration files
     * @param yamcsInstance
     * @throws IOException
     * @throws ConfigurationException
     * @throws StreamSqlException
     * @throws ParseException
     * @throws ActiveMQException
     * @throws YamcsApiException
     */
    public XtceTmRecorder(String yamcsInstance, Map<String, Object> config) throws IOException, ConfigurationException, StreamSqlException, ParseException, YamcsApiException {

        this.yamcsInstance = yamcsInstance;
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+yamcsInstance+"]");

        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
       
        if(ydb.getTable(TABLE_NAME)==null) {
            String query="create table "+TABLE_NAME+"("+RECORDED_TM_TUPLE_DEFINITION.getStringDefinition1()+", primary key(gentime, seqNum)) histogram(pname) partition by time_and_value(gentime"+getTimePartitioningSchemaSql()+", pname) table_format=compressed";
            
            ydb.execute(query);
        }
        ydb.execute("create stream tm_is"+RECORDED_TM_TUPLE_DEFINITION.getStringDefinition());
        ydb.execute("insert into "+TABLE_NAME+" select * from tm_is");
        xtceDb=XtceDbFactory.getInstance(yamcsInstance);
        tmExtractor=new XtceTmExtractor(xtceDb);

        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        if(config==null || !config.containsKey("streams")) {
            List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.tm);
            for(StreamConfigEntry sce: sceList){
                createRecorder(sce);
            }
        } else if(config != null && config.containsKey("streams")){
            List<String> streamNames = YConfiguration.getList(config, "streams");
            for(String sn: streamNames) {
                StreamConfigEntry sce = sc.getEntry(StandardStreamType.tm, sn);
                if(sce==null) {
                    throw new ConfigurationException("No stream config found for '"+sn+"'");
                }
                createRecorder(sce);
            }
        }
        
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    static String getTimePartitioningSchemaSql() {
        YConfiguration yconfig = YConfiguration.getConfiguration("yamcs");
        String partSchema = "";
        if(yconfig.containsKey("archiveConfig", "timePartitioningSchema")) {
            partSchema = "('"+yconfig.getString("archiveConfig", "timePartitioningSchema")+"')";
        }
        return partSchema;
    }
    private void createRecorder(StreamConfigEntry streamConf) {       
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
        SequenceContainer rootsc = streamConf.getRootContainer() ;
        if(rootsc == null) {
            rootsc=xtceDb.getRootSequenceContainer();
        }
        if(rootsc==null) {
            throw new ConfigurationException("XtceDb does not have a root sequence container and no container was specified for decoding packets from "+streamConf.getName()+" stream");
        }


        subscribeContainers(rootsc);
       
        Stream inputStream=ydb.getStream(streamConf.getName());

        if(inputStream==null) {
            throw new ConfigurationException("Cannot find stream '"+streamConf.getName()+"'");
        }
        Stream tm_is = ydb.getStream("tm_is");
        StreamRecorder recorder = new StreamRecorder(inputStream, tm_is, rootsc, streamConf.isAsync());
        recorders.add(recorder);
    }

    //subscribe all containers that have useAsArchivePartition set
    private void subscribeContainers(SequenceContainer sc) {
        if(sc==null) return;
        
        if(sc.useAsArchivePartition()) {
            tmExtractor.startProviding(sc);
        }
        
        if(xtceDb.getInheritingContainers(sc) != null) {
            for(SequenceContainer sc1:xtceDb.getInheritingContainers(sc)) {
                subscribeContainers(sc1);
            }
        }
    }
    
    @Override
    protected void doStart() {
        for(StreamRecorder sr: recorders) {
            sr.inputStream.addSubscriber(sr);
            if(sr.async) {
                new Thread(sr).start();
            }
        }
        notifyStarted();
    }



    @Override
    protected void doStop() {
        for(StreamRecorder sr: recorders) {
            sr.quit();
        }
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream s = ydb.getStream("tm_is");
        Collection<StreamSubscriber> subscribers = s.getSubscribers();
        s.close();
        for(StreamSubscriber ss:subscribers) {
            if(ss instanceof TableWriter) {
                ((TableWriter)ss).close();
            }
        }
        notifyStopped();
    }




    public long getNumProcessedPackets() {
        return totalNumPackets;
    }

    /**
     * Records telemetry from one stream. The decoding starts with the specified sequence container 
     * 
     * If async is set to true, the tuples are put in a queue and processed from a different thread.
     * @author nm
     *
     */
    class StreamRecorder implements StreamSubscriber, Runnable {
        SequenceContainer sc;
        boolean async;
        Stream inputStream;
        Stream outputStream;

        LinkedBlockingQueue<Tuple> tmQueue;

        StreamRecorder(Stream inputStream, Stream outputStream, SequenceContainer sc, boolean async) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.sc = sc;
            this.async = async;
            if(async) {
                tmQueue = new LinkedBlockingQueue<Tuple>(100000);
            }
        }

        /**
         * Only called if running in async mode, otherwise tuples are saved directly
         */
        @Override
        public void run() {
            Thread.currentThread().setName(this.getClass().getSimpleName()+"["+yamcsInstance+"]");
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


        @Override
        public void onTuple(Stream istream, Tuple t) {
            try {
                if(async) {
                    tmQueue.put(t);
                } else {
                    saveTuple(t);
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

        public void quit() {
            if(!async) return;

            try {
                tmQueue.put(END_MARK);
            } catch (InterruptedException e) {
                log.warn("got interrupted while putting the empty buffer in the queue");
                Thread.currentThread().interrupt();
            }
        }

        /**saves a TM tuple. The definition is in  * TmProviderAdapter
         * 
         * it finds the XTCE names and puts them inside the recording
         * @param t
         */
        protected void saveTuple(Tuple t) {
            long gentime=(Long)t.getColumn(0);
            byte[] packet=(byte[])t.getColumn(3);
            totalNumPackets++;

            ByteBuffer bb=ByteBuffer.wrap(packet);
            tmExtractor.processPacket(bb, gentime, timeService.getMissionTime(), sc);

            //the result contains a list with all the matching containers, the first one is the root container
            //we should normally have just two elements in the list
            List<ContainerExtractionResult> result=tmExtractor.getContainerResult();
            SequenceContainer partitionBySc=null;
            for(int i=result.size()-1; i>=0; i--) {
                SequenceContainer sc = result.get(i).getContainer();
                if(sc.useAsArchivePartition()) {
                    partitionBySc = sc;
                    break;
                }
            }
            if(partitionBySc==null) {
                partitionBySc = result.get(0).getContainer();
            }

            try {
                List<Object> c=t.getColumns();
                List<Object> columns=new ArrayList<Object>(c.size()+1);
                columns.addAll(c);

                columns.add(c.size(), partitionBySc.getQualifiedName());
                Tuple tp=new Tuple(RECORDED_TM_TUPLE_DEFINITION, columns);
                outputStream.emitTuple(tp);
            } catch (Exception e) {
                log.error("got exception when saving packet ", e);
            }
        }

    }
}