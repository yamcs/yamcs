package org.yamcs.archive;

import static org.yamcs.tctm.PpProviderAdapter.PP_TUPLE_DEFINITION;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.YConfiguration;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * PpRecorder
 * Records (processed) Parameters 
 * 
 * The base table definition is {@link PpProviderAdapter}
 * @author nm
 *
 */
public class PpRecorder extends AbstractService {

    String yamcsInstance;
    Stream realtimeStream, dumpStream;

    static public final String TABLE_NAME="pp";
    List<String> streams = new ArrayList<String>();
    
    public PpRecorder(String yamcsInstance) {
        this(yamcsInstance, null);
    }
    
    public PpRecorder(String yamcsInstance, Map<String, Object> config) {
        this.yamcsInstance=yamcsInstance;
        YarchDatabase ydb=YarchDatabase.getInstance(yamcsInstance);
        try {
            String cols=PP_TUPLE_DEFINITION.getStringDefinition1();
            if(ydb.getTable(TABLE_NAME)==null) {
                String query="create table "+TABLE_NAME+"("+cols+", primary key(gentime, seqNum)) histogram(ppgroup) partition by time_and_value(gentime"+XtceTmRecorder.getTimePartitioningSchemaSql()+",ppgroup) table_format=compressed";
                ydb.execute(query);
            }
            
            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);            
            if(config==null || !config.containsKey("streams")) {
                List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.param);
                for(StreamConfigEntry sce: sceList){
                    streams.add(sce.getName());
                    ydb.execute("insert into "+TABLE_NAME+" select * from "+sce.getName());
                }
            } else if(config.containsKey("streams")){
                List<String> streamNames = YConfiguration.getList(config, "streams");
                for(String sn: streamNames) {
                    StreamConfigEntry sce = sc.getEntry(StandardStreamType.param, sn);
                    if(sce==null) {
                        throw new ConfigurationException("No stream config found for '"+sn+"'");
                    }
                    streams.add(sce.getName());
                    ydb.execute("insert into "+TABLE_NAME+" select * from "+sce.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConfigurationException("exception when creating pp input stream", e);
        }
    }
    
    

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
        Utils.closeTableWriters(ydb, streams);
        notifyStopped();
    }
    
}
