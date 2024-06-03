package org.yamcs.algorithms;

import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;

import com.google.common.util.concurrent.AbstractService;

//used to avoid errors from prm that no provider for parameters
// in realtiy parameters are handed manually to the algorithm manager
class MyParaProvider extends AbstractService implements ParameterProvider {

    @Override
    public void init(Processor processor, YConfiguration config, Object spec) {
        processor.getParameterProcessorManager().addParameterProvider(this);
    }

    @Override
    public void setParameterProcessor(ParameterProcessor parameterProcessor) {
    }

    @Override
    public void startProviding(Parameter paramDef) {
    }

    @Override
    public void startProvidingAll() {

    }

    @Override
    public void stopProviding(Parameter paramDef) {
    }

    @Override
    public boolean canProvide(NamedObjectId paraId) {
        return true;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        return XtceAlgorithmTest.mdb.getParameter(paraId.getName());
    }

    @Override
    public boolean canProvide(Parameter param) {
        return true;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStarted();
    }

}
