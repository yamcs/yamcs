package org.yamcs.tctm;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.api.YamcsApiException;

/**
 * Sends commands from the yarch stream to a {@link org.yamcs.tctm.TcDataLink}
 *
 */
public class TcDataLinkInitialiser extends AbstractService {
    private Map<String, TcDataLink> tclinks= new HashMap<>();
    final String yamcsInstance;
    public static final String KEY_tcDataLinks = "tcDataLinks";
    public static final String KEY_tcUplinkers = "tcUplinkers";
    
    public static final  String CMDHIST_TUPLE_COL_CMDNAME = "cmdName";
    
    
    static public final TupleDefinition TC_TUPLE_DEFINITION=new TupleDefinition();
    //this is the commandId (used as the primary key when recording), the rest will be handled dynamically
    static {
	TC_TUPLE_DEFINITION.addColumn("gentime", DataType.TIMESTAMP);
	TC_TUPLE_DEFINITION.addColumn("origin", DataType.STRING);
	TC_TUPLE_DEFINITION.addColumn("seqNum", DataType.INT);
	TC_TUPLE_DEFINITION.addColumn(CMDHIST_TUPLE_COL_CMDNAME, DataType.STRING);
    }
    public static final String REALTIME_TC_STREAM_NAME="tc_realtime";

    @SuppressWarnings({ "rawtypes", "static-access", "unchecked" })
    public TcDataLinkInitialiser(String yamcsInstance) throws StreamSqlException, ParseException, YamcsApiException, IOException {
	this.yamcsInstance=yamcsInstance;
	YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);


	YConfiguration c = YConfiguration.getConfiguration("yamcs."+yamcsInstance);
	List uplinkers = c.containsKey(KEY_tcDataLinks)?c.getList(KEY_tcDataLinks):c.getList(KEY_tcUplinkers);
	int count=1;
	for(Object o:uplinkers) {
	    if(!(o instanceof Map)) {
	        throw new ConfigurationException("uplinker has to be Map and not a "+o.getClass());
	    }
	    Map m=(Map)o;

	    String className=c.getString(m, "class");
            Object args=null;
            if(m.containsKey("args")) {
                args=m.get("args");
            } else if(m.containsKey("spec")) {
                args=m.get("spec");
            }

	    String streamName=c.getString(m, "stream");
	    boolean enabledAtStartup=true;
	    if(m.containsKey("enabledAtStartup")) {
		enabledAtStartup=c.getBoolean(m, "enabledAtStartup"); 
	    }
	    final Stream stream;
	    String name = "tc"+count;
            if(m.containsKey("name")) {
                name=m.get("name").toString();
            }
            if(tclinks.containsKey(name)) {
                throw new ConfigurationException("Instance "+yamcsInstance+": there is already a TC Link by name '"+name+"'");
            }
            
	    if(streamName==null) {
		streamName = REALTIME_TC_STREAM_NAME;
	    }
	    stream = ydb.getStream(streamName);
	    if(stream==null) {
	        throw new ConfigurationException("Cannot find stream '"+streamName+"'");
	    }
	    
	    final TcDataLink tcuplinker = (args==null)?YObjectLoader.loadObject(className, yamcsInstance, name): 
	            YObjectLoader.loadObject(className, yamcsInstance, name, args);
	    if(!enabledAtStartup) {
	        tcuplinker.disable();
	    }

	    stream.addSubscriber(new StreamSubscriber() {
		@Override
		public void onTuple(Stream s, Tuple tuple) {
		    tcuplinker.sendTc(PreparedCommand.fromTuple(tuple));
		}

		@Override
		public void streamClosed(Stream s) {
		    stopAsync();
		}
	    });

	    tcuplinker.setCommandHistoryPublisher(new StreamCommandHistoryPublisher(yamcsInstance));
	    tclinks.put(name, tcuplinker);
	    ManagementService.getInstance().registerLink(yamcsInstance, name, streamName, args!=null?args.toString():"", tcuplinker);
	    count++;
	}
    }

    @Override
    protected void doStart() {
	for(TcDataLink tcuplinker:tclinks.values()) {
	    tcuplinker.startAsync();
	}
	notifyStarted();
    }

    @Override
    protected void doStop() {
        ManagementService mgrsrv =  ManagementService.getInstance();
        for(Map.Entry<String, TcDataLink> me: tclinks.entrySet()) {
            mgrsrv.unregisterLink(yamcsInstance, me.getKey());
            me.getValue().stopAsync();
        }
	notifyStopped();
    }
}
