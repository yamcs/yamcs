package org.yamcs.tctm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.api.YamcsApiException;

/**
 * Sends commands from the yarch stream to a {@link org.yamcs.tctm.TcUplinker }
 *
 */
public class TcUplinkerAdapter extends AbstractService {
    private Collection<TcDataLink> tcuplinkers=new ArrayList<TcDataLink>();
    final String yamcsInstance;
    final static public String KEY_tcDataLinks = "tcDataLinks";
    final static public String KEY_tcUplinkers = "tcUplinkers";
    
    
    static public final TupleDefinition TC_TUPLE_DEFINITION=new TupleDefinition();
    //this is the commandId (used as the primary key when recording), the rest will be handled dynamically
    static {
	TC_TUPLE_DEFINITION.addColumn("gentime", DataType.TIMESTAMP);
	TC_TUPLE_DEFINITION.addColumn("origin", DataType.STRING);
	TC_TUPLE_DEFINITION.addColumn("seqNum", DataType.INT);
	TC_TUPLE_DEFINITION.addColumn("cmdName", DataType.STRING);
    }
    static public final String REALTIME_TC_STREAM_NAME="tc_realtime";

    @SuppressWarnings({ "rawtypes", "static-access", "unchecked" })
    public TcUplinkerAdapter(String yamcsInstance) throws ConfigurationException, StreamSqlException, ParseException, YamcsApiException, IOException {
	this.yamcsInstance=yamcsInstance;
	YarchDatabase ydb=YarchDatabase.getInstance(yamcsInstance);

	//new StreamAdapter(realtimeStream, new SimpleString(archiveInstance+".tc.realtime"), new TmTupleTranslator());


	YConfiguration c = YConfiguration.getConfiguration("yamcs."+yamcsInstance);
	List uplinkers = c.containsKey(KEY_tcDataLinks)?c.getList(KEY_tcDataLinks):c.getList(KEY_tcUplinkers);
	int count=1;
	for(Object o:uplinkers) {
	    if(!(o instanceof Map)) throw new ConfigurationException("uplinker has to be Map and not a "+o.getClass());
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
            
	    if(streamName==null) {
		streamName = REALTIME_TC_STREAM_NAME;
	    }
	    stream = ydb.getStream(streamName);
	    if(stream==null) throw new ConfigurationException("Cannot find stream '"+streamName+"'");
	    YObjectLoader<TcDataLink> objloader=new YObjectLoader<TcDataLink>();
	    
	    final TcDataLink tcuplinker = (args==null)?objloader.loadObject(className, yamcsInstance, name): objloader.loadObject(className, yamcsInstance, name, args);
	    if(!enabledAtStartup) tcuplinker.disable();

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

	    tcuplinker.setCommandHistoryPublisher(new YarchCommandHistoryAdapter(yamcsInstance));
	    tcuplinkers.add(tcuplinker);
	    ManagementService.getInstance().registerLink(yamcsInstance, name, streamName, args!=null?args.toString():"", tcuplinker);
	    count++;
	}
    }

    @Override
    protected void doStart() {
	for(TcDataLink tcuplinker:tcuplinkers) {
	    tcuplinker.startAsync();
	}
	notifyStarted();
    }

    @Override
    protected void doStop() {
	for(TcDataLink tcuplinker:tcuplinkers) {
	    tcuplinker.stopAsync();
	}
	notifyStopped();
    }
}
