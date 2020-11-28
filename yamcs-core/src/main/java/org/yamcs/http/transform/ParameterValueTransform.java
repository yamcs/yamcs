package org.yamcs.http.transform;

import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Transforms internal parameter value to protobuf versions setting the fields depending on the profile
 * 
 * @author nm
 *
 */
public interface ParameterValueTransform {
    org.yamcs.protobuf.Pvalue.ParameterValue transform(NamedObjectId id, org.yamcs.parameter.ParameterValue pv);

    default org.yamcs.protobuf.Pvalue.ParameterValue transform(ParameterValueWithId pvwi) {
        return pvwi.toGbpParameterValue();
    }
}
