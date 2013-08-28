package org.yamcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtceproc.XtceTmProcessor;

/**
 * @author mache
 * Keeps track of which parameters are part of which subscriptions.
 * 
 * There are two types of subscriptions:
 *  - subscribe all
 *  - subscribe to a set
 * Both types have an unique id associated but different methods work with them
 * 
 */
public class ParameterRequestManager implements ParameterListener {
	Logger log;
	//Maps the parameters to the request(subscription id) in which they have been asked
	private Map<Parameter,ArrayList<ParaSubscrRequestStruct>> param2RequestMap= new HashMap<Parameter,ArrayList<ParaSubscrRequestStruct>>();
	//Maps the request (subscription id) to an object that is consuming the results who has requested it
	private Map<Integer,ParameterConsumer> request2ParameterConsumerMap = new HashMap<Integer,ParameterConsumer>();
	
	//contains the subscribeAll ids -> namespace mapping
	private Map<Integer,String> subscribeAll =new HashMap<Integer,String>();
	
	
	private XtceTmProcessor tmProcessor=null;
	private DerivedValuesManager derivedValuesManager=null;
	private AlgorithmManager algorithmManager=null;
	private SystemVariablesManager systemVariablesManager=null;
	private ParameterProvider ppProvider=null;
	private List<ParameterProvider> parameterProviders=new ArrayList<ParameterProvider>();
	
	private static AtomicInteger lastSubscriptionId= new AtomicInteger();
	public final Channel channel;
	TmPacketProvider tmPacketProvider;
    
    /**
     * Creates a new ParameterRequestManager, configured to listen to a newly
     * created XtceTmProcessor.
     */
    public ParameterRequestManager(Channel chan) throws ConfigurationException {
        this(chan, new XtceTmProcessor(chan));
    }
    
	/**
	 * Creates a new ParameterRequestManager, configured to listen to the
	 * specified XtceTmProcessor.
	 */
    public ParameterRequestManager(Channel chan, XtceTmProcessor tmProcessor) throws ConfigurationException {
        this.channel=chan;
        this.tmProcessor=tmProcessor;
	    systemVariablesManager=new SystemVariablesManager(this, chan);
	    log=LoggerFactory.getLogger(this.getClass().getName()+"["+chan.getName()+"]");
		tmProcessor.setParameterListener(this);
		algorithmManager=new AlgorithmManager(this, chan);
		//derived values should be the last one because it can be based on tm and system variables
		derivedValuesManager=new DerivedValuesManager(this, chan);
	}
	
    // TODO replace by configuration in yaml file
    public void addParameterProvider(ParameterProvider parameterProvider) {
        parameterProviders.add(parameterProvider);
    }
	
	/** Added by AMI on request of NM during a remote email session. */
	public int generateDummyRequestId() {
		return lastSubscriptionId.incrementAndGet();
	}

	/**
	 * subscribes to all parameters
	 */
	public synchronized int subscribeAll(String namespace, ParameterConsumer consumer) {
        int id=lastSubscriptionId.incrementAndGet();
        log.debug("new subscribeAll with subscriptionId "+id);
        if(subscribeAll.isEmpty()) {
            tmProcessor.startProvidingAll();
            derivedValuesManager.startProvidingAll();
            algorithmManager.startProvidingAll();
            systemVariablesManager.startProvidingAll();
            for(ParameterProvider provider:parameterProviders) {
                provider.startProvidingAll();
            }
        }
        subscribeAll.put(id, namespace);
        request2ParameterConsumerMap.put(id, consumer);
        return id;
    }
	
	/**
	 * removes the subscription to all parameters
	 * @param subscriptionId
	 * @return
	 */
	public synchronized boolean unsubscribeAll(int subscriptionId) {
	    return subscribeAll.remove(subscriptionId) != null;
    }

	public synchronized int addRequest(List<NamedObjectId> paraList, ParameterConsumer tpc) throws InvalidIdentification {
		List<ParameterProvider> providers=getProviders(paraList);
		int id=lastSubscriptionId.incrementAndGet();
		log.debug("new request with subscriptionId "+id+" for itemList="+paraList);
		for(int i=0;i<paraList.size();i++) {
			log.trace("adding to subscriptionID:{} item:{} ",id, paraList.get(i));
			addItemToRequest(id,paraList.get(i),providers.get(i));
			//log.info("afterwards the subscription looks like: "+toString());
		}
		request2ParameterConsumerMap.put(id, tpc);
		return id;
	}
	
	/**
	 * Create request with a given id. This is called when switching channels, the id is comming from the other channel.
	 * @param subscriptionID
	 * @param itemList
	 * @param tpc
	 * @return
	 * @throws InvalidIdentification
	 */
	public synchronized void addRequest(int id, List<NamedObjectId> paraList, ParameterConsumer tpc) throws InvalidIdentification {
		List<ParameterProvider> providers=getProviders(paraList);
		for(int i=0;i<paraList.size();i++) {
			log.trace("adding to subscibtionID:{} item:{}",id, paraList.get(i));
			addItemToRequest(id,paraList.get(i),providers.get(i));
			//log.info("afterwards the subscription looks like: "+toString());
		}
		request2ParameterConsumerMap.put(id, tpc);

	}
	
	/**
	 * Add items to an request id. 
	 * @param subscriptionID
	 * @param itemList
	 * @throws InvalidIdentification
	 */
	public synchronized void addItemsToRequest(int subscriptionId, List<NamedObjectId> paraList) throws InvalidIdentification, InvalidRequestIdentification {
		if(!request2ParameterConsumerMap.containsKey(subscriptionId)) {
			log.error(" addItemsToRequest called with an invalid subscriptionId="+subscriptionId+"\n current subscr:\n"+request2ParameterConsumerMap);
			throw new InvalidRequestIdentification("no such subscriptionID",subscriptionId);
		}
		List<ParameterProvider> providers=getProviders(paraList);
		for(int i=0;i<paraList.size();i++) {
			addItemToRequest(subscriptionId, paraList.get(i), providers.get(i));
		}
	}
	
	/**
	 * Adds a new item to an existing request. There is no check if the item is already there, 
	 *  so there can be duplicates (observed in the CGS CIS).
	 *  This call also works with a new id
	 * @param id
	 * @param para
	 * @param provider 
	 * @throws InvalidIdentification
	 */
	private void addItemToRequest(int id, NamedObjectId para, ParameterProvider provider) {
		Parameter paramDef;
		try {
			paramDef = provider.getParameter(para);
		} catch (InvalidIdentification e) {
			log.error("Unexpected InvalidIdentification exception received for an item which was supposed to be provided by this provider: subscriptionId="+id+" item="+para);
			return;
		}
		if(!param2RequestMap.containsKey(paramDef)) {
			//this parameter is not requested by any other request
			ArrayList<ParaSubscrRequestStruct>al_req=new ArrayList<ParaSubscrRequestStruct>();
			param2RequestMap.put(paramDef,al_req);
			provider.startProviding(paramDef);
		}
		param2RequestMap.get(paramDef).add(new ParaSubscrRequestStruct(id,para));
	}
	
	/**
	 * Removes an item from a request. All the instances of the item are removed.
	 * Any items specified that are not in the subscription will be ignored.
	 * @param subscriptionID
	 * @param paraList
	 * @throws InvalidIdentification
	 */
	public synchronized void removeItemsFromRequest(int subscriptionID, List<NamedObjectId> paraList) throws InvalidIdentification {
		List<ParameterProvider> providers=getProviders(paraList);
		for(int i=0;i<paraList.size();i++) {
			removeItemFromRequest(subscriptionID,paraList.get(i),providers.get(i));
		}
	}

	private void removeItemFromRequest(int subscriptionId, NamedObjectId para, ParameterProvider provider) {
		Parameter paramDef;
		try {
			paramDef=provider.getParameter(para);
		} catch (InvalidIdentification e) {
			log.error("Unexpected InvalidIdentification exception received for an item which was supposed to be provided by some provider because getProviders didn't complain: subscriptionId="+subscriptionId+" itemId="+para);
			return;
		}
		
		if(param2RequestMap.containsKey(paramDef)) { //is there really any request associated to this parameter?
			ArrayList<ParaSubscrRequestStruct> al_req=param2RequestMap.get(paramDef);
			boolean found=false;
			Iterator<ParaSubscrRequestStruct> it1=al_req.iterator();
			//remove the subscription from the list of this parameter
			while(it1.hasNext()){
				ParaSubscrRequestStruct s=it1.next();
				if(s.subscriptionId==subscriptionId) {
					it1.remove();
					found=true; 
				}
			}
			if(found) { 
				if(al_req.isEmpty()) { //nobody wants this parameter anymore
					provider.stopProviding(paramDef);
					param2RequestMap.remove(paramDef);
				}
			} else {
				log.warn("parameter removal requested for "+para+" but not part of subscription "+subscriptionId);		
			}
		} else {
			log.warn("parameter removal requested for "+para+" but not subscribed");
		}
	}

    /**
     * Removes all the items from this subscription and returns them into an
     * ArrayList. The result is usually used in the TelemetryImpl to move this
     * subscription to a different ParameterRequestManager
     */
	public synchronized ArrayList<NamedObjectId> removeRequest(int subscriptionId) {
		log.debug("removing request for subscriptionId "+subscriptionId);
		//It's a bit annoying that we have to loop through all the parameters to find the ones that
		// are relevant for this request. We could keep track of an aditional map.
		ArrayList<NamedObjectId> result=new ArrayList<NamedObjectId>();
		//loop through all the parameter definitions 
		//  find all the subscriptions with the requested subscriptionId and add their corresponding 
		//  itemId to the list.
		for(Iterator<Parameter> it = param2RequestMap.keySet().iterator();it.hasNext();) {
			Parameter paramDef=it.next();
			ArrayList<ParaSubscrRequestStruct> al_req=param2RequestMap.get(paramDef);
			NamedObjectId para=null;
			Iterator<ParaSubscrRequestStruct> it1=al_req.iterator();
			//remove the subscription from the list of this parameter (if present)
			while(it1.hasNext()){
				ParaSubscrRequestStruct s=it1.next();
				if(s.subscriptionId==subscriptionId) {
					result.add(s.para);
					it1.remove();
					para=s.para; 
				}
			}
			if(para!=null) { 
				if(al_req.isEmpty()) { //nobody wants this parameter anymore
					it.remove();
					getProvider(para).stopProviding(paramDef);
				}
			}
		}
		request2ParameterConsumerMap.remove(subscriptionId);
		return result;
	}
	
	private List<ParameterProvider> getProviders(List<NamedObjectId> itemList) throws InvalidIdentification {
		List<ParameterProvider> providers=new ArrayList<ParameterProvider>(itemList.size());
		ArrayList<NamedObjectId> invalid=new ArrayList<NamedObjectId>();
		for(int i=0;i<itemList.size();i++) {
			providers.add(i, getProvider(itemList.get(i)));
			if (providers.get(i)==null){
				invalid.add(itemList.get(i));
			}
		}
		if(invalid.size()!=0) {
			log.info("throwing InvalidIdentification for the following items:"+invalid);
			throw new InvalidIdentification(invalid);
		}
		return providers;
	}

	private ParameterProvider getProvider(NamedObjectId itemId) {
		if(tmProcessor.canProvide(itemId)) return tmProcessor;
		if ((systemVariablesManager!=null)&&(systemVariablesManager.canProvide(itemId))) return systemVariablesManager;
		if (algorithmManager.canProvide(itemId)) return algorithmManager;
		if (derivedValuesManager.canProvide(itemId)) return derivedValuesManager;
		if ((ppProvider!=null)&&(ppProvider.canProvide(itemId))) return ppProvider;
		for(ParameterProvider provider:parameterProviders) {
		    if(provider.canProvide(itemId)) {
		        return provider;
		    }
		}
		return null;
	}

	/**
	 * @param paraId
	 * @return the corresponding parameter definition for a IntemIdentification
	 * @throws InvalidIdentification in case no provider knows of this parameter. The InvalidIdentification is empty so it can't be passed via CORBA
	 */
	public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
		if(tmProcessor.canProvide(paraId)) return tmProcessor.getParameter(paraId);
		if ((systemVariablesManager!=null)&&(systemVariablesManager.canProvide(paraId))) return systemVariablesManager.getParameter(paraId);
		if (algorithmManager.canProvide(paraId)) return algorithmManager.getParameter(paraId);
		if (derivedValuesManager.canProvide(paraId)) return derivedValuesManager.getParameter(paraId);
		if ((ppProvider!=null)&&(ppProvider.canProvide(paraId))) return ppProvider.getParameter(paraId);
		for(ParameterProvider provider:parameterProviders) {
		    if(provider.canProvide(paraId)) {
		        return provider.getParameter(paraId);
		    }
		}
		throw new InvalidIdentification(paraId);
	}

	/* (non-Javadoc)
	 * @see org.yamcs.ParameterRequestManagerInterface#updateItems(java.util.ArrayList, java.util.ArrayList)
	 */
	@Override
    public synchronized void update(Collection<ParameterValue> params) {
		log.trace("ParamRequestManager.updateItems with {} parameters", params.size());
		//maps subscription id to a list of (value,id) to be delivered for that subscription
		HashMap<Integer,ArrayList<ParameterValueWithId>> delivery= new HashMap<Integer,ArrayList<ParameterValueWithId>>();
		
		//so first we add to the delivery the parameters just received
		updateDelivery(delivery,params);
		
		int derivedValueSubscriptionId=derivedValuesManager.getSubscriptionId();
		//then if the delivery updates some of the parameters required by the derived values
		//  compute the derived values
		//System.out.println("---------------delivery:"+delivery+" derivedValueSubscriptionId:"+derivedValueSubscriptionId);
		if(delivery.containsKey(derivedValueSubscriptionId)) {
			updateDelivery(delivery,derivedValuesManager.updateDerivedValues(delivery.get(derivedValueSubscriptionId))); 
		}
		
		int algoSubscriptionId=algorithmManager.getSubscriptionId();
        //same for the algorithms
        if(delivery.containsKey(algoSubscriptionId)) {
            updateDelivery(delivery,algorithmManager.updateAlgorithms(delivery.get(algoSubscriptionId))); 
        }

		//and finally deliver the delivery :)
		for(Map.Entry<Integer, ArrayList<ParameterValueWithId>> entry: delivery.entrySet()){
			Integer subscriptionId=entry.getKey();
			ArrayList<ParameterValueWithId> al=entry.getValue();
			ParameterConsumer consumer=request2ParameterConsumerMap.get(subscriptionId);
			if(consumer==null) {
				log.error("subscriptionId "+subscriptionId+" appears in the delivery list, but there is no consumer for it");
			} else {
				consumer.updateItems(subscriptionId,al);
			}
		}
	}
	
	/** 
	 * adds the passed parameters to the delivery
	 * @param delivery
	 * @param params
	 */
	private void updateDelivery(HashMap<Integer, ArrayList<ParameterValueWithId>> delivery, Collection<ParameterValue> params) {
		for(Iterator<ParameterValue> it=params.iterator();it.hasNext();) {
			ParameterValue pv=it.next();
			Parameter pDef=pv.def;
			//now walk through the requests and add this item to their delivery list
			if(!param2RequestMap.containsKey(pDef)) continue; //it could be that the parameter has been removed but the provider has not yet removed it from its list 
			for(Iterator<ParaSubscrRequestStruct> it1=param2RequestMap.get(pDef).iterator(); it1.hasNext();){
                ParaSubscrRequestStruct s=it1.next();
                if(!delivery.containsKey(s.subscriptionId)){
                        delivery.put(s.subscriptionId, new ArrayList<ParameterValueWithId>());
                }
                ParameterValueWithId pvwi=new ParameterValueWithId();
                pvwi.setId(s.para);
                pvwi.setParameterValue(pv);
                delivery.get(s.subscriptionId).add(pvwi);
			}
		}
		
		//update the subscribeAll subscriptions
		for(Map.Entry<Integer, String> entry:subscribeAll.entrySet()) {
		    int id=entry.getKey();
		    String namespace=entry.getValue();
		    
		    if(!delivery.containsKey(id)){
		        delivery.put(id, new ArrayList<ParameterValueWithId>());
		    }
		    ArrayList<ParameterValueWithId> al=delivery.get(id);
		    for(ParameterValue pv: params) {
		        ParameterValueWithId pvwi=new ParameterValueWithId();
		        NamedObjectId.Builder noid=NamedObjectId.newBuilder();
		        String name=pv.def.getAlias(namespace);
		        if(name==null) {
		            noid.setName(pv.def.getName());
		        } else {
		            noid.setNamespace(namespace).setName(pv.def.getAlias(namespace));
		        }
		        pvwi.setId(noid.build());
		        pvwi.setParameterValue(pv);
		        al.add(pvwi);
		    }
		}
	}

	public XtceTmProcessor getTmProcessor() {
		return tmProcessor;
	}
	
	/**
	 * Sets the telemetry packet provider by simply calling the corresponding method in the associated
	 *  TmProcessor
	 * @param p
	 */
	public void setPacketProvider(TmPacketProvider p) {
	    this.tmPacketProvider=p;
	    tmPacketProvider.setTmProcessor(tmProcessor);
	}

	/**
	 * @param ppProvider the ppProvider to set
	 */
	public void setProcessedParameterProvider(ParameterProvider paramProvider) {
		if(paramProvider!=null) paramProvider.setParameterListener(this);
		this.ppProvider = paramProvider;
	}
	
	/**
	 * Starts processing by creating a new thread for the associated TmProcessor and SystemVariablesManager
	 *
	 */
	public void start() {
		tmProcessor.start();
		if(ppProvider!=null) ppProvider.start();
		if(systemVariablesManager!=null) systemVariablesManager.start();
	}

	public void quit() {
        if(systemVariablesManager!=null) systemVariablesManager.stop();
	    tmPacketProvider.stop();
		if(ppProvider!=null) ppProvider.stop();
	}
	
	@Override
    public String toString() {
		StringBuffer sb=new StringBuffer();
		sb.append("Current Subscription list:\n");
		for(Parameter param:param2RequestMap.keySet()) {
			sb.append(param); sb.append("requested by [");
			ArrayList<ParaSubscrRequestStruct> al_req=param2RequestMap.get(param);
			for(Iterator<ParaSubscrRequestStruct> it1=al_req.iterator();it1.hasNext();) {
				ParaSubscrRequestStruct iirs=it1.next();
				sb.append("("+iirs.subscriptionId+",");
				sb.append(iirs.para.toString()); sb.append(") ");
			}
			sb.append("]\n");
		}
		sb.append("TmProcessor subscription:"+tmProcessor);
		return sb.toString();
	}

	public DerivedValuesManager getDerivedValuesManager() {
		return derivedValuesManager;
	}
}
/**
 * Keeps pairs (ParameterId,subscriptionId). We need these pairs because each consumer wants 
 *  to receive back parameters together with the original ParameterId he has used for subscription 
 *  (for example one can subscribe to a parameter based on opsname and another can subscribe to the same parameter 
 *    based on pathname).
 * @author mache
 *
 */
class ParaSubscrRequestStruct {
	NamedObjectId para;
	int subscriptionId;
	public ParaSubscrRequestStruct(int id, NamedObjectId item) {
		this.para=item;
		subscriptionId=id;
	}
	
	@Override
    public String toString() {
		return "(subscriptionId="+subscriptionId+", item="+para.toString();
	}
}
