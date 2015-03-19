package org.yamcs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.yamcs.archive.PpProviderAdapter;
import org.yamcs.archive.TcUplinkerAdapter;
import org.yamcs.archive.TmProviderAdapter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.TokenMgrError;

import com.google.common.util.concurrent.AbstractService;

/**
 * Service run at the very beginning that creates different streams required for Yamcs operation
 * 
 * There are a number of "hardcoded" stream types that can be created without specifying the schema (because the schema has to match the definition expected by other services).
 * 
 * Additional streams can be created by specifying a file containing StreamSQL commands.
 * 
 * 
 * @author nm
 *
 */
public class StreamInitService extends AbstractService{
    //these are the names of the hardcoded schemas
    static final public String CMD_HIST="cmdHist";
    static final public String TM="tm";
    static final public String PARAM="param";
    static final public String TC="tc";
    static final public String EVENT="event";
    static final public String SQLFILE="sqlFile";

    static public List<String> schemas= Arrays.asList(CMD_HIST, TM, PARAM, TC, EVENT, SQLFILE);

    YarchDatabase ydb;
    final String instance;

    public StreamInitService(String instance, Map<String, List<String>> config) throws ConfigurationException {
	for(String k:config.keySet()) {
	    if(!schemas.contains(k)) throw new ConfigurationException("unkwon schema '"+k+"'. Supported schemas are "+schemas);
	}
	this.instance = instance;
	ydb = YarchDatabase.getInstance(instance);
	for(Map.Entry<String, List<String>> m:config.entrySet()) {
	    List<String> streamNameList = m.getValue();
	    String streamType = m.getKey();

	    for(String streamName: streamNameList) {
		try {
		    if(!SQLFILE.equalsIgnoreCase(streamType) && ydb.getStream(streamName)!=null) {
			throw new ConfigurationException("Cannot create stream '"+streamName+"' because it already exists");		
		    }
		    if(CMD_HIST.equalsIgnoreCase(streamType)) {
			createCmdHistoryStream(streamName);
		    } else if(TM.equalsIgnoreCase(streamType)) {
			createTmStream(streamName);
		    } else if(PARAM.equalsIgnoreCase(streamType)) {
			createTmStream(streamName);
		    } else if(TC.equalsIgnoreCase(streamType)) {
			createTcStream(streamName);
		    } else if(EVENT.equalsIgnoreCase(streamType)) {
			createEventStream(streamName);
		    } else if(SQLFILE.equalsIgnoreCase(m.getKey())) {
			loadSqlFile(streamName); //filename in fact
		    }
		} catch (ConfigurationException e) {
		    throw e;
		} catch (Exception e) {
		    if(!SQLFILE.equalsIgnoreCase(m.getKey())) {
			throw new ConfigurationException("Cannot create/load stream of type "+streamType, e);
		    } else {
			throw new ConfigurationException("Cannot load stream sql file "+streamName, e);
		    }
		}
	    }

	}
    }


    @Override
    protected void doStart() {
	notifyStarted();
    }



    private void createEventStream(String streamName) throws StreamSqlException, ParseException  {
	ydb.execute("create stream "+streamName+"(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'))");
    }


    private void createTcStream(String streamName) throws StreamSqlException, ParseException {
	ydb.execute("create stream "+streamName+TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition());
    }


    private void createTmStream(String streamName) throws StreamSqlException, ParseException {
	ydb.execute("create stream "+streamName+TmProviderAdapter.TM_TUPLE_DEFINITION.getStringDefinition());
    }
    
    private void createParamStream(String streamName) throws StreamSqlException, ParseException {
	ydb.execute("create stream "+streamName+PpProviderAdapter.PP_TUPLE_DEFINITION.getStringDefinition());
    }

    private void createCmdHistoryStream(String streamName) throws StreamSqlException, ParseException {
	ydb.execute("create stream "+streamName+TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition());

    }

    private void loadSqlFile(String filename) throws IOException, StreamSqlException, ParseException {
	File f = new File(filename);
	 ExecutionContext context=new ExecutionContext(instance);
	 FileReader reader = new FileReader(f);
	 StreamSqlParser parser=new StreamSqlParser(reader);
	 try {
	     parser.StreamSqlStatement().execute(context);
	 } catch (TokenMgrError e) {
	     throw new ParseException(e.getMessage());
	 }
    }


    @Override
    protected void doStop() {
	notifyStopped();
    }
}
