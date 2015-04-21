package org.yamcs.web.rest;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.yamcs.Channel;
import org.yamcs.InvalidIdentification;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.parameter.SoftwareParameterManager;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.RestGetParameterRequest;
import org.yamcs.protobuf.Rest.RestSetParameterResponse;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.Parameter;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

/**
 * Handles incoming requests related to realtime Parameters (get/set).
 * 
 */
public class ParameterRequestHandler extends AbstractRestRequestHandler {

    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri) throws RestException {
	org.yamcs.Channel yamcsChannel = org.yamcs.Channel.getInstance(yamcsInstance, "realtime");

	QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
	if ("_get".equals(qsDecoder.path())) {
	    RestGetParameterRequest request = readMessage(req, SchemaRest.RestGetParameterRequest.MERGE).build();
	    ParameterData pdata = getParameters(request, yamcsChannel);
	    writeMessage(ctx, req, qsDecoder, pdata, SchemaPvalue.ParameterData.WRITE);
	} else if ("_set".equals(qsDecoder.path())) {
	    ParameterData pdata = readMessage(req, SchemaPvalue.ParameterData.MERGE).build();
	    RestSetParameterResponse response = setParameters(pdata, yamcsChannel);

	    writeMessage(ctx, req, qsDecoder, response, SchemaRest.RestSetParameterResponse.WRITE);
	} else {
	    String fqname = "/"+qsDecoder.path();
	    NamedObjectId id = NamedObjectId.newBuilder().setName(fqname).build();
	    Parameter p = yamcsChannel.getXtceDb().getParameter(id);
	    if(p==null) {
		sendError(ctx, NOT_FOUND);
		return;
	    }
	    if(req.getMethod()==HttpMethod.GET) {
		ParameterValue pv = getParameterFromCache(id, p, yamcsChannel);
		if(pv==null) {
		    sendError(ctx, NOT_FOUND);
		} else {
		    writeMessage(ctx, req, qsDecoder, pv, SchemaPvalue.ParameterValue.WRITE);
		}
	    } else if(req.getMethod()==HttpMethod.POST) {
		 Value v = readMessage(req, SchemaYamcs.Value.MERGE).build();
		 RestSetParameterResponse response = setParameter(p, v, yamcsChannel);
		 writeMessage(ctx, req, qsDecoder, response, SchemaRest.RestSetParameterResponse.WRITE);
	    } else {
		throw new BadRequestException("Only GET and POST methods supported for parameter");
	    }
	}
    }




    private ParameterValue getParameterFromCache(NamedObjectId id, Parameter p, Channel yamcsChannel) throws BadRequestException {
	//TODO permissions
	ParameterRequestManagerImpl prm = yamcsChannel.getParameterRequestManager();
	if(!prm.hasParameterCache()) {
	    throw new BadRequestException("ParameterCache not activated for this channel");
	}
	org.yamcs.ParameterValue pv = prm.getValueFromCache(p);
	if(pv==null)   return null;
	return pv.toGpb(id);
    }


    /**
     * sets single parameter
     */
    private RestSetParameterResponse setParameter(Parameter p, Value v, Channel yamcsChannel) throws BadRequestException {
	SoftwareParameterManager spm = yamcsChannel.getParameterRequestManager().getSoftwareParameterManager();
	if(spm==null) {
	    throw new BadRequestException("SoftwareParameterManager not activated for this channel");
	}
	
	try {
	    spm.updateParameter(p, v);
	} catch (IllegalArgumentException e) {
	    throw new BadRequestException(e.getMessage());
	}
	return RestSetParameterResponse.newBuilder().build();
    }
    
    /**
     * sets multiple parameters parameters
     */
    private RestSetParameterResponse setParameters(ParameterData pdata, org.yamcs.Channel yamcsChannel) throws RestException {
	//TODO permissions
	
	SoftwareParameterManager spm = yamcsChannel.getParameterRequestManager().getSoftwareParameterManager();
	if(spm==null) {
	    throw new BadRequestException("SoftwareParameterManager not activated for this channel");
	}
	try {
	    spm.updateParameters(pdata.getParameterList());
	} catch (IllegalArgumentException e) {
	    throw new BadRequestException(e.getMessage());
	}

	return RestSetParameterResponse.newBuilder().build();
    }

    /**
     * Gets parameter values
     */
    private ParameterData getParameters(RestGetParameterRequest request, org.yamcs.Channel yamcsChannel) throws RestException {
	if(request.getListCount()==0) {
	    throw new BadRequestException("Empty parameter list");
	}
	//TODO permissions
	ParameterRequestManagerImpl prm = yamcsChannel.getParameterRequestManager();
	MyConsumer myConsumer = new MyConsumer();
	ParameterWithIdRequestHelper pwirh = new ParameterWithIdRequestHelper(prm, myConsumer);
	List<NamedObjectId> idList = request.getListList();
	ParameterData.Builder pdatab = ParameterData.newBuilder();
	try {
	    if(request.hasFromCache()) {
		if(!prm.hasParameterCache()) {
		    throw new BadRequestException("ParameterCache not activated for this channel");
		}
		List<ParameterValueWithId> l;
		l = pwirh.getValuesFromCache(idList);
		for(ParameterValueWithId pvwi: l) {
		    pdatab.addParameter(pvwi.toGbpParameterValue());
		}
	    } else {

		long timeout = getTimeout(request);
		int reqId = pwirh.addRequest(idList);
		long t0 = System.currentTimeMillis();
		long t1;
		while(true) {
		    t1 = System.currentTimeMillis();
		    long remaining = timeout - (t1-t0);
		    List<ParameterValueWithId> l = myConsumer.queue.poll(remaining, TimeUnit.MILLISECONDS);
		    if(l==null) break;

		    for(ParameterValueWithId pvwi: l) {
			pdatab.addParameter(pvwi.toGbpParameterValue());
		    }
		    //TODO: this may not be correct: if we get a parameter multiple times, we stop here before receiving all parameters
		    if(pdatab.getParameterCount() == idList.size()) break;
		} 
		pwirh.removeRequest(reqId);
	    }

	}  catch (InvalidIdentification e) {
	    //TODO - send the invalid parameters in a parsable form
	    throw new BadRequestException("Invalid parameters: "+e.invalidParameters.toString());
	} catch (InterruptedException e) {
	    throw new InternalServerErrorException("Interrupted while waiting for prameters");
	}

	return pdatab.build();
    }


    private long getTimeout(RestGetParameterRequest request) throws BadRequestException {
	long timeout = 10000;
	if(request.hasTimeout()) {
	    timeout = request.getTimeout();
	    if(timeout>60000) {
		throw new BadRequestException("Invalid timeout specified. Maximum is 60.000 milliseconds");
	    }
	}
	return timeout;
    }


    class MyConsumer implements ParameterWithIdConsumer {
	LinkedBlockingQueue<List<ParameterValueWithId>> queue = new LinkedBlockingQueue<List<ParameterValueWithId>>();

	@Override
	public void update(int subscriptionId, List<ParameterValueWithId> params) {
	    queue.add(params);
	}
    }
}
