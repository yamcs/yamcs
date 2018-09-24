package org.yamcs.parameter;

import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info = "please use the LocalParameterManager instead of SoftwareParameterManager")
public class SoftwareParameterManager extends LocalParameterManager {

    public SoftwareParameterManager(String yamcsInstance) {
        super(yamcsInstance);
    }

}
