package org.yamcs;

import org.hornetq.api.core.HornetQException;
import org.yamcs.archive.PpRecorder;
import org.yamcs.archive.TcUplinkerAdapter;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.tctm.YarchPpProvider;
import org.yamcs.tctm.YarchTcUplinker;
import org.yamcs.tctm.YarchTmPacketProvider;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;
import org.yamcs.api.YamcsApiException;

/**
 * Creates a channel processing data from the realtime yarch streams.
 * @author nm
 *
 */
public class YarchChannel extends AbstractService {
    TcTmService tctm;
    Channel channel;
    public YarchChannel(String archiveInstance) throws ConfigurationException, StreamSqlException, ChannelException, ParseException, HornetQException, YamcsApiException {
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        TmPacketProvider tm=new YarchTmPacketProvider(archiveInstance, "tm_realtime");
        
        
        ParameterProvider param=null;
        
        if(ydb.getStream("pp_realtime")!=null) {
            param=new YarchPpProvider(archiveInstance, PpRecorder.REALTIME_PP_STREAM_NAME);
        } 
        YarchTcUplinker tc=null;
        if(ydb.getStream(TcUplinkerAdapter.REALTIME_TC_STREAM_NAME) !=null) {
            tc=new YarchTcUplinker(archiveInstance, TcUplinkerAdapter.REALTIME_TC_STREAM_NAME);
        }
        
        tctm=new SimpleTcTmService(tm, param, tc);
        CommandHistory cmdHist=new YarchCommandHistoryAdapter(archiveInstance);
        channel=ChannelFactory.create(archiveInstance, "realtime", "yarch", "realtime", tctm, "system", cmdHist);
        channel.setPersistent(true);
        new RealtimeParameterService(channel);
    }

    @Override
    protected void doStart() {
       channel.start();
       notifyStarted();
    }

    @Override
    protected void doStop() {
        channel.quit();
        notifyStarted();
    }
}