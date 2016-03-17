package org.yamcs.tctm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hornetq.api.core.HornetQException;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
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
public class DataLinkInitialiser extends AbstractService {
    TmDataLinkInitialiser tmDataLinkInitialiser;
    TcUplinkerAdapter tcDataLinkInitialiser;
    PpProviderAdapter ppDataLinkInitialiser;
    ServiceManager  serviceManager;
    
    public DataLinkInitialiser(String yamcsInstance) throws ConfigurationException, StreamSqlException, ParseException, HornetQException, YamcsApiException, IOException {
        YConfiguration c = YConfiguration.getConfiguration("yamcs."+yamcsInstance);
        List<Service> services = new ArrayList<Service>();
        if(c.containsKey(TmDataLinkInitialiser.KEY_tmProviders)) {
            tmDataLinkInitialiser = new TmDataLinkInitialiser(yamcsInstance);
            services.add(tmDataLinkInitialiser);
        }
        if(c.containsKey(TcUplinkerAdapter.KEY_tcUplinkers)) {
            tcDataLinkInitialiser = new TcUplinkerAdapter(yamcsInstance);
            services.add(tcDataLinkInitialiser);
        }
        if(c.containsKey(PpProviderAdapter.KEY_ppProviders)) {
            ppDataLinkInitialiser = new PpProviderAdapter(yamcsInstance);
            services.add(ppDataLinkInitialiser);
        }
        if(services.isEmpty()) {
            throw new ConfigurationException("None of the "+TmDataLinkInitialiser.KEY_tmProviders+", "+TcUplinkerAdapter.KEY_tcUplinkers+" or "+PpProviderAdapter.KEY_ppProviders+" configured");
        }
        serviceManager = new ServiceManager(services);
    }

    @Override
    protected void doStart() {
        serviceManager.startAsync();
        serviceManager.awaitHealthy();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        serviceManager.stopAsync();
        serviceManager.awaitStopped();
        notifyStopped();
    }
}
