package org.yamcs.security;

import java.util.Map;

import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info = "Rename all references to BasicAuthModule to DefaultAuthModule")
public class BasicAuthModule extends DefaultAuthModule {

    public BasicAuthModule(Map<String, Object> config) {
        super(config);
    }
}
