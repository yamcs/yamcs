package org.yamcs.parameter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.YProcessor;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ParameterTypeProcessor;

import com.google.common.util.concurrent.AbstractService;

/**
 * Implements software parameters - these are parameters that can be set from the clients.
 * 
 * 
 * All the parameters are send from the executor thread.
 * 
 * @author nm
 *
 */
public class SoftwareParameterManager extends AbstractService implements ParameterProvider {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    ParameterRequestManager prm;
    private NamedDescriptionIndex<Parameter> params = new NamedDescriptionIndex<Parameter>();
    Set<Parameter> subscribedParams = new HashSet<Parameter>();
    private static final Logger log=LoggerFactory.getLogger(SoftwareParameterManager.class);
    final String yamcsInstance;
    YProcessor yproc;

    public SoftwareParameterManager(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    public void init(XtceDb xtcedb) {
        for(Parameter p:xtcedb.getParameters()) {
            if(p.getDataSource() == DataSource.LOCAL) {
                params.add(p);
            }
        }
        log.debug("Found "+params.size()+" local parameters");
    }

    @Override
    public void init(YProcessor yproc) throws ConfigurationException {
        init(yproc.getXtceDb());
        this.yproc = yproc;
    }

    @Override
    public void setParameterListener(ParameterRequestManager parameterListener) {
        this.prm = parameterListener;
    }

    //called on the execution thread to update
    // TODO: convert from raw to engineering values
    private void doUpdate(final List<org.yamcs.protobuf.Pvalue.ParameterValue> gpvList) {
        ParameterValueList pvlist = new ParameterValueList();
        for(org.yamcs.protobuf.Pvalue.ParameterValue gpv: gpvList) {
            Parameter p = getParam(gpv.getId());
            if(subscribedParams.contains(p)) {
                org.yamcs.parameter.ParameterValue pv =  org.yamcs.parameter.ParameterValue.fromGpb(p, gpv);
                long t;
                if(yproc!=null) {
                    t=yproc.getCurrentTime();
                } else {
                    t = TimeEncoding.getWallclockTime();
                }
		
                if(gpv.hasAcquisitionStatus()) {
                    pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
                }
                if(!gpv.hasProcessingStatus()) {
                    pv.setProcessingStatus(true);
                }
                if(!gpv.hasGenerationTime()) {
                    pv.setGenerationTime(t);
                }
                if(!gpv.hasAcquisitionTime()) {
                    pv.setAcquisitionTime(t);
                }
                pvlist.add(pv);
            }
        }
        if(pvlist.size()>0) {
            prm.update(pvlist);
        }
    }

    /**
     * update the list of parameters.
     *  - resolves NamedObjectId -&gt; Parameter 
     *  - sends the result to PRM
     */
    public void updateParameters(final List<org.yamcs.protobuf.Pvalue.ParameterValue> gpvList) {
        //first validate that the names are sofware parameters and the types match
        for(org.yamcs.protobuf.Pvalue.ParameterValue gpv: gpvList) {
            Parameter p = getParam(gpv.getId());
            if(p==null) {
                throw new IllegalArgumentException("Cannot find a local(software) parameter for '"+gpv.getId()+"'");
            }
            ParameterTypeProcessor.checkEngValueAssignment(p, ValueUtility.fromGpb(gpv.getEngValue()));
        }
        //then filter out the subscribed ones and send it to PRM
        executor.submit(new Runnable() {
            @Override
            public void run() {
                doUpdate(gpvList);
            }
        });
    }

    /**
     *  Updates a parameter just with the engineering value
     */
    public void updateParameter(final Parameter p, final Value engValue) {
        if(p.getDataSource()!=DataSource.LOCAL) {
            throw new IllegalArgumentException("DataSource of parameter "+p.getQualifiedName()+" is not local");
        }
        ParameterTypeProcessor.checkEngValueAssignment(p, engValue);
        executor.submit(new Runnable() {
        @Override
        public void run() {
            ParameterValue pv = new ParameterValue(p);
            pv.setEngineeringValue(engValue);
            long t = yproc.getCurrentTime();
            pv.setAcquisitionTime(t);
            pv.setGenerationTime(t);
            pv.setProcessingStatus(true);
            prm.update(Arrays.asList(pv));
        }
    });
    }

    @Override
    public void startProviding(final Parameter paramDef) {
        log.debug("requested to provide {}", paramDef.getQualifiedName());
        executor.submit(new Runnable() {
            @Override
            public void run() {
                subscribedParams.add(paramDef);
            }
        });
    }

    @Override
    public void startProvidingAll() {
        log.debug("requested to provide all");

        executor.submit(new Runnable() {
            @Override
            public void run() {
                for(Parameter p:params) {
                    subscribedParams.add(p);
                }
            }
        });
    }

    @Override
    public void stopProviding(final Parameter paramDef) {
        log.debug("requested to stop providing {}", paramDef.getQualifiedName());

        executor.submit(new Runnable() {
            @Override
            public void run() {
                subscribedParams.remove(paramDef);
            }
        });
    }

    @Override
    public boolean canProvide(NamedObjectId paraId) {
        return getParam(paraId) != null;
    }

    private Parameter getParam(NamedObjectId paraId) {
        Parameter p = null;
        if(paraId.hasNamespace()) {
            p = params.get(paraId.getNamespace(), paraId.getName());
        } else {
            p = params.get(paraId.getName());
        }
        return p;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        Parameter p = getParam(paraId);
        if(p==null) {
            log.info("throwing InvalidIdentification becasue cannot provide {}", paraId);
            throw new InvalidIdentification(paraId);
        }
        return p;
    }

    @Override
    public boolean canProvide(Parameter param) {
        return params.get(param.getQualifiedName())!=null;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        executor.shutdown();
        notifyStopped();
    }
}
