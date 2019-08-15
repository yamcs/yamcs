package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.http.HttpException;
import org.yamcs.protobuf.InstanceTemplate;
import org.yamcs.protobuf.Rest.ListInstanceTemplatesResponse;

public class TemplateRestHandler extends RestHandler {

    @Route(path = "/api/instance-templates", method = "GET")
    public void listInstanceTemplates(RestRequest req) {
        ListInstanceTemplatesResponse.Builder templatesb = ListInstanceTemplatesResponse.newBuilder();

        List<InstanceTemplate> templates = new ArrayList<>(YamcsServer.getInstanceTemplates());
        templates.sort((t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));

        for (InstanceTemplate template : templates) {
            templatesb.addTemplates(template);
        }
        completeOK(req, templatesb.build());
    }

    @Route(path = "/api/instance-templates/{template}", method = "GET")
    public void getInstanceTemplate(RestRequest req) throws HttpException {
        String name = verifyInstanceTemplate(req, req.getRouteParam("template"));
        InstanceTemplate template = yamcsServer.getInstanceTemplate(name);
        completeOK(req, template);
    }
}
