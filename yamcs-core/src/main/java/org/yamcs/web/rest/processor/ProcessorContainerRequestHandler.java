package org.yamcs.web.rest.processor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.container.ContainerValueWithId;
import org.yamcs.container.ContainerWithIdConsumer;
import org.yamcs.container.ContainerWithIdRequestHelper;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.protobuf.Cvalue.ContainerValue;
import org.yamcs.protobuf.Rest.BulkGetContainerValueRequest;
import org.yamcs.protobuf.Rest.BulkGetContainerValueResponse;
import org.yamcs.protobuf.SchemaCvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelFuture;

/**
 * Handles incoming requests related to realtime Containers (get).
 * 
 * <p>
 * 
 */
public class ProcessorContainerRequestHandler extends RestHandler {
	final static Logger log = LoggerFactory.getLogger(ProcessorContainerRequestHandler.class.getName());
	
	@Route(path = "/api/processors/:instance/:processor/containers/:name*", method = "GET")
    public ChannelFuture getContainerValue(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        
        XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
        SequenceContainer container = verifyContainer(req, mdb, req.getRouteParam("name"));
                
        long timeout = 10000;
        if (req.hasQueryParameter("timeout")) { 
        	timeout = req.getQueryParameterAsLong("timeout");
        }
        
        NamedObjectId id = NamedObjectId.newBuilder().setName(container.getQualifiedName()).build();
        List<NamedObjectId> ids = Arrays.asList(id);
        List<ContainerValue> cvals = doGetContainerValues(processor, req.getAuthToken(), req, ids, timeout);

        ContainerValue cval;
        if (cvals.isEmpty()) {
            cval = ContainerValue.newBuilder().setId(id).build();
        } else {
            cval = cvals.get(0);
        }
            
        return sendOK(req, cval, SchemaCvalue.ContainerValue.WRITE);
    }	
	
	@Route(path = "/api/processors/:instance/:processor/containers/mget", method = "GET", priority = true)
    public ChannelFuture getContainerValues(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));        
        
        BulkGetContainerValueRequest request = req.bodyAsMessage(SchemaRest.BulkGetContainerValueRequest.MERGE).build();
        if (request.getIdCount() == 0) {
            throw new BadRequestException("Empty parameter list");
        }
                                      
        long timeout = 10000;
        
        if (request.hasTimeout()) { 
        	timeout = request.getTimeout();
        }
        
        if (req.hasQueryParameter("timeout")) { 
        	timeout = req.getQueryParameterAsLong("timeout");
        }
        
        List<NamedObjectId> ids = request.getIdList();
        List<ContainerValue> cvals = doGetContainerValues(processor, req.getAuthToken(), req, ids, timeout);

        BulkGetContainerValueResponse.Builder bgcve = BulkGetContainerValueResponse.newBuilder();
        bgcve.addAllValue(cvals);        
        return sendOK(req, bgcve.build(), SchemaRest.BulkGetContainerValueResponse.WRITE);
    }	
	
	
	
	/**
	 * Query the value of containers through the processor
	 * 
	 * @param processor
	 * @param authToken
	 * @param req
	 * @param idList
	 * @param timeout
	 * @return
	 * @throws RestException
	 */
	private List<ContainerValue> doGetContainerValues(YProcessor processor, AuthenticationToken authToken, 
			RestRequest req, List<NamedObjectId> idList, long timeout) throws HttpException { 
        if (timeout > 60000) {
            throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
        }
        

		ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
		MyConsumer consumer = new MyConsumer();
		ContainerWithIdRequestHelper cidrh = new ContainerWithIdRequestHelper(prm, consumer);
		
		List<ContainerValue> clist = new LinkedList<>();

		Set<String> remaining = new HashSet<>();
		for (NamedObjectId id : idList) {
			remaining.add(id.getNamespace() + "/" + id.getName());
		}

		try {
			int subscrriptionId = cidrh.subscribeContainers(idList, authToken);
			long t0 = System.currentTimeMillis();
			long t1;
			while (true) {
				t1 = System.currentTimeMillis();
				long remainingTime = timeout - (t1 - t0);
				List<ContainerValueWithId> enqueued = consumer.queue.poll(remainingTime, TimeUnit.MILLISECONDS);
				if (enqueued == null) {
					break;
				}

				for (ContainerValueWithId cvid : enqueued) {
					clist.add(cvid.toGbpContainerData());
					remaining.remove(cvid.getId().getNamespace() + "/" + cvid.getId().getName());
				}

				if (remaining.isEmpty()) {
					break;
				}
			}
			cidrh.removeSubscription(subscrriptionId);

		} catch (InvalidIdentification e) {
			throw new BadRequestException("Invalid parameters: " + e.invalidParameters.toString());
		} catch (InterruptedException e) {
			throw new InternalServerErrorException("Interrupted while waiting for containers");
		} catch (NoPermissionException e) {
			throw new ForbiddenException(e.getMessage(), e);
		}

		return clist;				
	}
	
	class MyConsumer implements ContainerWithIdConsumer {
		LinkedBlockingQueue<List<ContainerValueWithId>> queue = new LinkedBlockingQueue<>();

		@Override
		public void update(int subscriptionId, List<ContainerValueWithId> containers) {
			queue.add(containers);
		}
	}
}
