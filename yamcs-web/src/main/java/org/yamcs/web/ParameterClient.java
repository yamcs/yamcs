package org.yamcs.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.codehaus.jackson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dyuproject.protostuff.JsonIOUtil;

import org.yamcs.Channel;
import org.yamcs.ChannelClient;
import org.yamcs.ChannelException;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.ParameterConsumer;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValue;
import org.yamcs.ParameterValueWithId;
import org.yamcs.protobuf.Comp.ComputationDef;
import org.yamcs.protobuf.Comp.ComputationDefList;
import org.yamcs.protobuf.SchemaComp;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.management.ManagementService;

/**
 * Provides realtime parameter subscription via web.  
 * 
 * TODO better deal with exceptions
 * 
 * @author nm
 *
 */
public class ParameterClient implements ParameterConsumer, ChannelClient {
    Channel channel;
    Logger log;
    //maps subscription ids <-> addresses
    WebSocketServerHandler wsHandler;
    int subscriptionId=-1;
    
    //subscription id used for computations
    int compSubscriptionId=-1;
    
    final String username="unknown";
    final String applicationName="uss-web";

    final CopyOnWriteArrayList<Computation> compList=new CopyOnWriteArrayList<Computation>();
    final int clientId;
    
    public ParameterClient(String yamcsInstance, WebSocketServerHandler webSocketServerHandler) {
        this.channel=Channel.getInstance(yamcsInstance, "realtime");
        log=LoggerFactory.getLogger(ParameterClient.class.getName()+"["+yamcsInstance+"]");
        this.wsHandler=webSocketServerHandler;
        clientId=ManagementService.getInstance().registerClient(yamcsInstance, channel.getName(), this);
    }
    
    public void processRequest(String request, int id, JsonParser jsp, WebSocketServerHandler wssh) {
        log.debug("received a new request: "+request);

        if("subscribe".equalsIgnoreCase(request)) {
            subscribe(id, jsp);
        } else if("subscribeAll".equalsIgnoreCase(request)) {
            subscribeAll(id, jsp);
        } else if ("unsubscribe".equalsIgnoreCase(request)) {
            unsubscribe(id, jsp);
        } else if ("unsubscribeAll".equalsIgnoreCase(request)) {
            unsubscribeAll(id, jsp);
        } else if("subscribeComputations".equalsIgnoreCase(request)) {
            subscribeComputations(id, jsp);
        } else  {
            wssh.sendException(id, "unknown request '"+request+"'");
        }
        
    }

   
    private void subscribe(int id, JsonParser jsp) {
        List<NamedObjectId> paraList=null;
        NamedObjectList.Builder nolb=NamedObjectList.newBuilder();
        try {
            JsonIOUtil.mergeFrom(jsp, nolb, SchemaYamcs.NamedObjectList.MERGE, false);
            paraList=nolb.getListList();
        } catch (IOException e) {
            wsHandler.sendException(id, "error decoding message: "+e.getMessage());
            log.warn("error decoding message: {}",e.toString());
            return;
        }
        
        //TODO check permissions and subscription limits
        try {
            ParameterRequestManager prm=channel.getParameterRequestManager();
            if(subscriptionId!=-1) {
                prm.addItemsToRequest(subscriptionId, paraList);
            } else {
                subscriptionId=prm.addRequest(paraList, this);
                System.out.println("---------------- here3 subscriptionID: "+subscriptionId);
            }
            wsHandler.sendReply(id, "OK", null);
        } catch (InvalidIdentification e) {
            NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            wsHandler.sendException(id, "InvalidIdentification", nol, org.yamcs.protobuf.SchemaYamcs.NamedObjectList.WRITE);
        } catch (InvalidRequestIdentification e) {
            log.error("got invalid subscription id", e);
            wsHandler.sendException(id, "internal error: "+e.toString());
        }
    }
    

    private void unsubscribe(int id, JsonParser jsp) {
        List<NamedObjectId> paraList=null;
       
        try {
            NamedObjectList.Builder nolb=NamedObjectList.newBuilder();
            JsonIOUtil.mergeFrom(jsp, nolb, SchemaYamcs.NamedObjectList.MERGE, false);
        } catch (IOException e) {
            log.warn("Could not decode the parameter list");
            return;
        }
        //TODO check permissions and subscription limits
        try {
            ParameterRequestManager prm=channel.getParameterRequestManager();
            if(subscriptionId!=-1) {
                prm.removeItemsFromRequest(subscriptionId, paraList);
                wsHandler.sendReply(id, "OK",null);
            } else {
                wsHandler.sendException(id, "not subscribed to anything");
                return;
            }
            wsHandler.sendReply(id, "OK", null);
        } catch (InvalidIdentification e) {
            wsHandler.sendException(id, e.toString());
        }
    }
    
    
    private void subscribeAll(int reqId, JsonParser jsp) {
        //TODO check permissions and subscription limits
        
        String namespace=null;
        try {
            StringMessage.Builder nolb=StringMessage.newBuilder();
            JsonIOUtil.mergeFrom(jsp, nolb, SchemaYamcs.StringMessage.MERGE, false);
        } catch (IOException e) {
            log.warn("Could not decode the namespace");
            return;
        }
        if(subscriptionId!=-1) {
            wsHandler.sendException(reqId, "already subscribed for this client");
            return;
        }
        ParameterRequestManager prm=channel.getParameterRequestManager();
        subscriptionId=prm.subscribeAll(namespace, this);
        wsHandler.sendReply(reqId, "OK", null);
    }
    
    

    private void subscribeComputations(int reqId, JsonParser jsp) {
        //TODO check permissions and subscription limits
        List<ComputationDef> cdefList;
        
        try {
            ComputationDefList.Builder cdlb=ComputationDefList.newBuilder();
            JsonIOUtil.mergeFrom(jsp, cdlb, SchemaComp.ComputationDefList.MERGE, false);
            cdefList=cdlb.getCompDefList();
        } catch (IOException e) {
            wsHandler.sendException(reqId, "error decoding message: "+e.getMessage());
            log.warn("error decoding message: {}",e.toString());
            return;
        }
        List<NamedObjectId> argList=new ArrayList<NamedObjectId>();
        for(ComputationDef c: cdefList) {
            argList.addAll(c.getArgumentList());
        }
        
        try {
            ParameterRequestManager prm=channel.getParameterRequestManager();
            if(compSubscriptionId!=-1) {
                prm.addItemsToRequest(compSubscriptionId, argList);
            } else {
                compSubscriptionId=prm.addRequest(argList, this);
            }
            
        } catch (InvalidIdentification e) {
            NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            wsHandler.sendException(reqId, "InvalidIdentification", nol, org.yamcs.protobuf.SchemaYamcs.NamedObjectList.WRITE);
            return;
        }
        
        try {
            for(ComputationDef cdef:cdefList) {
                Computation c = ComputationFactory.getComputation(cdef);
                compList.add(c);
            }
        } catch (Exception e) {
            log.warn("Cannot create computation: ",e);
            wsHandler.sendException(reqId, "error creating computation: "+e.toString());
            return;
        }
        wsHandler.sendReply(reqId, "OK", null);
    }
    
    private void unsubscribeAll(int reqId, JsonParser jsp) {
        if(subscriptionId==-1) {
            wsHandler.sendException(reqId, "not subscribed");
            return;
        }
        ParameterRequestManager prm=channel.getParameterRequestManager();
        boolean r=prm.unsubscribeAll(subscriptionId);
        if(r) {
            wsHandler.sendReply(reqId, "OK", null);
            subscriptionId=-1;
        } else {
            wsHandler.sendException(reqId, "not a subscribeAll subscription for this client");
        }
    }
    
    @Override
    public void updateItems(int subscrId, ArrayList<ParameterValueWithId> paramList) {
        if(wsHandler==null) return;
        
        if(subscrId==compSubscriptionId) {
            updateComputations(paramList);
            return;
        }
        ParameterData.Builder pd=ParameterData.newBuilder();
        for(ParameterValueWithId pvwi:paramList) {
            ParameterValue pv=pvwi.getParameterValue();
            pd.addParameter(pv.toGpb(pvwi.getId()));
        }
        try {
            wsHandler.sendData(ProtoDataType.PARAMETER, pd.build());
        } catch (Exception e) {
            log.warn("got error when sending parameter updates, quitting", e);
            quit();
        }
    }

    public void updateComputations(ArrayList<ParameterValueWithId> paramList) {
        Map<NamedObjectId, ParameterValue> parameters=new HashMap<NamedObjectId, ParameterValue>();
        for(ParameterValueWithId pvwi:paramList) {
            parameters.put(pvwi.getId(), pvwi.getParameterValue());
        }
        ParameterData.Builder pd=ParameterData.newBuilder();
        for(Computation c:compList) {
            org.yamcs.protobuf.Pvalue.ParameterValue pv=c.evaluate(parameters);
            if(pv!=null) pd.addParameter(pv);
        }
        if(pd.getParameterCount()==0) return;
        
        try {
            wsHandler.sendData(ProtoDataType.PARAMETER, pd.build());
        } catch (Exception e) {
            log.warn("got error when sending parameter updates, quitting", e);
            quit();
        }
    }
    
    /**
     * called when the socket is closed
     * unsubscribe all parameters and the client from the managmenet interface
     * 
     */
    public void quit() {
        ParameterRequestManager prm=channel.getParameterRequestManager();
        ManagementService.getInstance().unregisterClient(clientId);
        if(subscriptionId!=-1) prm.removeRequest(subscriptionId);
        if(compSubscriptionId!=-1) prm.removeRequest(compSubscriptionId);
    }
    

    @Override
    public void switchChannel(Channel c) throws ChannelException {
        log.info("switching channel to {}", c);
        
        ParameterRequestManager prm=channel.getParameterRequestManager();
        List<NamedObjectId> paraList=prm.removeRequest(subscriptionId);
        List<NamedObjectId> compParaList=prm.removeRequest(compSubscriptionId);
        channel=c;
        prm=channel.getParameterRequestManager();
        try {
            prm.addRequest(subscriptionId, paraList, this);
        } catch (InvalidIdentification e) {
            log.warn("got InvalidIdentification when resubscribing");
            e.printStackTrace();
        }
        try {
            prm.addRequest(compSubscriptionId, compParaList, this);
        } catch (InvalidIdentification e) {
            log.warn("got InvalidIdentification when resubscribing");
            e.printStackTrace();
        }
    }

    @Override
    public void channelQuit() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }
}
