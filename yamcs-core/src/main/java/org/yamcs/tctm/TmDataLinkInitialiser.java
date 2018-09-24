package org.yamcs.tctm;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Loads multiple {@link TmPacketDataLink} and injects all the packets into a defined stream.
 * 
 * 
 * @author nm
 *
 */
public class TmDataLinkInitialiser extends AbstractService {
    final static public String KEY_tmDataLinks = "tmDataLinks";
    
    private Map<String, TmPacketDataLink> tmlinks=new HashMap<>();
    final String yamcsInstance;
    final static public String GENTIME_COLUMN = "gentime";
    final static public String SEQNUM_COLUMN = "seqNum";
    final static public String RECTIME_COLUMN = "rectime";
    final static public String PACKET_COLUMN = "packet";

    static public final TupleDefinition TM_TUPLE_DEFINITION=new TupleDefinition();
    static {
        TM_TUPLE_DEFINITION.addColumn(GENTIME_COLUMN, DataType.TIMESTAMP);
        TM_TUPLE_DEFINITION.addColumn(SEQNUM_COLUMN, DataType.INT);
        TM_TUPLE_DEFINITION.addColumn(RECTIME_COLUMN, DataType.TIMESTAMP); //reception or recording time (useful in case we import data from other recordings which provide this) 
        TM_TUPLE_DEFINITION.addColumn(PACKET_COLUMN, DataType.BINARY);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public TmDataLinkInitialiser(String yamcsInstance) throws ConfigurationException, IOException {
        this.yamcsInstance = yamcsInstance;
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        
        YConfiguration c = YConfiguration.getConfiguration("yamcs."+yamcsInstance);
        
        List<?> providers = c.getList(KEY_tmDataLinks);
        int count=1;
        ManagementService mgrsrv =  ManagementService.getInstance();
        for(Object o:providers) {
            if(!(o instanceof Map<?, ?>)) {
                throw new ConfigurationException("tmProvider has to be a Map and not a "+o.getClass());
            }
            Map<String, Object> m=(Map<String, Object>)o;
            String className=YConfiguration.getString(m, "class");
            Object args=null;
            if(m.containsKey("args")) {
                args=m.get("args");
            } else if(m.containsKey("spec")) {
                args=m.get("spec");
            }
            String name = "tm"+count;
            if(m.containsKey("name")) {
                name = m.get("name").toString();
            }
            if(tmlinks.containsKey(name)) {
                throw new ConfigurationException("Instance "+yamcsInstance+": there is already a TM Link by name '"+name+"'");
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
        
            TmPacketDataLink prov= null;
            if(args!=null) {
                prov = YObjectLoader.loadObject(className, yamcsInstance, name, args);
            } else {
                prov = YObjectLoader.loadObject(className, yamcsInstance, name);
            }
        
            if(!enabledAtStartup) {
                prov.disable();
            }
            boolean dropCorrupted = YConfiguration.getBoolean(m, "dropCorruptedPackets", true);
            prov.setTmSink(new TmSink() {
                @Override
                public void processPacket(PacketWithTime pwrt) {
                    if(pwrt.isCorrupted() && dropCorrupted) {
                        return;
                    }
                    long time= pwrt.getGenerationTime();
                    byte[] pkt = pwrt.getPacket();
                    Tuple t=new Tuple(TM_TUPLE_DEFINITION, new Object[] {time, pwrt.getSeqCount(), pwrt.getReceptionTime(), pkt });
                    stream.emitTuple(t);
                }
            });
        
            tmlinks.put(name, prov);
            mgrsrv.registerLink(yamcsInstance, name, streamName, args!=null?args.toString():"",  prov);
            count++;
        }
    }

    
    @Override
    protected void doStart() {
        for(TmPacketDataLink prov:tmlinks.values()) {
            prov.startAsync();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        ManagementService mgrsrv =  ManagementService.getInstance();
        for(Map.Entry<String, TmPacketDataLink> me: tmlinks.entrySet()) {
            mgrsrv.unregisterLink(yamcsInstance, me.getKey());
            me.getValue().stopAsync();
        }
        notifyStopped();
    }
}
