package org.yamcs.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.ParameterValue;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.web.websocket.ContainerResource;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.XtceDb;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * This manager for providing extracted parameters of a specified
 * SequenceContainer. The reasons of having this manager are - We cannot just
 * extend RawContainerRequestManager (previously ContainerRequestManager),
 * because we need AlarmChecker. - Improving ParameterRequestManager to support
 * SequenceContainer results a mixing of Parameter and SequenceContainer
 * 
 * This manager use the above two manager to provide both raw and extracted
 * sequence container with minimal impact of all the logics in the YProcessor.
 * Major change: ParameterRequestManager receives the list of
 * ContainerExtractionResult (if exist) then forwards to the ParameterConsumer.
 * 
 * @author dho
 *
 */
public class ContainerWithIdRequestHelper implements ParameterConsumer {
	private Logger log;

	private ParameterRequestManagerImpl prm;
	private ContainerWithIdConsumer consumer;

	private ConcurrentHashMap<SequenceContainer, Set<Parameter>> cachedParameterSet = new ConcurrentHashMap<>();
	Map<Integer, ListMultimap<SequenceContainer, NamedObjectId>> subscriptions = new ConcurrentHashMap<Integer, ListMultimap<SequenceContainer, NamedObjectId>>();

	public ContainerWithIdRequestHelper(ParameterRequestManagerImpl prm, ContainerWithIdConsumer consumer) {
		log = LoggerFactory.getLogger(ContainerResource.class.getName() + "[" + prm.yproc.getInstance() + "]");
		this.prm = prm;
		this.consumer = consumer;
	}

	/**
	 * Create a new subscription, add a list of containers in it and register
	 * the corresponding consumer
	 * 
	 * @param containers
	 * @param consumer
	 * @return
	 * @throws InvalidIdentification
	 * @throws NoPermissionException
	 */
	public int subscribeContainers(Collection<NamedObjectId> idList, AuthenticationToken authToken)
			throws InvalidIdentification, NoPermissionException {
		int subscriptionId = prm.addRequest(new LinkedList<Parameter>(), this);
		subscribeContainers(subscriptionId, idList, authToken);
		return subscriptionId;
	}

	/**
	 * Add a list of containers to an existing subscription
	 * 
	 * @param subscriptionId
	 * @param containers
	 * @return
	 * @throws InvalidIdentification
	 * @throws NoPermissionException
	 */
	public void subscribeContainers(int subscriptionId, Collection<NamedObjectId> idList, AuthenticationToken authToken)
			throws InvalidIdentification, NoPermissionException {
		log.debug("Subscribing to " + idList.size() + " container(s)");
		for (NamedObjectId id : idList) {
			subscribeContainer(subscriptionId, id, authToken);
		}
	}

	/**
	 * Add a container to an existing subscription
	 * 
	 * @param subscriptionId
	 * @param container
	 * @return
	 * @throws InvalidIdentification
	 * @throws NoPermissionException
	 */
	public void subscribeContainer(int subscriptionId, NamedObjectId id, AuthenticationToken authToken)
			throws InvalidIdentification, NoPermissionException {
		ListMultimap<SequenceContainer, NamedObjectId> subscription = subscriptions.get(subscriptionId);
		if (subscription == null) {
			subscription = ArrayListMultimap.create();
			subscriptions.put(subscriptionId, subscription);
		}

		XtceDb xtcedb = prm.getXtceDb();
		SequenceContainer sc = xtcedb.getSequenceContainer(id);

		if (sc == null) {
			throw new InvalidIdentification(id);
		}

		__checkContainerPrivilege(authToken, sc.getName());

		Set<Parameter> parameters = __getParameters(sc);
		for (Parameter param : parameters) {
			__checkParameterPrivilege(authToken, param.getName());
		}

		synchronized (subscription) {
			subscription.put(sc, id);

			if (subscription.get(sc).size() == 1) {
				List<Parameter> paramList = new ArrayList<>();
				paramList.addAll(parameters);
				prm.addItemsToRequest(subscriptionId, paramList);
			}
		}
	}

	/**
	 * Remove all the containers in the specified list from an existing
	 * subscription
	 * 
	 * @param subcriptionId
	 * @param containers
	 * @throws NoPermissionException
	 */
	public void unsubscribeContainer(int subscriptionId, Collection<NamedObjectId> idList,
			AuthenticationToken authToken) throws NoPermissionException {
		for (NamedObjectId id : idList) {
			unsubscribeContainer(subscriptionId, id, authToken);
		}
	}

	/**
	 * Remove a container from an existing subscription
	 * 
	 * @param subscriptionId
	 * @param container
	 * @throws NoPermissionException
	 */
	public void unsubscribeContainer(int subscriptionId, NamedObjectId id, AuthenticationToken authToken)
			throws NoPermissionException {
		ListMultimap<SequenceContainer, NamedObjectId> subscription = subscriptions.get(subscriptionId);
		if (subscription == null) {
			log.warn("Trying to remove object from unknown subscription");
			return;
		}

		XtceDb xtcedb = prm.getXtceDb();
		SequenceContainer sc = xtcedb.getSequenceContainer(id);

		__checkContainerPrivilege(authToken, sc.getName());
		Set<Parameter> parameters = __getParameters(sc);
		for (Parameter param : parameters) {
			__checkParameterPrivilege(authToken, param.getName());
		}

		synchronized (subscription) {
			subscription.remove(sc, id);
			if (subscription.size() == 0) {
				List<Parameter> paramList = new ArrayList<>();
				paramList.addAll(__getParameters(sc));
				prm.removeItemsFromRequest(subscriptionId, paramList);
			}
		}
	}

	/**
	 * Remove a whole subscription
	 * 
	 * @param subscriptionId
	 */
	public void removeSubscription(int subscriptionId) {
		if (subscriptions.containsKey(subscriptionId)) {
			subscriptions.remove(subscriptionId);
			prm.removeRequest(subscriptionId);
		}
	}

	/**
	 * Retrieve from the cache all the parameters contained in the specified
	 * container. If they are missing in the current cache, they will be added
	 * before returned to the caller
	 * 
	 * @param container
	 * @return
	 */
	private Set<Parameter> __getParameters(SequenceContainer container) {
		Set<Parameter> result = cachedParameterSet.get(container);

		if (result == null) {
			result = new HashSet<>();
			__indexParameters(container, result);
			cachedParameterSet.put(container, result);
		}

		return result;
	}

	/**
	 * Retrieve recursively all the parameters contained in the specified
	 * container
	 * 
	 * @param container
	 * @param parameters
	 */
	private void __indexParameters(SequenceContainer container, Set<Parameter> parameters) {
		if (container != null) {
			for (SequenceEntry se : container.getEntryList()) {
				if (se instanceof ContainerEntry) {
					__indexParameters(((ContainerEntry) se).getRefContainer(), parameters);
				} else if (se instanceof ParameterEntry) {
					parameters.add(((ParameterEntry) se).getParameter());
				}
			}
			__indexParameters(container.getBaseContainer(), parameters);
		}
	}

	/**
	 * Check if the user has a privilege for the specified parameter name
	 * 
	 * @param authToken
	 * @param parameterName
	 * @throws NoPermissionException
	 */
	private void __checkParameterPrivilege(AuthenticationToken authToken, String parameterName)
			throws NoPermissionException {
		if (!Privilege.getInstance().hasPrivilege(authToken, Privilege.Type.TM_PARAMETER, parameterName)) {
			throw new NoPermissionException("User " + authToken + " has no permission for parameter " + parameterName);
		}
	}

	/**
	 * Check if the user has a privilege for the specified container name
	 * 
	 * @param authToken
	 * @param parameterName
	 * @throws NoPermissionException
	 */
	private void __checkContainerPrivilege(AuthenticationToken authToken, String containerName)
			throws NoPermissionException {
		if (!Privilege.getInstance().hasPrivilege(authToken, Privilege.Type.TM_PACKET, containerName)) {
			throw new NoPermissionException("User " + authToken + " has no permission for container " + containerName);
		}
	}

	@Override
	public void updateItems(int subscriptionId, List<ContainerExtractionResult> containers,
			List<ParameterValue> items) {
		if (containers == null || containers.isEmpty()) {
			return;
		}

		ListMultimap<SequenceContainer, NamedObjectId> subscription = subscriptions.get(subscriptionId);
		for (ContainerExtractionResult container : containers) {
			SequenceContainer sc = container.getContainer();
			List<NamedObjectId> listId = subscription.get(sc);

			if (!listId.isEmpty()) {
				Set<Parameter> subscribed = cachedParameterSet.get(sc);
				List<ParameterValue> paramList = new LinkedList<>();

				for (ParameterValue pv : items) {
					if (subscribed.contains(pv.getParameter())) {
						paramList.add(pv);
					}
				}

				List<ContainerValueWithId> containerValues = new LinkedList<>();
				for (NamedObjectId id : listId) {
					ContainerValueWithId containerValue = new ContainerValueWithId();
					containerValue.setId(id);
					containerValue.setParamVals(paramList);
					containerValues.add(containerValue);
				}

				consumer.update(subscriptionId, containerValues);
			}
		}
	}

	public synchronized void switchPrm(ParameterRequestManagerImpl newPrm, AuthenticationToken authToken)
			throws InvalidIdentification, NoPermissionException {

		for (int subscriptionId : subscriptions.keySet()) {
			List<Parameter> plist = prm.removeRequest(subscriptionId);
			// checking permission
			for (Parameter p : plist)
				__checkParameterPrivilege(authToken, p.getQualifiedName());

			newPrm.addRequest(subscriptionId, plist, this);
		}

		prm = newPrm;
	}
}
