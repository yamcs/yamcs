package org.yamcs.parameter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ParameterTypeUtils;

import com.google.common.util.concurrent.AbstractService;

/**
 * Implements software parameters - these are parameters that can be set from the clients.
 * 
 * 
 * All the parameters are sent from the executor thread.
 * 
 * @author nm
 *
 */
public class LocalParameterManager extends AbstractService implements SoftwareParameterManagerIf, ParameterProvider {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    private List<ParameterListener> parameterListeners = new CopyOnWriteArrayList<>();
    private NamedDescriptionIndex<Parameter> params = new NamedDescriptionIndex<>();
    Set<Parameter> subscribedParams = new HashSet<>();
    private static final Logger log = LoggerFactory.getLogger(LocalParameterManager.class);
    final String yamcsInstance;
    Processor proc;

    public LocalParameterManager(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    public void init(XtceDb xtcedb) {
        for (Parameter p : xtcedb.getParameters()) {
            if (p.getDataSource() == DataSource.LOCAL) {
                params.add(p);
            }
        }
        log.debug("Found {} local parameters", params.size());
    }

    @Override
    public void init(Processor proc) throws ConfigurationException {
        init(proc.getXtceDb());
        this.proc = proc;
        ParameterRequestManager prm = proc.getParameterRequestManager();
        prm.addParameterProvider(this);
        prm.addSoftwareParameterManager(DataSource.LOCAL, this);
    }

    @Override
    public void setParameterListener(ParameterListener parameterListener) {
        parameterListeners.add(parameterListener);
    }

    public void addParameterListener(ParameterListener parameterListener) {
        parameterListeners.add(parameterListener);
    }

    // called on the execution thread to update
    // TODO: convert from raw to engineering values
    private void doUpdate(final List<ParameterValue> gpvList) {
        ParameterValueList pvlist = new ParameterValueList();
        for (ParameterValue pv : gpvList) {
            Parameter p = pv.getParameter();
            if (subscribedParams.contains(p)) {
                long t;
                if (proc != null) {
                    t = proc.getCurrentTime();
                } else {
                    t = TimeEncoding.getWallclockTime();
                }


                if (pv.getGenerationTime() == TimeEncoding.INVALID_INSTANT) {
                    pv.setGenerationTime(t);
                }
                if (pv.getAcquisitionTime() == TimeEncoding.INVALID_INSTANT) {
                    pv.setAcquisitionTime(t);
                }
                pvlist.add(pv);
            }
        }
        if (pvlist.size() > 0) {
            parameterListeners.forEach(l -> l.update(pvlist));
        }
    }

    /**
     * update the list of parameters. - resolves NamedObjectId -&gt; Parameter - sends the result to PRM
     */
    public void updateParameters(final List<ParameterValue> gpvList) {
        // first validate that the names are sofware parameters and the types match
        for (ParameterValue pv : gpvList) {
            Parameter p = pv.getParameter();
            ParameterTypeUtils.checkEngValueAssignment(p, pv.getEngValue());
        }
        // then filter out the subscribed ones and send it to PRM
        executor.submit(() -> doUpdate(gpvList));
    }

    /**
     * Updates a parameter just with the engineering value
     */
    public void updateParameter(final Parameter p, final Value engValue) {
        if (p.getDataSource() != DataSource.LOCAL) {
            throw new IllegalArgumentException("DataSource of parameter " + p.getQualifiedName() + " is not local");
        }
        ParameterTypeUtils.checkEngValueAssignment(p, engValue);
        executor.submit(() -> {
            ParameterValue pv = new ParameterValue(p);
            pv.setEngineeringValue(engValue);
            long t = proc.getCurrentTime();
            pv.setAcquisitionTime(t);
            pv.setGenerationTime(t);

            List<ParameterValue> wrapped = Arrays.asList(pv);
            parameterListeners.forEach(l -> l.update(wrapped));
        });
    }

    @Override
    public void startProviding(final Parameter paramDef) {
        log.debug("requested to provide {}", paramDef.getQualifiedName());
        executor.submit(() -> subscribedParams.add(paramDef));
    }

    @Override
    public void startProvidingAll() {
        log.debug("requested to provide all");

        executor.submit(() -> {
            for (Parameter p : params) {
                subscribedParams.add(p);
            }
        });
    }

    @Override
    public void stopProviding(final Parameter paramDef) {
        log.debug("requested to stop providing {}", paramDef.getQualifiedName());
        executor.submit(() -> subscribedParams.remove(paramDef));
    }

    @Override
    public boolean canProvide(NamedObjectId paraId) {
        return getParam(paraId) != null;
    }

    private Parameter getParam(NamedObjectId paraId) {
        Parameter p;
        if (paraId.hasNamespace()) {
            p = params.get(paraId.getNamespace(), paraId.getName());
        } else {
            p = params.get(paraId.getName());
        }
        return p;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        Parameter p = getParam(paraId);
        if (p == null) {
            log.info("throwing InvalidIdentification because cannot provide {}", paraId);
            throw new InvalidIdentification(paraId);
        }
        return p;
    }

    @Override
    public boolean canProvide(Parameter param) {
        return params.get(param.getQualifiedName()) != null;
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
