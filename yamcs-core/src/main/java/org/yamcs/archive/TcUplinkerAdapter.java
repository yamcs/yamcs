package org.yamcs.archive;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.hornetq.api.core.HornetQException;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.YarchCommandHistoryAdapter;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.ManagementService;
import org.yamcs.tctm.TcUplinker;
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
import org.yamcs.api.YamcsClient;

/**
 * Sends commands from the yarch stream to a {@link org.yamcs.tctm.TcUplinker }
 *
 */
public class TcUplinkerAdapter extends AbstractService {
	private Collection<TcUplinker> tcuplinkers=new ArrayList<TcUplinker>();
	final String archiveInstance;

	static public final TupleDefinition TC_TUPLE_DEFINITION=new TupleDefinition();
	//this is the commandId (used as the primary key when recording), the rest will be handled dynamically
	static {
		TC_TUPLE_DEFINITION.addColumn("gentime", DataType.TIMESTAMP);
		TC_TUPLE_DEFINITION.addColumn("origin", DataType.STRING);
		TC_TUPLE_DEFINITION.addColumn("seqNum", DataType.INT);
		TC_TUPLE_DEFINITION.addColumn("cmdName", DataType.STRING);
	}
	static public final String REALTIME_TC_STREAM_NAME="tc_realtime";
	

	final private Stream realtimeStream;
	YamcsClient yclient;
	
	public TcUplinkerAdapter(String archiveInstance) throws ConfigurationException, StreamSqlException, ParseException, HornetQException, YamcsApiException, IOException {
		this.archiveInstance=archiveInstance;
		YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
		ydb.execute("create stream "+REALTIME_TC_STREAM_NAME+TC_TUPLE_DEFINITION.getStringDefinition());
		
		realtimeStream=ydb.getStream(TcUplinkerAdapter.REALTIME_TC_STREAM_NAME);

		//new StreamAdapter(realtimeStream, new SimpleString(archiveInstance+".tc.realtime"), new TmTupleTranslator());


        YConfiguration c=YConfiguration.getConfiguration("yamcs."+archiveInstance);
        List uplinkers=c.getList("tcUplinkers");
        int count=1;
        for(Object o:uplinkers) {
            if(!(o instanceof Map)) throw new ConfigurationException("uplinker has to be Map and not a "+o.getClass());
            Map m=(Map)o;
            String className=c.getString(m, "class");
            String spec=c.getString(m, "spec");
            String streamName=c.getString(m, "stream");
            boolean enabledAtStartup=true;
            if(m.containsKey("enabledAtStartup")) {
               enabledAtStartup=c.getBoolean(m, "enabledAtStartup"); 
            }
			final Stream stream;

			if(streamName!=null) {
				if(streamName.equals(REALTIME_TC_STREAM_NAME)) {
					stream=realtimeStream;
				} else {
					throw new ConfigurationException("Stream '"+streamName+"' unkown. Only "+REALTIME_TC_STREAM_NAME+" supported for the moment.");
				}
			} else {
				stream=realtimeStream;
			}
			final TcUplinker tcuplinker=loadTcUplinker(className, spec);
			if(!enabledAtStartup) tcuplinker.disable();
			
			stream.addSubscriber(new StreamSubscriber() {
                @Override
                public void onTuple(Stream s, Tuple tuple) {
                    tcuplinker.sendTc(PreparedCommand.fromTuple(tuple));
                }
                
                @Override
                public void streamClosed(Stream s) {
                    stop();
                }
            });
			
			tcuplinker.setCommandHistoryListener(new YarchCommandHistoryAdapter(archiveInstance));
			tcuplinkers.add(tcuplinker);
			ManagementService.getInstance().registerLink(archiveInstance, "tc"+count, streamName, spec,  tcuplinker);
			count++;
		}
	}
	
	private TcUplinker loadTcUplinker(String className, String spec) throws ConfigurationException, IOException {
		try {
			Class<TcUplinker> ic=(Class<TcUplinker>)Class.forName(className);
			Constructor<TcUplinker> c=ic.getConstructor(String.class, String.class);
			return c.newInstance(archiveInstance, spec);
		} catch (InvocationTargetException e) {
			Throwable t=e.getCause();
			if(t instanceof ConfigurationException) {
				throw (ConfigurationException)t;
			} else if(t instanceof IOException) {
				throw (IOException)t;
			} else {
				throw new ConfigurationException("Cannot instantiate tc uplinker from class " +className, t);
			}

		} catch (Exception e) {
			throw new ConfigurationException("Cannot instantiate tc uplinker from class "+className+": "+e);
		}
	}

	@Override
	protected void doStart() {
		for(TcUplinker tcuplinker:tcuplinkers) {
		    tcuplinker.start();
		}
		notifyStarted();
	}

	@Override
	protected void doStop() {
		for(TcUplinker tcuplinker:tcuplinkers) {
		    tcuplinker.stop();
		}
		notifyStopped();
	}
}
