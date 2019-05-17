package org.yamcs.tctm.cfs;


import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

public class CfsTcTmSplitter extends AbstractService implements YamcsService {
    
    //splits the inStream into cfdpIn and tmRealtimeStream
    Stream inStream;
    Stream cfdpInStream;
    Stream tmRealtimeStream;
    
    //combines the cfdpOutStream and the tcRealtime into outStream
    Stream outStream;
    Stream cfdpOutStream;
    Stream tcRealtimeStream;
    
    int cfdpTmMsgId;
    int cfdpTcMsgId;
    
    StreamSubscriber tmSplitter;
    StreamSubscriber tcCombiner;
    
    public CfsTcTmSplitter(String yamcsInstance, YConfiguration config) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        inStream = getAndCheck(ydb, config.getString("inStream"));
        cfdpInStream = getAndCheck(ydb, config.getString("cfdpInStream"));
        tmRealtimeStream = getAndCheck(ydb,config.getString("tmRealtimeStream"));
        
        outStream = getAndCheck(ydb, config.getString("outStream"));
        cfdpOutStream = getAndCheck(ydb, config.getString("cfdpOutStream"));
        tcRealtimeStream = getAndCheck(ydb, config.getString("tcRealtimeStream"));
        cfdpTmMsgId = config.getInt("cfdpTmMsgId");
        cfdpTcMsgId = config.getInt("cfdpTcMsgId");
        
    }
    @Override
    protected void doStart() {
        tmSplitter = new StreamSubscriber() {
            
            @Override
            public void streamClosed(Stream stream) {}
            
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                byte[] packet = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
                int msgId = ByteArrayUtils.decodeShort(packet, 0);
                if(msgId == cfdpTmMsgId) {
                    cfdpInStream.emitTuple(tuple);
                } else {
                    tmRealtimeStream.emitTuple(tuple);
                }
            }
        };
        
        inStream.addSubscriber(tmSplitter);
        
        tcCombiner = new StreamSubscriber() {
            
            @Override
            public void streamClosed(Stream stream) {}
            
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                // TODO Auto-generated method stub
                
            }
        };
        notifyStarted();
    }
    
    private Stream getAndCheck(YarchDatabaseInstance ydb, String streamName) {
        Stream s = ydb.getStream(streamName);
        if (s == null) {
            throw new ConfigurationException("Cannot find stream " + streamName);
        }
        return s;
    }
    @Override
    protected void doStop() {
        inStream.removeSubscriber(tmSplitter);
        cfdpOutStream.removeSubscriber(tcCombiner);
        tcRealtimeStream.removeSubscriber(tcCombiner);
        notifyStopped();
    }
}    
