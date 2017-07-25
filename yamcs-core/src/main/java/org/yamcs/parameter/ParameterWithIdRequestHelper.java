package org.yamcs.parameter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * This sits in front of the ParameterRequestManager and implements subscriptions based on NamedObjectId 
 * taking care to send to the consumers the parameters with the requested id.
 * 
 * A client can request in fact the same parameter with two different names and they will get it twice each time.
 * 
 * In addition it can also provide updates on parameter expirations. 
 * 
 * TODO: impose some subscription limits
 * 
 * @author nm
 *
 */
public class ParameterWithIdRequestHelper implements ParameterConsumer {
    ParameterRequestManagerImpl prm;
    final ParameterWithIdConsumer listener;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    Map<Integer, Subscription> subscriptions = new ConcurrentHashMap<>();
    
    //how often to check expiration
    private static long CHECK_EXPIRATION_INTERVAL = 1000;
    final static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1); 
    
    public ParameterWithIdRequestHelper(ParameterRequestManagerImpl prm, ParameterWithIdConsumer listener) {
        this.prm = prm;
        this.listener = listener;
        schedulePeriodicExpirationChecking(this);
    }

    private static void schedulePeriodicExpirationChecking(ParameterWithIdRequestHelper x) {
        //trick to allow GC to collect this object and remove it from the timer 
        final WeakReference<ParameterWithIdRequestHelper> ref = new WeakReference<>(x);
        final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>(); 
        ScheduledFuture<?> future =  timer.scheduleAtFixedRate(() -> {
            ParameterWithIdRequestHelper pwirh =  ref.get();
            if(pwirh==null) {
                ScheduledFuture<?> f =  futureRef.get();
                f.cancel(false);
            } else {
                pwirh.checkPeriodicExpiration();
            }
        }, CHECK_EXPIRATION_INTERVAL, CHECK_EXPIRATION_INTERVAL, TimeUnit.MILLISECONDS);
        futureRef.set(future);
    }
    
    public int addRequest(List<NamedObjectId> idList, AuthenticationToken authToken) throws InvalidIdentification, NoPermissionException {
        return addRequest(idList, false, authToken);
    }

    public int addRequest(List<NamedObjectId> idList, boolean checkExpiration, AuthenticationToken authToken) throws InvalidIdentification, NoPermissionException {
        ListMultimap<Parameter, NamedObjectId> lm = ArrayListMultimap.create();
        List<Parameter> plist = checkNames(idList);
        for(int i =0; i<idList.size() ; i++) {
            Parameter p = plist.get(i);
            checkParameterPrivilege(authToken, p.getQualifiedName());
            NamedObjectId id = idList.get(i);
            lm.put(p, id);
        }
        int subscriptionId = prm.addRequest(plist, this);
        Subscription subscr = new Subscription(lm, checkExpiration);
        subscr.checkExpiration = checkExpiration;
        subscriptions.put(subscriptionId, subscr);

        return subscriptionId;
    }

    public void addItemsToRequest(int subscriptionId,  List<NamedObjectId> idList, AuthenticationToken authToken) throws InvalidIdentification, NoPermissionException {
        Subscription subscr = subscriptions.get(subscriptionId);
        if(subscr==null) {
            log.warn("add item requested for an invalid subscription id {}", subscriptionId);
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
        List<NamedObjectId> invalid = new ArrayList<>();
        List<Parameter> result = new ArrayList<>();
        for(NamedObjectId id:plist) {
            try {
                Parameter p = prm.getParameter(id);
                result.add(p);
            } catch (InvalidIdentification e) {
                invalid.add(id);
            }
        }
        if(!invalid.isEmpty()) {
            throw new InvalidIdentification(invalid);
        }

        return result;
    }


    public void removeRequest(int subscriptionId) {
        if(!subscriptions.containsKey(subscriptionId)) {
            log.warn("remove requested for an invalid subscription id {}", subscriptionId);
            return;
        }
        prm.removeRequest(subscriptionId);
    }

    
    public void removeItemsFromRequest(int subscriptionId,   List<NamedObjectId> parameterIds, AuthenticationToken authToken) throws NoPermissionException {
        Subscription subscr = subscriptions.get(subscriptionId);
        if(subscr==null) {
            log.warn("remove requested for an invalid subscription id {}", subscriptionId);
            return;
        }

        List<Parameter> paramsToRemove = new ArrayList<>();
        synchronized(subscr) {
            Iterator<Entry<Parameter, NamedObjectId>> it = subscr.params.entries().iterator();
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
        Subscription subscription = subscriptions.get(subscriptionId);
        if(subscription==null) { //probably the subscription has just been removed
            log.debug("Received an updateItems for an unknown subscription {}", subscriptionId);
            return;
        }
        
        List<ParameterValueWithId> plist = new ArrayList<>(items.size());
        synchronized(subscription) {
            if(subscription.checkExpiration) {
                long now = getAquisitionTime(items);
                
                List<ParameterValueWithId> expired  = updateAndCheckExpiration(subscription, items, now);
                if(!expired.isEmpty()) {
                    log.debug("Updating {} parameters due to expiration");
                    listener.update(subscriptionId, expired);
                }
                
            }

            for(ParameterValue pv: items) {
                addValueForAllIds(plist, subscription, pv);
            }
        }
        listener.update(subscriptionId, plist);
    }


    private long getAquisitionTime(List<ParameterValue> items) {
        for(ParameterValue pv: items) {
            if(pv.hasAcquisitionTime()) {
                return pv.getAcquisitionTime();
            }
        }
        return TimeEncoding.getWallclockTime();
    }

    //adds the pv into plist with all ids subscribed
    private void addValueForAllIds(List<ParameterValueWithId> plist, Subscription subscription, ParameterValue pv) {
        Parameter p = pv.getParameter();
        List<NamedObjectId> idList = subscription.get(p);
        if(idList==null || idList.isEmpty()) {
            log.warn("Received values for a parameter not subscribed: {}", pv.getParameter());
            return;
        }

        for(NamedObjectId id:idList) {
            ParameterValueWithId pvwi = new ParameterValueWithId(pv, id);
            plist.add(pvwi);
        }
    }
    

    private void checkPeriodicExpiration() {
        for(Map.Entry<Integer,Subscription>me: subscriptions.entrySet()) {
            Subscription subscription = me.getValue();
            synchronized(subscription) {
                long now = TimeEncoding.getWallclockTime();
                if((subscription.checkExpiration) && (now-subscription.lastExpirationCheck>CHECK_EXPIRATION_INTERVAL)) {
                    List<ParameterValueWithId> expired = checkExpiration(subscription, now);
                    if(!expired.isEmpty()) {
                        log.debug("Updating {} parameters due to expiration");
                        listener.update(me.getKey(), expired);
                    }
                }
            }
        }
    }
    

    //update the expiration list with new values and check expiration of parameters that are just updating
    // in case the expiration is shorter than the check interval - this method would detect and send the parameters that have just expired
    private List<ParameterValueWithId> updateAndCheckExpiration(Subscription subscription, List<ParameterValue> items, long now) {
        List<ParameterValueWithId> expired = new ArrayList<>();
        for(ParameterValue pv: items) {
            Parameter p = pv.getParameter();
            ParameterValue oldPv = subscription.pvexp.put(p, pv);
            if((oldPv!=null) && oldPv.hasExpirationTime() 
                    && oldPv.getAcquisitionStatus()==AcquisitionStatus.ACQUIRED && oldPv.getExpirationTime()<now) {
                
                ParameterValue tmp = new ParameterValue(oldPv); //make a copy because this is shared by other subscribers
                tmp.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                addValueForAllIds(expired, subscription, tmp);
            }
        }
        return expired;
    }

    //check expiration of all parameters from subscription
    private List<ParameterValueWithId> checkExpiration(Subscription subscription, long now) {
        List<ParameterValueWithId> expired = new ArrayList<>();
        for(ParameterValue pv: subscription.pvexp.values()) {
            if(pv.getAcquisitionStatus()==AcquisitionStatus.ACQUIRED && pv.hasExpirationTime()
                    && pv.getExpirationTime()<now) {
                ParameterValue tmp = new ParameterValue(pv); //make a copy because this is shared by other subscribers
                tmp.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                subscription.pvexp.put(pv.getParameter(), tmp);
                addValueForAllIds(expired, subscription, tmp);
             } 
        }
        subscription.lastExpirationCheck = now;
        return expired;
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
        List<ParameterValueWithId> plist = new ArrayList<>(values.size());


        for(ParameterValue pv: values) {
            List<NamedObjectId> l = lm.get(pv.getParameter());
            if(l==null) {
                log.warn("Received values for a parameter not requested: {}", pv.getParameter());
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
        if(!Privilege.getInstance().hasPrivilege1(authToken, Privilege.Type.TM_PARAMETER, parameterName)) {
            throw  new NoPermissionException("User " + authToken + " has no permission for parameter "  + parameterName);
        }
    }


    static class Subscription {
        ListMultimap<Parameter, NamedObjectId> params;
        boolean checkExpiration = false;
        long lastExpirationCheck = -1;
        //contains the parameters that have an expiration time set
        Map<Parameter, ParameterValue> pvexp; 

        public Subscription(ListMultimap<Parameter, NamedObjectId> lm, boolean checkExpiration) {
            this.params = lm;
            this.checkExpiration = checkExpiration;
            if(checkExpiration) {
                pvexp = new HashMap<>();
            }
        }


        public List<NamedObjectId> get(Parameter parameter) {
            return params.get(parameter);
        }
        public boolean put(Parameter p, NamedObjectId id) {
            return params.put(p,  id);
        }
        public boolean containsEntry(Parameter p, NamedObjectId id) {
            return params.containsEntry(p,  id);
        }
    }
}

