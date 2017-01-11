package org.yamcs.tctm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * 
 * Injects processed parameters from PpDataLinks into yamcs streams.
 *
 * To the base definition there is one column for each parameter name with the type PROTOBUF({@link org.yamcs.protobuf.Pvalue.ParameterValue})

 * @author nm
 *
 */
public class ParameterDataLinkInitialiser extends AbstractService {
    public static final String KEY_parameterDataLinks = "parameterDataLinks";
    
    public static final String PARAMETER_TUPLE_COL_RECTIME = "rectime";
    public static final String PARAMETER_TUPLE_COL_SEQ_NUM = "seqNum";
    public static final String PARAMETER_TUPLE_COL_GROUP = "group";
    public static final String PARAMETER_TUPLE_COL_GENTIME = "gentime";
    String yamcsInstance;
    private Collection<ParameterDataLink> parameterDataLinks = new ArrayList<ParameterDataLink>();
    final private Logger log;

    static public final TupleDefinition PARAMETER_TUPLE_DEFINITION = new TupleDefinition();
    //first columns from the PP tuples
    //the actual values are encoded as separated columns (umi_0x010203040506, value) value is ParameterValue
    static {
        PARAMETER_TUPLE_DEFINITION.addColumn(PARAMETER_TUPLE_COL_GENTIME, DataType.TIMESTAMP); //generation time
        PARAMETER_TUPLE_DEFINITION.addColumn(PARAMETER_TUPLE_COL_GROUP, DataType.ENUM); //group - used for partitioning (i.e. splitting the archive in multiple files)
        PARAMETER_TUPLE_DEFINITION.addColumn(PARAMETER_TUPLE_COL_SEQ_NUM, DataType.INT); //sequence number
        PARAMETER_TUPLE_DEFINITION.addColumn(PARAMETER_TUPLE_COL_RECTIME, DataType.TIMESTAMP); //recording time

    } 
    
    static public final DataType PARAMETER_DATA_TYPE = DataType.protobuf(org.yamcs.protobuf.Pvalue.ParameterValue.class.getName());
    final TimeService timeService;
    
    public ParameterDataLinkInitialiser(String yamcsInstance) throws IOException, ConfigurationException {
        this.yamcsInstance = yamcsInstance;
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);

        YConfiguration c = YConfiguration.getConfiguration("yamcs."+yamcsInstance);
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        @SuppressWarnings("rawtypes")
        List providers = c.getList(KEY_parameterDataLinks);
        
        int count=1;
        for(Object o:providers) {
            if(!(o instanceof Map)) throw new ConfigurationException("ppProvider has to be a Map and not a "+o.getClass());
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Map<String, Object> m = (Map)o;
            
            Object args=null;
            if(m.containsKey("args")) {
                args=m.get("args");
            } else if(m.containsKey("config")) {
                args=m.get("config");
            } else if(m.containsKey("spec")) {
                args=m.get("spec");
            }
            String streamName = YConfiguration.getString(m, "stream");
            String providerName="pp"+count;
            boolean enabledAtStartup=true;
            if(m.containsKey("enabledAtStartup")) {
                enabledAtStartup=YConfiguration.getBoolean(m, "enabledAtStartup"); 
            }

            final Stream stream = ydb.getStream(streamName);
            if(stream==null) {
                throw new ConfigurationException("Cannot find stream '"+streamName+"'");
            }


            ParameterDataLink prov = YObjectLoader.loadObject(m, yamcsInstance, providerName);

            if(!enabledAtStartup) prov.disable();

            prov.setParameterSink(new MyPpListener(stream));

            ManagementService.getInstance().registerLink(yamcsInstance, providerName, streamName, args!=null?args.toString():"", prov);
            parameterDataLinks .add(prov);
            count++;
        }
    }

    @Override
    protected void doStart() {
        for(ParameterDataLink prov:parameterDataLinks ) {
            prov.startAsync();
        }
        notifyStarted();
    }	

    static public void main(String[] args) throws Exception {
        new ParameterDataLinkInitialiser("test").startAsync();
    }


    @Override
    protected void doStop() {
        for(ParameterDataLink prov:parameterDataLinks ) {
            prov.stopAsync();
        }
        notifyStopped();

    }


    class MyPpListener implements ParameterSink {
        final Stream stream;
        final DataType paraDataType = DataType.protobuf(org.yamcs.protobuf.Pvalue.ParameterValue.class.getName());
        public MyPpListener(Stream stream) {
            this.stream = stream;
        }


        @Override
        public void updatePps(long gentime, String group, int seqNum, Collection<ParameterValue> params) {
            TupleDefinition tdef = PARAMETER_TUPLE_DEFINITION.copy();
            List<Object> cols=new ArrayList<Object>(4+params.size());
            cols.add(gentime);
            cols.add(group);
            cols.add(seqNum);
            cols.add(timeService.getMissionTime());
            for(ParameterValue pv:params) {
                String qualifiedName = pv.getParameter().getQualifiedName();
                if( qualifiedName == null || qualifiedName.isEmpty() ) {
                    qualifiedName = pv.getParameter().getName();
                    log.trace( "Using namespaced name for PP "+qualifiedName+" because fully qualified name not available." );
                }
                int idx = tdef.getColumnIndex(qualifiedName);
                if(idx!=-1) {
                    log.warn("duplicate value for "+pv.getParameter()+"\nfirst: "+cols.get(idx)+"\n second: "+pv.toGpb(null));
                    continue;
                }
                tdef.addColumn(qualifiedName, paraDataType);
                cols.add(pv.toGpb( NamedObjectId.newBuilder().setName( qualifiedName ).build() ));
            }
            Tuple t=new Tuple(tdef, cols);
            stream.emitTuple(t);
        }
        
        
        @Override
        public void updateParams(long gentime, String group, int seqNum, Collection<org.yamcs.protobuf.Pvalue.ParameterValue> params) {
            TupleDefinition tdef = PARAMETER_TUPLE_DEFINITION.copy();
            List<Object> cols = new ArrayList<Object>(4+params.size());
            cols.add(gentime);
            cols.add(group);
            cols.add(seqNum);
            cols.add(timeService.getMissionTime());
            for(org.yamcs.protobuf.Pvalue.ParameterValue pv:params) {
                NamedObjectId id = pv.getId();
                String qualifiedName = id.getName();
                if(id.hasNamespace()) {
                    log.trace("Using namespaced name for parameter "+id+" because fully qualified name not available.");
                }
                
                int idx=tdef.getColumnIndex(qualifiedName);
                if(idx!=-1) {
                    log.warn("duplicate value for "+id+"\nfirst: "+cols.get(idx)+"\n second: "+pv);
                    continue;
                }
                tdef.addColumn(qualifiedName, paraDataType);
                cols.add(pv);
            }
            Tuple t=new Tuple(tdef, cols);
            stream.emitTuple(t);
        }
    }

}