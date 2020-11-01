package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.yarch.Stream;

public class StreamPbParameterSender extends StreamParameterSender implements ParameterSink {

    public StreamPbParameterSender(String yamcsInstance, Stream stream) {
        super(yamcsInstance, stream);
    }

    @Override
    public void updateParams(long gentime, String group, int seqNum,
            Collection<org.yamcs.protobuf.Pvalue.ParameterValue> params) {
        List<ParameterValue> plist = new ArrayList<>(params.size());
        for (org.yamcs.protobuf.Pvalue.ParameterValue pbv : params) {
            NamedObjectId id = pbv.getId();
            String qualifiedName = id.getName();
            if (id.hasNamespace()) {
                log.trace("Using namespaced name for parameter {} because fully qualified name not available.", id);
            }
            ParameterValue pv = BasicParameterValue.fromGpb(qualifiedName, pbv);
            plist.add(pv);
        }
        updateParameters(gentime, group, seqNum, plist);
    }
    
}
