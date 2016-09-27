package org.yamcs.archive;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Performs histogram and completeness index retrievals.
 * @author nm
 *
 */
class IndexRequestProcessor implements Runnable {
    int n=500;
    final String archiveInstance;
    final static AtomicInteger counter=new AtomicInteger(); 
    static Logger log=LoggerFactory.getLogger(IndexRequestProcessor.class.getName());
    final IndexRequest req;
    TmIndex tmIndexer;
    IndexRequestListener indexRequestListener;

    //these maps contains the names with which the records will be sent to the client
    final Map<String, NamedObjectId> tmpackets=new HashMap<>();
  //  final Map<String, NamedObjectId> ppgroups=new HashMap<>();
    
    boolean sendParams;

    IndexRequestProcessor(TmIndex tmIndexer, IndexRequest req, IndexRequestListener l) {
        log.debug("new index request: "+req);
        this.archiveInstance=req.getInstance();
        this.req=req;
        this.tmIndexer=tmIndexer;
        this.indexRequestListener=l;

        if(req.getSendAllTm() || req.getTmPacketCount()>0) {
            XtceDb db=XtceDbFactory.getInstance(archiveInstance);
            String defaultns=req.getDefaultNamespace();

            if(req.getSendAllTm()) {
                for(SequenceContainer sc:db.getSequenceContainers()) {
                    if(req.hasDefaultNamespace() && (sc.getAlias(defaultns)!=null)) {
                        tmpackets.put(sc.getQualifiedName(),  NamedObjectId.newBuilder()
                                .setName(sc.getAlias(defaultns)).setNamespace(defaultns).build());
                    } else {
                        tmpackets.put(sc.getQualifiedName(), NamedObjectId.newBuilder().setName(sc.getQualifiedName()).build());
                    }
                }
            } else {
                for(NamedObjectId id:req.getTmPacketList()) {
                    SequenceContainer sc=db.getSequenceContainer(id);
                    if(sc!=null) {
                        tmpackets.put(sc.getQualifiedName(), id);
                    }
                }
            }
        }
        
        //pp groups do not support namespaces yet
        if(req.getSendAllPp() || req.getPpGroupCount()>0) {
            sendParams = true; //TODO: fix; currently always send all
        }
        /*
            PpDefDb ppdb=PpDbFactory.getInstance(archiveInstance);
            if(req.getSendAllPp()) {
                for(String s:ppdb.getGroups()) {
                    ppgroups.put(s, NamedObjectId.newBuilder().setName(s).build());
                }
            } else {
                for(NamedObjectId id:req.getPpGroupList()) {
                    ppgroups.put(id.getName(), id);
                }
            }
        }*/
    }

    @Override
    public void run() {
        boolean ok=true;
        try {
            if(tmpackets.size()>0) ok=sendHistogramData(XtceTmRecorder.TABLE_NAME, "pname", 2000, tmpackets);
            if(ok && sendParams) ok=sendHistogramData(PpRecorder.TABLE_NAME, "ppgroup", 20000, null); //use 20 sec for the PP to avoid millions of records
            
            if(req.getSendAllCmd()) ok=sendHistogramData(CommandHistoryRecorder.TABLE_NAME, "cmdName", 2000, null);
            if(req.getSendAllEvent()) ok=sendHistogramData(EventRecorder.TABLE_NAME, "source", 2000, null);
            if(ok && req.getSendCompletenessIndex()) ok=sendCompletenessIndex();
        } catch (Exception e) {
            log.error("got exception while sending the response", e);
            ok=false;
        } finally {
            try {
                indexRequestListener.finished(ok);
            } catch (Exception e) {
                log.error("Error when sending finished signal ", e);
            }
        }
    }


    boolean sendHistogramData(final String tblName, String columnName, long mergeTime, final Map<String, NamedObjectId> name2id) {
        try {
        	YarchDatabase ydb=YarchDatabase.getInstance(req.getInstance());
        	if( ydb.getTable( tblName ) == null ) {
        		log.warn( "Histogram from table '{}' requested, but table does not exist.", tblName );
        		return true;
        	}
            String streamName=tblName+"_histo_str"+counter.getAndIncrement();
            StringBuilder sb=new StringBuilder();
            sb.append("create stream ").append(streamName).append(" as select * from ")
            .append(tblName).append(" histogram(").append(columnName).append(",").append(mergeTime).append(")");
            if(req.hasStart()||req.hasStop()) {
                sb.append(" where ");
            }
            if(req.hasStart()) {
                sb.append("last>").append(req.getStart());
            } 
            if(req.hasStart() && req.hasStop()) {
                sb.append(" and ");
            }
            if(req.hasStop()) {
                sb.append("first<").append(req.getStop());
            }
            
            String query=sb.toString();
            log.debug("executing query: {}", query);
            ydb.execute(query);
            final Semaphore semaphore=new Semaphore(0);
            final Stream stream=ydb.getStream(streamName);
            final AtomicBoolean ok=new AtomicBoolean(true);
            stream.addSubscriber(new StreamSubscriber() {
                IndexResult.Builder builder=IndexResult.newBuilder().setInstance(archiveInstance).setType("histogram").setTableName(tblName);
                
                @Override
                public void streamClosed(Stream s) {
                    log.debug("Stream {} closed", s.getName());
                    IndexRequestProcessor.this.sendData(builder);
                    semaphore.release();
                }

                @Override
                public void onTuple(Stream s, Tuple t) {
                    String name=(String)t.getColumn(0);
                    NamedObjectId id;
                    if(name2id!=null) {
                        id=name2id.get(name);
                        if(id==null) {
                            log.debug("Not sending {} because no id for it", name);
                            return;
                        }
                    } else {
                        id=NamedObjectId.newBuilder().setName(name).build();
                    }
                    long first=(Long)t.getColumn(1);
                    long last=(Long)t.getColumn(2);
                    int num=(Integer)t.getColumn(3);
                    ArchiveRecord ar=ArchiveRecord.newBuilder().setId(id)
                    .setFirst(first).setLast(last).setNum(num).build();
                    builder.addRecords(ar);
                    if(builder.getRecordsCount()>=n) sendData();
                }

                void sendData() {
                    if(!IndexRequestProcessor.this.sendData(builder)) {
                        stream.close();
                        ok.set(false);
                        return;
                    }
                    builder=IndexResult.newBuilder().setInstance(archiveInstance).setType("histogram").setTableName(tblName);
                }
            });
            stream.start();
            semaphore.acquire();
            return ok.get();
        } catch (Exception e) {
            log.error("got exception while retrieving histogram data", e);
            return false;
        }
    }


    private boolean sendCompletenessIndex() {
        long start=req.hasStart()?req.getStart():TimeEncoding.INVALID_INSTANT;
        long stop=req.hasStop()?req.getStop():TimeEncoding.INVALID_INSTANT;
        IndexIterator it=tmIndexer.getIterator(null, start, stop);
        ArchiveRecord ar;
        IndexResult.Builder builder=IndexResult.newBuilder().setInstance(archiveInstance).setType("completeness");
        while((ar=it.getNextRecord())!=null) {
            builder.addRecords(ar);
            if(builder.getRecordsCount()>=n) {
                if(!sendData(builder)) return false;
                builder=IndexResult.newBuilder().setInstance(archiveInstance).setType("completeness");
            }
        }
        if(!sendData(builder)) return false;
        return true;
    }

    boolean sendData(IndexResult.Builder builder) {
        if(builder.getRecordsCount()==0) return true;
        log.debug("sending {} {} records", builder.getRecordsCount(), builder.getType());
        try {
            indexRequestListener.processData(builder.build());
            return true;
        } catch (Exception e) {
            log.warn("Error when sending histogram data",e);
            return false;
        }
    }
}
