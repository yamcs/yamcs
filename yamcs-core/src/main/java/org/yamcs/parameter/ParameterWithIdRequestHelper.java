package org.yamcs.parameter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.Parameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;


/**
 * This sits in front of the ParameterRequestManager and implements subscriptions based on NamedObjectId 
 * taking care to send to the consumers the parameters with the requested id.
 * 
 * A client can request in fact the same parameter with two different names and they will get it twice each time
 * 
 * TODO: check privileges and subscription limits
 * 
 * @author nm
 *
 */
public class ParameterWithIdRequestHelper implements ParameterConsumer {
    ParameterRequestManagerImpl prm;
    final ParameterWithIdConsumer listener;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    Map<Integer, ListMultimap<Parameter, NamedObjectId>> subscriptions = new ConcurrentHashMap<Integer, ListMultimap<Parameter, NamedObjectId>>();



    public ParameterWithIdRequestHelper(ParameterRequestManagerImpl prm, ParameterWithIdConsumer listener) {
        this.prm = prm;
        this.listener = listener;
    }

    public int addRequest(List<NamedObjectId> idList, AuthenticationToken authToken) throws InvalidIdentification, NoPermissionException {
        ListMultimap<Parameter, NamedObjectId> lm = ArrayListMultimap.create();
        List<Parameter> plist = checkNames(idList);
        for(int i =0; i<idList.size() ; i++) {
            Parameter p = plist.get(i);
            checkParameterPrivilege(authToken, p.getQualifiedName());
            NamedObjectId id = idList.get(i);
            lm.put(p, id);
        }
        int subscriptionId = prm.addRequest(plist, this);
        subscriptions.put(subscriptionId, lm);

        return subscriptionId;
    }

    public void addItemsToRequest(int subscriptionId,  List<NamedObjectId> idList, AuthenticationToken authToken)
            throws InvalidIdentification, NoPermissionException {
        ListMultimap<Parameter, NamedObjectId> subscr = subscriptions.get(subscriptionId);
        if(subscr==null) {
            log.warn("add item requested for an invalid subscription id "+subscriptionId);
            return;
        }

        List<Parameter> plist = checkNames(idList);
        synchronized(subscr) {
            for(int i =0; i<idList.size() ; i++) {
                Parameter p = plist.get(i);
                checkParameterPrivilege(authToken, p.getQualifiedName());
                NamedObjectId id = idList.get(i);
                if(subscr.containsEntry(p, id)) {
                    log.info("Ignoring duplicate subscription for '{}', id: {}", p.getName(), StringConverter.idToString(id));
                    continue;
                }
                subscr.put(p, id);
            }
        }
        prm.addItemsToRequest(subscriptionId, plist);
    }

    private List<Parameter> checkNames(List<NamedObjectId> plist) throws InvalidIdentification {
        List<NamedObjectId> invalid = new ArrayList<NamedObjectId>();
        List<Parameter> result = new ArrayList<Parameter>();
        for(NamedObjectId id:plist) {
            try {
                Parameter p = prm.getParameter(id);
                result.add(p);
            } catch (InvalidIdentification e) {
                invalid.add(id);
            }
        }
        if(!invalid.isEmpty()) throw new InvalidIdentification(invalid);

        return result;
    }


    public void removeRequest(int subscriptionId) {
        if(!subscriptions.containsKey(subscriptionId)) {
            log.warn("remove requested for an invalid subscription id "+subscriptionId);
            return;
        }
        prm.removeRequest(subscriptionId);
    }


    public void removeItemsFromRequest(int subscriptionId,   List<NamedObjectId> parameterIds, AuthenticationToken authToken) throws NoPermissionException {
        ListMultimap<Parameter, NamedObjectId> subscr = subscriptions.get(subscriptionId);
        if(subscr==null) {
            log.warn("remove requested for an invalid subscription id "+subscriptionId);
            return;
        }

        List<Parameter> paramsToRemove = new ArrayList<Parameter>();
        synchronized(subscr) {
            Iterator<Entry<Parameter, NamedObjectId>> it = subscr.entries().iterator();
            while(it.hasNext()) {
                Entry<Parameter, NamedObjectId> e = it.next();
                checkParameterPrivilege(authToken, e.getKey().getQualifiedName());
                if(parameterIds.contains(e.getValue())) {
                    paramsToRemove.add(e.getKey());
                }
            }
        }
        if(!paramsToRemove.isEmpty()) {
            prm.removeItemsFromRequest(subscriptionId, paramsToRemove);
        }
    }

    public ParameterRequestManagerImpl getPrm() {
        return prm;
    }


    public int subscribeAll(String namespace, AuthenticationToken authToken) throws NoPermissionException {
        checkParameterPrivilege(authToken, ".*");
        return prm.subscribeAll(this);
    }


    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        ListMultimap<Parameter, NamedObjectId> subscription = subscriptions.get(subscriptionId);
        if(subscription==null) { //probably the subscription has just been removed
            log.debug("Received an updateItems for an unknown subscription "+subscriptionId);
            return;
        }
        List<ParameterValueWithId> plist = new ArrayList<ParameterValueWithId>(items.size());
        synchronized(subscription) {
            for(ParameterValue pv: items) {
                List<NamedObjectId> idList = subscription.get(pv.getParameter());
                if(idList==null || idList.size() == 0) {
                    log.warn("Received values for a parameter not subscribed: "+pv.getParameter());
                    continue;
                }

                for(NamedObjectId id:idList) {
                    ParameterValueWithId pvwi = new ParameterValueWithId(pv, id);
                    plist.add(pvwi);
                }	    
            }
        }
        listener.update(subscriptionId, plist);
    }

    public List<ParameterValueWithId> getValuesFromCache(List<NamedObjectId> idList, AuthenticationToken authToken) throws InvalidIdentification, NoPermissionException {
        List<Parameter> params = checkNames(idList);

        ListMultimap<Parameter, NamedObjectId> lm = ArrayListMultimap.create();
        for(int i =0; i<idList.size() ; i++) {
            Parameter p = params.get(i);
            checkParameterPrivilege(authToken, p.getQualifiedName());
            NamedObjectId id = idList.get(i);
            lm.put(p, id);
        }

        List<ParameterValue> values = prm.getValuesFromCache(params);
        List<ParameterValueWithId> plist = new ArrayList<ParameterValueWithId>(values.size());


        for(ParameterValue pv: values) {
            List<NamedObjectId> l = lm.get(pv.getParameter());
            if(l==null) {
                log.warn("Received values for a parameter not requested: "+pv.getParameter());
                continue;
            }

            for(NamedObjectId id:l) {
                ParameterValueWithId pvwi = new ParameterValueWithId(pv, id);
                plist.add(pvwi);
            }	    
        }
        return plist;
    }

    public void switchPrm(ParameterRequestManagerImpl newPrm, AuthenticationToken authToken)
            throws InvalidIdentification, NoPermissionException {
        for(int subscriptionId: subscriptions.keySet()) {
            List<Parameter> plist = prm.removeRequest(subscriptionId);
            // checking permission
            for(Parameter p : plist)
                checkParameterPrivilege(authToken, p.getQualifiedName());
            newPrm.addRequest(subscriptionId, plist, this);
        }
        prm=newPrm;
    }

    public boolean hasParameterCache() {
        return prm.hasParameterCache();
    }

    /**
     * Check if the user has a privilege for the specified parameter name
     * @param authToken
     * @param parameterName
     * @throws NoPermissionException
     */
    private void checkParameterPrivilege(AuthenticationToken authToken, String parameterName) throws NoPermissionException  {
        if(!Privilege.getInstance().hasPrivilege(authToken, Privilege.Type.TM_PARAMETER, parameterName)) {
            throw  new NoPermissionException("User " + authToken + " has no permission for parameter "  + parameterName);
        }
    }

}

