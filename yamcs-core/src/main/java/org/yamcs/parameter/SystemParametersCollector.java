package org.yamcs.parameter;

import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info = "The SystemParametersCollector has been renamed to SystemParametersService. Please update your configuration.")
public class SystemParametersCollector extends SystemParametersService {

}
