package org.yamcs.http.transform;

import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class YamcsWebTransform {
    static final ParameterValueTransform PV_TRANSFORM = new YamcsWebPvTransform();
    
    static class YamcsWebProfile implements TransformProfile {
        @Override
        public ParameterValueTransform getParameterValueTransform() {
            return PV_TRANSFORM;
        }
    }
    
    static class YamcsWebPvTransform implements ParameterValueTransform {
        @Override
        public ParameterValue transform(NamedObjectId id, org.yamcs.parameter.ParameterValue pv) {
            return pv.toGpb(id);
        }
    }
}
