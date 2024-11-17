package org.yamcs.parameter;

import java.util.List;
import java.util.function.Consumer;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.xtce.Parameter;

/**
 * Combines retrieval from different sources:
 * <ul>
 * <li>Parameter Archive</li>
 * <li>Replays</li>
 * <li>Parameter cache</li>
 * <li>Realtime Parameter Archive filler</li>
 * </ul>
 * 
 */
public class ParameterRetrievalService extends AbstractYamcsService {
    String procName = "realtime";
    ParameterCache pcache;
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        this.procName = config.getString("processor", "realtime");
    }

    @Override
    protected void doStart() {
        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        var proc = ysi.getProcessor(procName);
        pcache = new ArrayParameterCache(yamcsInstance, null);
        System.out.println("-------------- proc: " + proc);

        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    // Called from the PRM
    public void update(List<ParameterValue> pvlist) {
        if (pcache != null) {
            pcache.update(pvlist);
        }
    }

    void retrieveSingle(ParameterWithId pid, ParameterRequest request, Consumer<ParameterValueArray> consumer) {
        // ascending case -> retrieve max possible from the parameter archive
    }
    /**
     * Get all the values from cache for a specific parameters
     * 
     * The parameter are returned in descending order (newest parameter is returned first). Note that you can only all
     * this function if the {@link #hasParameterCache()} returns true.
     * 
     * @param param
     * @return
     */
    public List<ParameterValue> getValuesFromCache(Parameter param) {
        return pcache.getAllValues(param);
    }


}
