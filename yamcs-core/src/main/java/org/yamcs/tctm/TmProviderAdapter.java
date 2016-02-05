package org.yamcs.tctm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.hornetq.api.core.HornetQException;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Loads multiple TmPacketSource and inject all the packets into a defined stream
 * @author nm
 *
 */
public class TmProviderAdapter extends AbstractService {
    private Collection<TmPacketSource> tmproviders=new ArrayList<TmPacketSource>();
    final String archiveInstance;
    final static public String GENTIME_COLUMN="gentime";
    final static public String SEQNUM_COLUMN="seqNum";
    final static public String RECTIME_COLUMN="rectime";
    final static public String PACKET_COLUMN="packet";

    static public final TupleDefinition TM_TUPLE_DEFINITION=new TupleDefinition();
    static {
        TM_TUPLE_DEFINITION.addColumn(GENTIME_COLUMN, DataType.TIMESTAMP);
        TM_TUPLE_DEFINITION.addColumn(SEQNUM_COLUMN, DataType.INT);
        TM_TUPLE_DEFINITION.addColumn(RECTIME_COLUMN, DataType.TIMESTAMP); //reception or recording time (useful in case we import data from other recordings which provide this) 
        TM_TUPLE_DEFINITION.addColumn(PACKET_COLUMN, DataType.BINARY);
    }

    YamcsClient yclient;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public TmProviderAdapter(String archiveInstance) throws ConfigurationException, StreamSqlException, ParseException, HornetQException, YamcsApiException, IOException {
        this.archiveInstance=archiveInstance;
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        
        YConfiguration c=YConfiguration.getConfiguration("yamcs."+archiveInstance);
        List providers=c.getList("tmProviders");
        int count=1;
        for(Object o:providers) {
            if(!(o instanceof Map<?, ?>)) throw new ConfigurationException("tmProvider has to be a Map and not a "+o.getClass());
            Map<String, Object> m=(Map<String, Object>)o;
            String className=YConfiguration.getString(m, "class");
            Object args=null;
            if(m.containsKey("args")) {
                args=m.get("args");
            } else if(m.containsKey("spec")) {
                args=m.get("spec");
            }
            String name="tm"+count;
            if(m.containsKey("name")) {
                name=m.get("name").toString();
            }
            boolean enabledAtStartup=true;
            if(m.containsKey("enabledAtStartup")) {
                enabledAtStartup=YConfiguration.getBoolean(m, "enabledAtStartup"); 
            }
            String streamName=YConfiguration.getString(m, "stream");
            Stream s=ydb.getStream(streamName);
            if(s==null) {
                throw new ConfigurationException("Cannot find stream '"+streamName+"'");
            }
            final Stream stream=s;
            YObjectLoader<TmPacketSource> objloader=new YObjectLoader<TmPacketSource>();
        
            TmPacketSource prov= null;
            if(args!=null) {
                prov = objloader.loadObject(className, archiveInstance, name, args);
            } else {
                prov = objloader.loadObject(className, archiveInstance, name);
            }
        
            if(!enabledAtStartup) prov.disable();
        
            prov.setTmSink(new TmSink() {
                @Override
                public void processPacket(PacketWithTime pwrt) {
                    long time= pwrt.getGenerationTime();
                    byte[] pkt = pwrt.getPacket();
                    int apidSeqCount = ByteBuffer.wrap(pkt).getInt(0);
                    Tuple t=new Tuple(TM_TUPLE_DEFINITION, new Object[] {time, apidSeqCount, pwrt.rectime, pkt });
                    stream.emitTuple(t);
                }
            });
        
            tmproviders.add(prov);
            ManagementService.getInstance().registerLink(archiveInstance, name, streamName, args!=null?args.toString():"",  prov);
            count++;
        }
    }

    
    @Override
    protected void doStart() {
        for(TmPacketSource prov:tmproviders) {
            prov.startAsync();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for(TmPacketSource prov:tmproviders) {
            prov.stopAsync();
        }
        notifyStopped();
    }
}
