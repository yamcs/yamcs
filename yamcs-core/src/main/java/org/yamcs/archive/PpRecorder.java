package org.yamcs.archive;

import java.io.IOException;

import org.yamcs.ConfigurationException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;


import com.google.common.util.concurrent.AbstractService;

import static org.yamcs.archive.PpProviderAdapter.PP_TUPLE_DEFINITION;


/**
 * PpRecorder
 * Records Parameters 
 * 
 * The base table definition is {@link PpProviderAdapter}
 * @author nm
 *
 */
public class PpRecorder extends AbstractService {

    String archiveInstance;
    Stream realtimeStream, dumpStream;

    static public final String REALTIME_PP_STREAM_NAME="pp_realtime";
    static public final String DUMP_PP_STREAM_NAME="pp_dump";
    static public final String TABLE_NAME="pp";

    public PpRecorder(String archiveInstance) throws IOException, ConfigurationException{
	this.archiveInstance=archiveInstance;
	YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
	try {
	    String cols=PP_TUPLE_DEFINITION.getStringDefinition1();
	    if(ydb.getTable(TABLE_NAME)==null) {
		String query="create table "+TABLE_NAME+"("+cols+", primary key(gentime, seqNum)) histogram(ppgroup) partition by time_and_value(gentime,ppgroup) table_format=compressed";
		ydb.execute(query);
	    }
	    if(ydb.getStream(REALTIME_PP_STREAM_NAME )==null) {
		ydb.execute("create stream "+REALTIME_PP_STREAM_NAME+PP_TUPLE_DEFINITION.getStringDefinition());
	    }
	    if(ydb.getStream(DUMP_PP_STREAM_NAME )==null) {
		ydb.execute("create stream "+DUMP_PP_STREAM_NAME+PP_TUPLE_DEFINITION.getStringDefinition());
	    }

	    ydb.execute("create stream pp_is("+cols+")");
	    ydb.execute("insert into "+TABLE_NAME+" select * from "+REALTIME_PP_STREAM_NAME);
	    ydb.execute("insert into "+TABLE_NAME+" select * from "+DUMP_PP_STREAM_NAME);
	} catch (Exception e) {
	    throw new ConfigurationException("exception when creating pp input stream", e);
	}
    }

    @Override
    protected void doStart() {
	notifyStarted();
    }

    @Override
    protected void doStop() {
	notifyStopped();
    }
}
