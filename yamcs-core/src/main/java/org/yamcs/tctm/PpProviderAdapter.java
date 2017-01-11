package org.yamcs.tctm;

import java.io.IOException;

import org.yamcs.ConfigurationException;
import org.yamcs.utils.DeprecationInfo;

@DeprecationInfo(info="Please use the PpDataLinkAdapter instead")
@Deprecated
public class PpProviderAdapter extends ParameterDataLinkInitialiser {

    public PpProviderAdapter(String yamcsInstance) throws IOException,  ConfigurationException {
        super(yamcsInstance);
    }

}
