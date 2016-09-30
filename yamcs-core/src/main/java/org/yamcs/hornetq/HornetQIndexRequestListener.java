package org.yamcs.hornetq;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

public class HornetQIndexRequestListener implements IndexRequestListener {
    
    static Logger log=LoggerFactory.getLogger(HornetQIndexRequestListener.class);
    SimpleString dataAddress;
    YamcsSession yamcsSession;
    YamcsClient yamcsClient;
    private boolean first;
    
    public HornetQIndexRequestListener(SimpleString replyto) {
        this.dataAddress = replyto;
        first = true;
    }
    
    @Override
    public void processData(IndexResult indexResult) throws Exception {
        if (first) {
            createYamcsClient();
        }
        yamcsClient.sendData(dataAddress, ProtoDataType.ARCHIVE_INDEX, indexResult);
    }

    private void createYamcsClient() throws Exception {
        yamcsSession=YamcsSession.newBuilder().build();
        yamcsClient=yamcsSession.newClientBuilder().setRpc(false).setDataProducer(true).build();
        Protocol.killProducerOnConsumerClosed(yamcsClient.getDataProducer(), dataAddress);
        first = false;
    }
    
    @Override
    public void finished(boolean success) {
        if (success) {
            try {
                if (first) {
                    createYamcsClient();
                }
                yamcsClient.sendDataEnd(dataAddress);
            } catch (Exception e) {
                log.error("got exception while sending the response", e);
            }
        }
        try {
            if (yamcsClient != null) yamcsClient.close();
        } catch (ActiveMQException e) {
            log.warn("Got exception while closing client", e);
        }
        try {
            if( yamcsSession != null ) yamcsSession.close();
        } catch (ActiveMQException e) {
            log.warn("Got exception while closing client", e);
        }
    }
}
