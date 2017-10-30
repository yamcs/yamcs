package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives event data from Artemis queues and publishes into yamcs streams
 * @author nm
 *
 */
public class ArtemisEventDataLink extends AbstractService {
    String instance;
    ServerLocator locator;
    ClientSession session ;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    
    public ArtemisEventDataLink(String instance) {
        this.instance = instance;
        locator = AbstractArtemisTranslatorService.getServerLocator(instance);
    }
    
    @Override
    protected void doStart() {
        try {
            session = locator.createSessionFactory().createSession();
            EventTupleTranslator translator = new EventTupleTranslator();
            YarchDatabase ydb = YarchDatabase.getInstance(instance);
            StreamConfig sc = StreamConfig.getInstance(instance);
            for(StreamConfigEntry sce: sc.getEntries()) {
                if(sce.getType() == StreamConfig.StandardStreamType.event) {
                    Stream stream = ydb.getStream(sce.getName());
                    String address = instance+"."+sce.getName();
                    String queue = address+"-StreamAdapter";
                    session.createTemporaryQueue(address, queue);
                    ClientConsumer client = session.createConsumer(queue);
                    client.setMessageHandler((msg) -> {
                        try {
                            Tuple tuple=translator.buildTuple(stream.getDefinition(), msg);
                            stream.emitTuple(tuple);
                        } catch(IllegalArgumentException e){
                            log.warn( "{} for message: {}", e.getMessage(), msg);
                        } 
                    });
                }
            }
        } catch (Exception e){
            notifyFailed(e);
        }
        notifyStarted();
    }
    
    
    

    @Override
    protected void doStop() {
        try {
            session.close();
            notifyStopped();
        } catch (ActiveMQException e) {
           notifyFailed(e);
        }
    }

}
