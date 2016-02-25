package org.yamcs.parameterarchive;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.YConfiguration;
import org.yamcs.YProcessor;

public class RealtimeArchiveFiller extends ArchiveFillerTask {
    ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(1);
    int flushInterval = 300; //seconds
    protected Logger log = LoggerFactory.getLogger(this.getClass());
    String processorName = "realtime";
    final String yamcsInstance;
    
    public RealtimeArchiveFiller(ParameterArchive parameterArchive, Map<String, Object> config) {
        super(parameterArchive);
        this.yamcsInstance = parameterArchive.getYamcsInstance();
        if(config!=null) {
            parseConfig(config);
        }
    }
    
    private void parseConfig(Map<String, Object> config) {
        flushInterval = YConfiguration.getInt(config, "flushInterval", flushInterval);      
        processorName = YConfiguration.getString(config, "processorName", processorName);
    }
    
    
    
    //move the updateItems (and all processing thereafter) in the executor thread
    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        executor.execute(()-> {
            super.updateItems(subscriptionId, items);
        });
    }
    
    public void flush() {
        log.debug("Flushing {} segments", pgSegments.values().size());
        for(Map<Integer, PGSegment> m :pgSegments.values()) {
            consolidateAndWriteToArchive(m.values());
        }
    }
    
    
    void start() {
        //subscribe to the realtime processor
        YProcessor yprocessor = YProcessor.getInstance(yamcsInstance, processorName);
        if(yprocessor == null) {
            throw new ConfigurationException("No processor named '"+processorName+"' in instance "+yamcsInstance);
        }
        yprocessor.getParameterRequestManager().subscribeAll(this);
        executor.scheduleAtFixedRate(this::flush, flushInterval, flushInterval, TimeUnit.SECONDS);
        
    }
    
    void stop() {
        executor.shutdown();
        flush();
    }
}
