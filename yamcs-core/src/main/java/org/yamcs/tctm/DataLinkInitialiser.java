package org.yamcs.tctm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;
import org.yamcs.api.YamcsApiException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

/**
 * Service that initialises all the data links
 *
 * @author nm
 *
 */
public class DataLinkInitialiser extends AbstractService implements YamcsService {
    TmDataLinkInitialiser tmDataLinkInitialiser;
    TcDataLinkInitialiser tcDataLinkInitialiser;
    ParameterDataLinkInitialiser ppDataLinkInitialiser;
    ServiceManager serviceManager;

    public DataLinkInitialiser(String yamcsInstance)
            throws ConfigurationException, StreamSqlException, ParseException, YamcsApiException, IOException {
        YConfiguration c = YConfiguration.getConfiguration("yamcs." + yamcsInstance);
        List<Service> services = new ArrayList<>();
        if (c.containsKey(TmDataLinkInitialiser.KEY_tmDataLinks)) {
            tmDataLinkInitialiser = new TmDataLinkInitialiser(yamcsInstance);
            services.add(tmDataLinkInitialiser);
        }
        if (c.containsKey(TcDataLinkInitialiser.KEY_tcDataLinks)) {
            tcDataLinkInitialiser = new TcDataLinkInitialiser(yamcsInstance);
            services.add(tcDataLinkInitialiser);
        }
        if (c.containsKey(ParameterDataLinkInitialiser.KEY_PARAMETER_DATA_LINKS)) {
            ppDataLinkInitialiser = new ParameterDataLinkInitialiser(yamcsInstance);
            services.add(ppDataLinkInitialiser);
        }
        if (!services.isEmpty()) {
            serviceManager = new ServiceManager(services);
        }
    }

    @Override
    protected void doStart() {
        if (serviceManager != null) {
            serviceManager.startAsync();
            serviceManager.awaitHealthy();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        if (serviceManager != null) {
            serviceManager.stopAsync();
            serviceManager.awaitStopped();
        }
        notifyStopped();
    }
}
