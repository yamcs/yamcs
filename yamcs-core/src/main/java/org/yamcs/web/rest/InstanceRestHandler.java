package org.yamcs.web.rest;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.CreateInstanceRequest;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance.InstanceState;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.utils.parser.FilterParser;
import org.yamcs.utils.parser.FilterParser.Result;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.TokenMgrError;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;

import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Handles incoming requests related to yamcs instances.
 */
public class InstanceRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);
    private static Pattern ALLOWED_INSTANCE_NAMES = Pattern.compile("\\w[\\w\\.-]*");

    @Route(path = "/api/instances", method = "GET")
    public void listInstances(RestRequest req) throws HttpException {
        Predicate<YamcsServerInstance> filter = getFilter(req.getQueryParameterList("filter"));
        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
        for (YamcsServerInstance instance : YamcsServer.getInstances()) {
            if (filter.test(instance)) {
                YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, instance.getInstanceInfo());
                instancesb.addInstance(enriched);
            }
        }
        completeOK(req, instancesb.build());
    }

    @Route(path = "/api/instances/:instance", method = "GET")
    public void getInstance(RestRequest req) throws HttpException {
        String instanceName = verifyInstance(req, req.getRouteParam("instance"));
        YamcsServerInstance instance = yamcsServer.getInstance(instanceName);
        YamcsInstance instanceInfo = instance.getInstanceInfo();
        YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, instanceInfo);
        completeOK(req, enriched);
    }

    @Route(path = "/api/instances/:instance/clients", method = "GET")
    public void listClientsForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ConnectedClient client : clients) {
            if (client.getProcessor() != null && instance.equals(client.getProcessor().getInstance())) {
                responseb.addClient(YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED));
            }
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/instances/:instance", method = { "PATCH", "PUT", "POST" })
    public void editInstance(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlServices);

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        String state;
        if (req.hasQueryParameter("state")) {
            state = req.getQueryParameter("state");
        } else {
            throw new BadRequestException("No state specified");
        }

        CompletableFuture<YamcsServerInstance> cf;
        switch (state.toLowerCase()) {
        case "stop":
        case "stopped":
            if (yamcsServer.getInstance(instance) == null) {
                throw new BadRequestException("No instance named '" + instance + "'");
            }
            cf = CompletableFuture.supplyAsync(() -> {
                return yamcsServer.stopInstance(instance);
            });
            break;
        case "restarted":
            cf = CompletableFuture.supplyAsync(() -> {
                try {
                    return yamcsServer.restartInstance(instance);
                } catch (IOException e) {
                    throw new UncheckedExecutionException(e);
                }
            });
            break;
        case "running":
            cf = CompletableFuture.supplyAsync(() -> {
                log.info("Starting instance {}", instance);
                try {
                    return yamcsServer.startInstance(instance);
                } catch (IOException e) {
                    throw new UncheckedExecutionException(e);
                }
            });
            break;
        default:
            throw new BadRequestException("Unsupported service state '" + state + "'");
        }
        cf.whenComplete((v, error) -> {
            if (error == null) {
                YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, v.getInstanceInfo());
                completeOK(req, enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                log.error("Error when changing instance state to {}", state, t);
                completeWithError(req, new InternalServerErrorException(t));
            }
        });
    }

    @Route(path = "/api/instances", method = { "PATCH", "PUT", "POST" })
    public void createInstance(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.CreateInstances);
        CreateInstanceRequest request = req.bodyAsMessage(CreateInstanceRequest.newBuilder()).build();

        if (!request.hasName()) {
            throw new BadRequestException("No instance name was specified");
        }
        String instanceName = request.getName();
        if (!ALLOWED_INSTANCE_NAMES.matcher(instanceName).matches()) {
            throw new BadRequestException("Invalid instance name");
        }
        if (!request.hasTemplate()) {
            throw new BadRequestException("No template was specified");
        }
        if (yamcsServer.getInstance(instanceName) != null) {
            throw new BadRequestException("An instance named '" + instanceName + "' already exists");
        }

        CompletableFuture<YamcsServerInstance> cf = CompletableFuture.supplyAsync(() -> {
            try {
                yamcsServer.createInstance(instanceName, request.getTemplate(),
                        request.getTemplateArgsMap(),
                        request.getLabelsMap());
                return yamcsServer.startInstance(instanceName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        cf.whenComplete((v, error) -> {
            if (error == null) {
                YamcsInstance instanceInfo = v.getInstanceInfo();
                YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, instanceInfo);
                completeOK(req, enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                log.error("Error when creating instance {}", instanceName, t);
                completeWithError(req, new InternalServerErrorException(t));
            }
        });
    }

    private Predicate<YamcsServerInstance> getFilter(List<String> flist) throws HttpException {
        if (flist == null) {
            return ysi -> true;
        }

        FilterParser fp = new FilterParser((StringReader) null);

        Predicate<YamcsServerInstance> pred = ysi -> true;
        for (String filter : flist) {
            fp.ReInit(new StringReader(filter));
            FilterParser.Result pr;
            try {
                pr = fp.parse();
                pred = pred.and(getPredicate(pr));
            } catch (ParseException | TokenMgrError e) {
                throw new BadRequestException("Cannot parse the filter '" + filter + "': " + e.getMessage());
            }

        }
        return pred;
    }

    private Predicate<YamcsServerInstance> getPredicate(Result pr) throws HttpException {

        if ("state".equals(pr.key)) {
            try {
                InstanceState state = InstanceState.valueOf(pr.value.toUpperCase());
                switch (pr.op) {
                case EQUAL:
                    return ysi -> ysi.state() == state;
                case NOT_EQUAL:
                    return ysi -> ysi.state() != state;
                default:
                    throw new IllegalStateException("Unknown operator " + pr.op);
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Unknown state '" + pr.value + "'. Valid values are: " + Arrays.asList(InstanceState.values()));
            }
        } else if (pr.key.startsWith("label:")) {
            String labelKey = pr.key.substring(6);
            return ysi -> {
                Map<String, ?> labels = ysi.getLabels();
                if (labels == null) {
                    return false;
                }
                Object o = labels.get(labelKey);
                if (o == null) {
                    return false;
                }
                switch (pr.op) {
                case EQUAL:
                    return pr.value.equals(o);
                case NOT_EQUAL:
                    return !pr.value.equals(o);
                default:
                    throw new IllegalStateException("Unknown operator " + pr.op);
                }
            };
        } else {
            throw new BadRequestException("Unknown filter key '" + pr.key + "'");
        }
    }
}
