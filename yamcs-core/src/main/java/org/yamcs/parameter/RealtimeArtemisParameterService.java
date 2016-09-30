package org.yamcs.parameter;

import static org.yamcs.api.artemis.Protocol.*;

import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import org.yamcs.*;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.hornetq.ArtemisManagement;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.HqClientMessageToken;

/**
 * Provides realtime parameter subscription via hornetq.  
 * 
 * Each connected client has one queue.
 * 
 * @author nm
 *
 */
public class RealtimeArtemisParameterService implements ParameterWithIdConsumer {
    YProcessor channel;
    YamcsClient yclient;
    Logger log;
    //maps subscription ids <-> addresses
    BiMap<Integer, SimpleString> subscriptions=Maps.synchronizedBiMap(HashBiMap.<Integer,SimpleString>create());
    ParameterWithIdRequestHelper prh;
    YamcsSession yamcsSession;

    public RealtimeArtemisParameterService(YProcessor channel) throws ActiveMQException, YamcsApiException {
        this.channel=channel;
        prh = new ParameterWithIdRequestHelper(channel.getParameterRequestManager(), this);

        log=LoggerFactory.getLogger(RealtimeArtemisParameterService.class.getName()+"["+channel.getInstance()+"]");
        yamcsSession = YamcsSession.newBuilder().build();
        SimpleString rpcAddress=Protocol.getParameterRealtimeAddress(channel.getInstance());
        yclient=yamcsSession .newClientBuilder().setRpcAddress(rpcAddress).setDataProducer(true).build();
        yclient.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage message) {
                try {
                    processRequest(message);
                } catch (Exception e) {
                    log.error("got error when processing request",e);
                }
            }
        });

    }

    private void processRequest(ClientMessage msg) throws YamcsApiException, ActiveMQException {
        SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);

        if(replyto==null) {
            log.warn("did not receive a replyto header. Ignoring the request");
            return;
        }

        String request=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
        log.debug("received a new request: "+request);
        SimpleString dataAddress=msg.getSimpleStringProperty(DATA_TO_HEADER_NAME);
        if(dataAddress==null) {
            yclient.sendErrorReply(replyto, "subscribe has to come with a data address (to send data to)");
            return;
        }

        if("subscribe".equalsIgnoreCase(request)) {
            subscribe(replyto, dataAddress, msg);
        } else if("subscribeAll".equalsIgnoreCase(request)) {
            subscribeAll(replyto, dataAddress, msg);
        } else if ("unsubscribe".equalsIgnoreCase(request)) {
            unsubscribe(replyto, dataAddress, msg);
        } else if ("unsubscribeAll".equalsIgnoreCase(request)) {
            unsubscribeAll(replyto, dataAddress, msg);
        } else  {
            yclient.sendErrorReply(replyto, "unknown request '"+request+"'");
        }

    }


    private void subscribe( SimpleString replyto, SimpleString dataAddress, ClientMessage msg)
            throws ActiveMQException {
        List<NamedObjectId> paraList=null;
        try {
            paraList= ((NamedObjectList)Protocol.decode(msg, NamedObjectList.newBuilder())).getListList();
        } catch (YamcsApiException e) {
            log.warn("Could not decode the parameter list");
            return;
        }
        HqClientMessageToken authToken = new HqClientMessageToken(msg, null);

        try {
            if(subscriptions.containsValue(dataAddress)) {
                int subscriptionId=subscriptions.inverse().get(dataAddress);
                prh.addItemsToRequest(subscriptionId, paraList, authToken);
            } else {
                ArtemisManagement.configureNonBlocking(dataAddress);
                int subscriptionId=prh.addRequest(paraList, authToken);
                subscriptions.put(subscriptionId, dataAddress);
            }
            yclient.sendReply(replyto, "OK",null);
        } catch (InvalidIdentification e) {
            NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            yclient.sendErrorReply(replyto, new YamcsException("InvalidIdentification", "Invalid Identification", nol));
        } catch (InvalidRequestIdentification e) {
            log.error("got invalid subscription id", e);
            yclient.sendErrorReply(replyto, "internal error: "+e.toString());
        } catch (NoPermissionException e) {
            log.error("No permission.", e);
            yclient.sendErrorReply(replyto, e);
        }
    }


    private void unsubscribe( SimpleString replyto, SimpleString dataAddress, ClientMessage msg) throws ActiveMQException {
        List<NamedObjectId> paraList=null;
        try {
            paraList= ((NamedObjectList)Protocol.decode(msg, NamedObjectList.newBuilder())).getListList();
        } catch (YamcsApiException e) {
            log.warn("Could not decode the parameter list");
            return;
        }
        AuthenticationToken authToken = new HqClientMessageToken(msg, null);
        if(subscriptions.containsValue(dataAddress)) {
            int subscriptionId=subscriptions.inverse().get(dataAddress);
            try {
                prh.removeItemsFromRequest(subscriptionId, paraList, authToken);
                yclient.sendReply(replyto, "OK",null);
            } catch (NoPermissionException e) {
                log.error("No permission.", e);
                yclient.sendErrorReply(replyto, e);
            }
        } else {
            yclient.sendErrorReply(replyto, "not subscribed to anything");
            return;
        }
        yclient.sendReply(replyto, "OK",null);

    }


    private void subscribeAll(SimpleString replyto, SimpleString dataAddress, ClientMessage msg) throws ActiveMQException {

        String namespace=null;
        try {
            namespace=((StringMessage)Protocol.decode(msg, StringMessage.newBuilder())).getMessage();
        } catch (YamcsApiException e) {
            log.warn("Could not decode the namespace");
            return;
        }
        if(subscriptions.containsValue(dataAddress)) {
            yclient.sendErrorReply(replyto, "already subscribed for this address");
            return;
        }
        AuthenticationToken authToken = new HqClientMessageToken(msg, null);
        int subscriptionId= 0;
        try {
            subscriptionId = prh.subscribeAll(namespace, authToken);
            subscriptions.put(subscriptionId, dataAddress);
            yclient.sendReply(replyto, "OK", null);
        } catch (NoPermissionException e) {
            log.error("No permission.", e);
            yclient.sendErrorReply(replyto, e);
        }
    }

    private void unsubscribeAll(SimpleString replyto, SimpleString dataAddress, ClientMessage msg) throws ActiveMQException {
        if(!subscriptions.containsValue(dataAddress)) {
            yclient.sendErrorReply(replyto, "not subscribed for this address");
            return;
        }
        ParameterRequestManagerImpl prm=channel.getParameterRequestManager();
        int subscriptionId=subscriptions.inverse().get(dataAddress);
        boolean r=prm.unsubscribeAll(subscriptionId);
        if(r) {
            yclient.sendReply(replyto, "OK", null);
            subscriptions.remove(subscriptionId);
        } else {
            yclient.sendErrorReply(replyto, "not a subscribeAll subscription for this address");
        }
    }

    @Override
    public void update(int subscriptionId, List<ParameterValueWithId> paramList) {
        SimpleString addr=subscriptions.get(subscriptionId);

        ParameterData.Builder pd=ParameterData.newBuilder();
        for(ParameterValueWithId pvwi:paramList) {
            ParameterValue pv=pvwi.getParameterValue();
            org.yamcs.protobuf.Pvalue.ParameterValue gpv=pv.toGpb(pvwi.getId());
            pd.addParameter(gpv);
        } 
        try {
            yclient.sendData(addr, ProtoDataType.PARAMETER, pd.build());
        } catch (ActiveMQException e) {
            subscriptions.remove(addr);
            log.warn("got error when sending parameter updates, removing any subscription of "+addr,e);
        }
    }

    public void quit() {
        try {
            yamcsSession.close();
        } catch (ActiveMQException e) {
            log.warn("Error when closing yamcsSession", e);
        }
    }
}
