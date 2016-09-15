package org.yamcs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.tctm.TcUplinkerAdapter;
import org.yamcs.tctm.TmDataLinkInitialiser;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

/**
 *  Run at the very beginning of Yamcs startup; creates different streams required for Yamcs operation
 * 
 * There are a number of "hardcoded" stream types that can be created without specifying the schema (because the schema has to match the definition expected by other services).
 * 
 * Additional streams can be created by specifying a file containing StreamSQL commands.
 * 
 * 
 * @author nm
 *
 */
public class StreamInitializer {
    private static final Logger log = LoggerFactory.getLogger(StreamInitializer.class);
    final String yamcsInstance;
    YarchDatabase ydb;
  
    public static void createStreams(String yamcsInstance) throws StreamSqlException, ParseException, IOException {
        StreamInitializer si = new StreamInitializer(yamcsInstance);
        si.createStreams();
    }
    
    
    public StreamInitializer(String yamcsInstance) throws ConfigurationException {
        ydb = YarchDatabase.getInstance(yamcsInstance);
        this.yamcsInstance = yamcsInstance;
    }
    
    public void createStreams() throws StreamSqlException, ParseException, IOException {
        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        for(StreamConfigEntry sce: sc.getEntries()) {
            if(sce.type == StreamConfig.StandardStreamType.cmdHist) {
                createCmdHistoryStream(sce.name);
            } else if(sce.type == StreamConfig.StandardStreamType.tm) {
                createTmStream(sce.name);
            } else if(sce.type == StreamConfig.StandardStreamType.param) {
                createParamStream(sce.name);
            } else if(sce.type == StreamConfig.StandardStreamType.tc) {
                createTcStream(sce.name);
            } else if(sce.type == StreamConfig.StandardStreamType.event) {
                createEventStream(sce.name);
            } else if(sce.type == StreamConfig.StandardStreamType.alarm) {
                createAlarmStream(sce.name);
            } else if(sce.type == StreamConfig.StandardStreamType.sqlFile) {
                loadSqlFile(sce.name); //filename in fact
            } else {
                throw new RuntimeException("Unknown stream type "+sce.type);
            }
        }
      
    }

    private void createEventStream(String streamName) throws StreamSqlException, ParseException  {
        ydb.execute("create stream "+streamName+"(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'))");
    }


    private void createTcStream(String streamName) throws StreamSqlException, ParseException {
        ydb.execute("create stream "+streamName+TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition());
    }


    private void createTmStream(String streamName) throws StreamSqlException, ParseException {
        ydb.execute("create stream "+streamName+TmDataLinkInitialiser.TM_TUPLE_DEFINITION.getStringDefinition());
    }

    private void createParamStream(String streamName) throws StreamSqlException, ParseException {
        ydb.execute("create stream "+streamName+PpProviderAdapter.PP_TUPLE_DEFINITION.getStringDefinition());
    }

    private void createCmdHistoryStream(String streamName) throws StreamSqlException, ParseException {
        ydb.execute("create stream "+streamName+TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition());

    }

    private void createAlarmStream(String streamName) throws StreamSqlException, ParseException  {
        ydb.execute("create stream " + streamName + AlarmServer.ALARM_TUPLE_DEFINITION.getStringDefinition());
    }


    private void loadSqlFile(Object o) throws IOException, StreamSqlException, ParseException {
        if(!(o instanceof String)) {
            throw new ConfigurationException("Expected to have a filename to load as SQL File");
        }
        log.debug("Loading SQL File {}", o);
        String filename = (String)o;
        File f = new File(filename);
        ExecutionContext context=new ExecutionContext(yamcsInstance);
        FileReader reader = new FileReader(f);
        StreamSqlParser parser=new StreamSqlParser(reader);
        try {
            StreamSqlStatement stmt;
            while((stmt = parser.StreamSqlStatement())!=null) {
                stmt.execute(context); 
            }
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }
}
