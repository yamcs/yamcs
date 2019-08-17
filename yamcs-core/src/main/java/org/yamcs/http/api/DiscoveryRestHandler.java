package org.yamcs.http.api;

import java.util.Collections;
import java.util.List;

import org.yamcs.http.HttpException;
import org.yamcs.protobuf.Rest.EndpointInfo;
import org.yamcs.protobuf.Rest.ListEndpointsResponse;

/**
 * Handles incoming requests related to api endpoints
 */
public class DiscoveryRestHandler extends RestHandler {

    private Router router;

    public DiscoveryRestHandler(Router router) {
        this.router = router;
    }

    @Route(rpc = "yamcs.protobuf.rest.Discovery.ListEndpoints")
    public void listEndpoints(RestRequest req) throws HttpException {
        List<EndpointInfo> endpoints = router.getEndpointInfoSet();
        Collections.sort(endpoints, (e1, e2) -> {
            int rc = e1.getUrl().compareToIgnoreCase(e2.getUrl());
            return rc != 0 ? rc : e1.getMethod().compareTo(e2.getMethod());
        });

        ListEndpointsResponse.Builder responseb = ListEndpointsResponse.newBuilder();
        responseb.addAllEndpoints(endpoints);
        completeOK(req, responseb.build());
    }

    public void getMessage(RestRequest req) throws HttpException {

    }
}
