package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SystemParameterDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;


/**
 * Collects each second system processed parameters from whomever registers and sends them on the sys_var stream
 * 
 *   
 * @author nm
 *
 */
public class SystemParametersCollector extends AbstractService implements Runnable {
    static Map<String,SystemParametersCollector> instances=new HashMap<String,SystemParametersCollector>();
    static long frequencyMillisec=1000;
    List<SystemParametersProducer> providers = new CopyOnWriteArrayList<SystemParametersProducer>();
    final static String PP_GROUP =  "yamcs";
    final static String STREAM_NAME="sys_param";
    ScheduledThreadPoolExecutor timer;
    final Stream stream;
    

    int seqCount = 0;
    final private Logger log;

    final private String namespace;
    final private String serverId;

    final String instance;
    TimeService timeService;
    
    
    static public SystemParametersCollector getInstance(String instance) {
        synchronized(instances) {
            return instances.get(instance);
        }
    }


    public SystemParametersCollector(String instance) throws ConfigurationException {
        this.instance = instance;
       
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+instance+"]");

        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        Stream s=ydb.getStream(STREAM_NAME);
        if(s==null) {
            throw new ConfigurationException("Stream ' "+STREAM_NAME+"' does not exist");
        }
        stream=s;
        timeService = YamcsServer.getInstance(instance).getTimeService();
        
        serverId = YamcsServer.getServerId();
        namespace = SystemParameterDb.YAMCS_SPACESYSTEM_NAME+NameDescription.PATH_SEPARATOR+serverId;
        log.debug("Using {} as serverId, and {} as namespace for system parameters", serverId, namespace);
        
        synchronized(instances) {
            instances.put(instance, this);    
        }
    }

    @Override
    public void doStart() {
        timer = new ScheduledThreadPoolExecutor(1);
        timer.scheduleAtFixedRate(this, 1000L, frequencyMillisec, TimeUnit.MILLISECONDS);
        notifyStarted();
    }
    
    @Override
    public void doStop() {
        timer.shutdown();
        notifyStopped();
    }

    /**
     * Run from the timer, collect all parameters and send them on the stream
     */
    @Override
    public void run() {
        Collection<ParameterValue> params = new ArrayList<ParameterValue>();
        for(SystemParametersProducer p: providers) {
            try {
                Collection<ParameterValue> pvc =p.getSystemParameters();
                params.addAll(pvc);
            } catch (Exception e) {
                log.warn("Error getting parameters from provider "+p, e);
            }
        }
        long gentime = timeService.getMissionTime();
        
        if(params.isEmpty()) return;

        TupleDefinition tdef=PpProviderAdapter.PP_TUPLE_DEFINITION.copy();
        List<Object> cols=new ArrayList<Object>(4+params.size());
        cols.add(gentime);
        cols.add(PP_GROUP);
        cols.add(seqCount);
        cols.add(timeService.getMissionTime());
        for(ParameterValue pv:params) {
            String name = pv.getId().getName();
            int idx=tdef.getColumnIndex(name);
            if(idx!=-1) {
                log.warn("duplicate value for "+pv.getId()+"\nfirst: "+cols.get(idx)+"\n second: "+pv);
                continue;
            }
            tdef.addColumn(name, PpProviderAdapter.PP_DATA_TYPE);
            cols.add(pv);
        }
        Tuple t=new Tuple(tdef, cols);
        stream.emitTuple(t);
    }

    public void registerProvider(SystemParametersProducer p, Collection<Parameter> params) {
        log.debug("Registering system variables provider {}", p);
        providers.add(p);
    }
    
    /**
     * this is the namespace all system variables should be in
     * @return
     */
    public String getNamespace() {
        return namespace;
    }
    
    
    public static ParameterValue getPV(NamedObjectId id, long time, String v) {
        return ParameterValue.newBuilder()
                .setId(id)
                .setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                .setAcquisitionTime(time)
                .setGenerationTime(time)
                .setEngValue(Value.newBuilder().setType(Type.STRING).setStringValue(v).build())
                .build();
    }
    
    

    public static ParameterValue getPV(NamedObjectId id, long time, long v) {
        return ParameterValue.newBuilder()
                .setId(id)
                .setAcquisitionStatus(AcquisitionStatus.ACQUIRED)
                .setAcquisitionTime(time)
                .setGenerationTime(time)
                .setEngValue(Value.newBuilder().setType(Type.SINT64).setSint64Value(v).build())
                .build();
    }
}
