package org.yamcs.web.rest;

import java.util.HashSet;
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
import org.yamcs.protobuf.Cvalue.ContainerData;
import org.yamcs.protobuf.Rest.RestGetParameterRequest;
import org.yamcs.protobuf.SchemaCvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Handles incoming requests related to realtime Containers (get).
 * 
 * <p>
 * /(instance)/api/container
 */
public class ContainerRequestHandler implements RestRequestHandler {
	final static Logger log = LoggerFactory.getLogger(ParameterRequestHandler.class.getName());

	@Override
	public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
		if (!req.hasPathSegment(pathOffset)) {
			throw new NotFoundException(req);
		}

		YProcessor processor = YProcessor.getInstance(req.yamcsInstance, "realtime");

		switch (req.getPathSegment(pathOffset)) {
		case "_get":
			return getContainers(req, processor);

		default:
			throw new MethodNotAllowedException(req);
		}
	}

	/**
	 * Gets parameter values
	 */
	private RestResponse getContainers(RestRequest req, YProcessor processor) throws RestException {
		RestGetParameterRequest request = req.bodyAsMessage(SchemaRest.RestGetParameterRequest.MERGE).build();
		if (request.getListCount() == 0) {
			throw new BadRequestException("Empty parameter list");
		}
		ParameterRequestManagerImpl prm = processor.getParameterRequestManager();
		MyConsumer consumer = new MyConsumer();
		ContainerWithIdRequestHelper cidrh = new ContainerWithIdRequestHelper(prm, consumer);

		List<NamedObjectId> idList = request.getListList();
		ContainerData.Builder cdata = ContainerData.newBuilder();

		Set<String> remaining = new HashSet<>();
		for (NamedObjectId id : idList) {
			remaining.add(id.getNamespace() + "/" + id.getName());
		}

		NamedObjectId noi = NamedObjectId.getDefaultInstance();

		try {
			long timeout = getTimeout(request);
			int subscrriptionId = cidrh.subscribeContainers(idList, req.authToken);
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
					cdata.addContainer(cvid.toGbpContainerData());
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

		return new RestResponse(req, cdata.build(), SchemaCvalue.ContainerData.WRITE);
	}

	private long getTimeout(RestGetParameterRequest request) throws BadRequestException {
		long timeout = 10000;
		if (request.hasTimeout()) {
			timeout = request.getTimeout();
			if (timeout > 60000) {
				throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
			}
		}
		return timeout;
	}

	class MyConsumer implements ContainerWithIdConsumer {
		LinkedBlockingQueue<List<ContainerValueWithId>> queue = new LinkedBlockingQueue<>();

		@Override
		public void update(int subscriptionId, List<ContainerValueWithId> containers) {
			queue.add(containers);
		}
	}
}
