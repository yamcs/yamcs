package org.yamcs.http.api;

import org.yamcs.YamcsServerInstance;

public class ManagementApi {

    /**
     * Deprecated, use {@link InstancesApi#verifyInstance(String, boolean) instead
     */
    @Deprecated
    public static String verifyInstance(String yamcsInstance, boolean allowGlobal) {
        return InstancesApi.verifyInstance(yamcsInstance, allowGlobal);
    }

    /**
     * Deprecated, use {@link InstancesApi#verifyInstance(String) instead
     */
    @Deprecated
    public static String verifyInstance(String yamcsInstance) {
        return InstancesApi.verifyInstance(yamcsInstance);
    }

    /**
     * Deprecated, use {@link InstancesApi#verifyInstanceObj(String) instead
     */
    @Deprecated
    public static YamcsServerInstance verifyInstanceObj(String yamcsInstance) {
        return InstancesApi.verifyInstanceObj(yamcsInstance);
    }
}
