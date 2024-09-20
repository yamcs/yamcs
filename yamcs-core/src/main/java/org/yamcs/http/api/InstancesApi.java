package org.yamcs.http.api;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.yamcs.InstanceMetadata;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.archive.CcsdsTmIndex;
import org.yamcs.filetransfer.FileTransferService;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.mdb.Mdb;
import org.yamcs.plists.ParameterListService;
import org.yamcs.protobuf.AbstractInstancesApi;
import org.yamcs.protobuf.CreateInstanceRequest;
import org.yamcs.protobuf.GetInstanceRequest;
import org.yamcs.protobuf.GetInstanceTemplateRequest;
import org.yamcs.protobuf.InstanceTemplate;
import org.yamcs.protobuf.ListInstanceTemplatesResponse;
import org.yamcs.protobuf.ListInstancesRequest;
import org.yamcs.protobuf.ListInstancesResponse;
import org.yamcs.protobuf.ReconfigureInstanceRequest;
import org.yamcs.protobuf.RestartInstanceRequest;
import org.yamcs.protobuf.StartInstanceRequest;
import org.yamcs.protobuf.StopInstanceRequest;
import org.yamcs.protobuf.TemplateVariable;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.protobuf.YamcsInstance.InstanceState;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.templating.Template;
import org.yamcs.templating.Variable;
import org.yamcs.time.TimeService;
import org.yamcs.timeline.TimelineService;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.FilterParser;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.TokenMgrError;
import org.yamcs.utils.parser.ast.Comparison;

import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.protobuf.Empty;

public class InstancesApi extends AbstractInstancesApi<Context> {

    private static final Log log = new Log(InstancesApi.class);

    public static final Pattern ALLOWED_INSTANCE_NAMES = Pattern.compile("\\w[\\w\\.-]*");

    @Override
    public void listInstanceTemplates(Context ctx, Empty request,
            Observer<ListInstanceTemplatesResponse> observer) {
        var templatesb = ListInstanceTemplatesResponse.newBuilder();

        YamcsServer yamcs = YamcsServer.getServer();
        List<Template> templates = new ArrayList<>(yamcs.getInstanceTemplates());
        templates.sort((t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));

        for (Template template : templates) {
            templatesb.addTemplates(toInstanceTemplate(template));
        }
        observer.complete(templatesb.build());
    }

    @Override
    public void getInstanceTemplate(Context ctx, GetInstanceTemplateRequest request,
            Observer<InstanceTemplate> observer) {
        YamcsServer yamcs = YamcsServer.getServer();
        String name = request.getTemplate();
        if (!yamcs.hasInstanceTemplate(name)) {
            throw new NotFoundException("No template named '" + name + "'");
        }

        InstanceTemplate template = toInstanceTemplate(yamcs.getInstanceTemplate(name));
        observer.complete(template);
    }

    @Override
    public void listInstances(Context ctx, ListInstancesRequest request, Observer<ListInstancesResponse> observer) {
        var filter = getFilter(request.getFilterList());
        var instancesb = ListInstancesResponse.newBuilder();
        for (YamcsServerInstance instance : YamcsServer.getInstances()) {
            if (filter.test(instance)) {
                YamcsInstance enriched = enrichYamcsInstance(instance.getInstanceInfo());
                instancesb.addInstances(enriched);
            }
        }
        observer.complete(instancesb.build());
    }

    @Override
    public void subscribeInstances(Context ctx, Empty request, Observer<YamcsInstance> observer) {
        ManagementListener listener = new ManagementListener() {
            @Override
            public void instanceStateChanged(YamcsServerInstance ysi) {
                YamcsInstance enriched = enrichYamcsInstance(ysi.getInstanceInfo());
                observer.next(enriched);
            }
        };

        observer.setCancelHandler(() -> ManagementService.getInstance().removeManagementListener(listener));
        ManagementService.getInstance().addManagementListener(listener);
    }

    @Override
    public void getInstance(Context ctx, GetInstanceRequest request, Observer<YamcsInstance> observer) {
        String instanceName = verifyInstance(request.getInstance());
        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        YamcsInstance instanceInfo = instance.getInstanceInfo();
        YamcsInstance enriched = enrichYamcsInstance(instanceInfo);
        observer.complete(enriched);
    }

    @Override
    public void reconfigureInstance(Context ctx, ReconfigureInstanceRequest request, Observer<YamcsInstance> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.CreateInstances);
        YamcsServer yamcs = YamcsServer.getServer();

        String instanceName = verifyInstance(request.getInstance());
        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        String templateName = instance.getTemplate();
        if (templateName == null) {
            throw new BadRequestException("This instance is not templated");
        }

        var templateArgs = new HashMap<String, Object>(request.getTemplateArgsMap());
        var labels = new HashMap<>(request.getLabelsMap());
        CompletableFuture.supplyAsync(() -> {
            try {
                yamcs.reconfigureInstance(instanceName, templateArgs, labels);
                return yamcs.restartInstance(instanceName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).whenComplete((v, error) -> {
            YamcsServerInstance ysi = yamcs.getInstance(instanceName);
            if (error == null) {
                YamcsInstance enriched = enrichYamcsInstance(ysi.getInstanceInfo());
                observer.complete(enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(t);
            }
        });
    }

    @Override
    public void createInstance(Context ctx, CreateInstanceRequest request, Observer<YamcsInstance> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.CreateInstances);
        YamcsServer yamcs = YamcsServer.getServer();

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
        if (yamcs.getInstance(instanceName) != null) {
            throw new BadRequestException("An instance named '" + instanceName + "' already exists");
        }

        String template = request.getTemplate();
        var templateArgs = new HashMap<String, Object>(request.getTemplateArgsMap());
        var labels = request.getLabelsMap();
        // Not (yet) supported via HTTP. If we do, should probably use JSON
        Map<String, Object> customMetadata = Collections.emptyMap();
        InstanceMetadata metadata = new InstanceMetadata();
        request.getLabelsMap().forEach((k, v) -> metadata.putLabel(k, v));

        var cf = CompletableFuture.supplyAsync(() -> {
            try {
                yamcs.createInstance(instanceName, template, templateArgs, labels, customMetadata);
                return yamcs.startInstance(instanceName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        cf.whenComplete((v, error) -> {
            if (error == null) {
                YamcsInstance instanceInfo = v.getInstanceInfo();
                YamcsInstance enriched = enrichYamcsInstance(instanceInfo);
                observer.complete(enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                log.error("Error when creating instance {}", instanceName, t);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });
    }

    @Override
    public void startInstance(Context ctx, StartInstanceRequest request, Observer<YamcsInstance> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        String instance = verifyInstance(request.getInstance());

        CompletableFuture.supplyAsync(() -> {
            try {
                YamcsServer.getServer().startInstance(instance);
                return null;
            } catch (IOException e) {
                throw new UncheckedExecutionException(e);
            }
        }).whenComplete((v, error) -> {
            YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instance);
            if (error == null) {
                YamcsInstance enriched = enrichYamcsInstance(ysi.getInstanceInfo());
                observer.complete(enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(t);
            }
        });
    }

    @Override
    public void stopInstance(Context ctx, StopInstanceRequest request, Observer<YamcsInstance> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        String instance = verifyInstance(request.getInstance());
        YamcsServer yamcs = YamcsServer.getServer();
        if (yamcs.getInstance(instance) == null) {
            throw new BadRequestException("No instance named '" + instance + "'");
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                yamcs.stopInstance(instance);
                return null;
            } catch (IOException e) {
                throw new UncheckedExecutionException(e);
            }
        }).whenComplete((v, error) -> {
            YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instance);
            if (error == null) {
                YamcsInstance enriched = enrichYamcsInstance(ysi.getInstanceInfo());
                observer.complete(enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(t);
            }
        });
    }

    @Override
    public void restartInstance(Context ctx, RestartInstanceRequest request, Observer<YamcsInstance> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        String instance = verifyInstance(request.getInstance());
        YamcsServer yamcs = YamcsServer.getServer();

        CompletableFuture.supplyAsync(() -> {
            try {
                yamcs.restartInstance(instance);
                return null;
            } catch (IOException e) {
                throw new UncheckedExecutionException(e);
            }
        }).whenComplete((v, error) -> {
            YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instance);
            if (error == null) {
                YamcsInstance enriched = enrichYamcsInstance(ysi.getInstanceInfo());
                observer.complete(enriched);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(t);
            }
        });
    }

    private Predicate<YamcsServerInstance> getFilter(List<String> flist) throws HttpException {
        if (flist == null) {
            return ysi -> true;
        }

        var fp = new FilterParser<YamcsServerInstance>((StringReader) null);
        fp.addEnumField("state", InstanceState.class, ysi -> ysi.state());
        fp.addPrefixField("label.", (ysi, field) -> {
            var label = field.substring("label.".length());
            return ysi.getLabels().get(label);
        });

        Predicate<YamcsServerInstance> pred = ysi -> true;
        for (String filter : flist) {
            // Temporary backwards support for an (undocumented) API change.
            // Can be removed in a few months.
            if (filter.startsWith("label:")) {
                filter = filter.replace("label:", "label.");
            }
            fp.ReInit(new StringReader(filter));
            Comparison pr;
            try {
                pr = fp.comparison();
                pred = pred.and(getPredicate(pr));
            } catch (ParseException | TokenMgrError e) {
                throw new BadRequestException("Cannot parse the filter '" + filter + "': " + e.getMessage());
            }

        }
        return pred;
    }

    private Predicate<YamcsServerInstance> getPredicate(Comparison pr) throws HttpException {
        if ("state".equals(pr.comparable)) {
            try {
                InstanceState state = InstanceState.valueOf(pr.value.toUpperCase());
                switch (pr.comparator) {
                case EQUAL_TO:
                    return ysi -> ysi.state() == state;
                case NOT_EQUAL_TO:
                    return ysi -> ysi.state() != state;
                default:
                    throw new IllegalStateException("Unknown operator " + pr.comparator);
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Unknown state '" + pr.value + "'. Valid values are: " + Arrays.asList(InstanceState.values()));
            }
        } else if (pr.comparable.startsWith("label.")) {
            String labelKey = pr.comparable.substring(6);
            return ysi -> {
                var labels = ysi.getLabels();
                if (labels == null) {
                    return false;
                }
                var v = labels.get(labelKey);
                if (v == null) {
                    return false;
                }
                switch (pr.comparator) {
                case EQUAL_TO:
                    return pr.value.equalsIgnoreCase(v);
                case NOT_EQUAL_TO:
                    return !pr.value.equalsIgnoreCase(v);
                default:
                    throw new IllegalStateException("Unknown operator " + pr.comparator);
                }
            };
        } else {
            throw new BadRequestException("Unknown filter key '" + pr.comparable + "'");
        }
    }

    private static InstanceTemplate toInstanceTemplate(Template template) {
        InstanceTemplate.Builder templateb = InstanceTemplate.newBuilder()
                .setName(template.getName());

        if (template.getDescription() != null) {
            templateb.setDescription(template.getDescription());
        }

        for (Variable variable : template.getVariables()) {
            var varb = TemplateVariable.newBuilder()
                    .setName(variable.getName())
                    .setRequired(variable.isRequired())
                    .setType(variable.getClass().getName());
            if (variable.getLabel() != null) {
                varb.setLabel(variable.getLabel());
            }
            if (variable.getHelp() != null) {
                varb.setHelp(variable.getHelp());
            }
            if (variable.getInitial() != null) {
                varb.setInitial(variable.getInitial());
            }

            // getChoices() may be dynamically calculated. Best call it once only.
            List<String> choices = variable.getChoices();
            if (choices != null) {
                for (String choice : choices) {
                    varb.addChoices(choice);
                }
            }

            templateb.addVariables(varb);
        }

        return templateb.build();
    }

    public static String verifyInstance(String instance, boolean allowGlobal) {
        if (allowGlobal && YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            return instance;
        }
        if (!YamcsServer.hasInstance(instance)) {
            throw new NotFoundException("No instance named '" + instance + "'");
        }
        return instance;
    }

    public static String verifyInstance(String instance) {
        return verifyInstance(instance, false);
    }

    public static YamcsServerInstance verifyInstanceObj(String instance) {
        YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instance);
        if (ysi == null) {
            throw new NotFoundException("No instance named '" + instance + "'");
        }
        return ysi;
    }

    private static YamcsInstance enrichYamcsInstance(YamcsInstance yamcsInstance) {
        YamcsServer yamcs = YamcsServer.getServer();
        YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);
        YamcsServerInstance ysi = yamcs.getInstance(yamcsInstance.getName());

        if (ysi == null) {
            throw new BadRequestException("Invalid Yamcs instance " + yamcsInstance.getName());
        }

        if (yamcsInstance.hasMissionDatabase()) {
            Mdb mdb = yamcs.getInstance(yamcsInstance.getName()).getMdb();
            if (mdb != null) {
                instanceb.setMissionDatabase(MdbApi.toMissionDatabase(yamcsInstance.getName(), mdb));
            }
        }

        String template = ysi.getTemplate();
        if (template != null) {
            instanceb.setTemplate(template);
            for (var arg : ysi.getTemplateArgs().entrySet()) {
                if (arg.getValue() instanceof String) {
                    instanceb.putTemplateArgs(arg.getKey(), (String) arg.getValue());
                }
            }

            Template latestTemplate = yamcs.getInstanceTemplate(template);
            instanceb.setTemplateAvailable(latestTemplate != null);
            if (latestTemplate != null) {
                boolean eq = Objects.equals(ysi.getTemplateSource(), latestTemplate.getSource());
                instanceb.setTemplateChanged(!eq);
            }
        }

        List<Processor> processors = new ArrayList<>(ysi.getProcessors());
        Collections.sort(processors, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (Processor processor : processors) {
            instanceb.addProcessors(ProcessingApi.toProcessorInfo(processor));
        }

        TimeService timeService = ysi.getTimeService();
        if (timeService != null) {
            instanceb.setMissionTime(TimeEncoding.toProtobufTimestamp(timeService.getMissionTime()));
        }

        if (!ysi.getServicesWithConfig(CcsdsTmIndex.class).isEmpty()) {
            instanceb.addCapabilities("ccsds-completeness");
        }
        if (!ysi.getServicesWithConfig(FileTransferService.class).isEmpty()) {
            instanceb.addCapabilities("file-transfer");
        }
        if (!ysi.getServicesWithConfig(TimelineService.class).isEmpty()) {
            instanceb.addCapabilities("timeline");
            instanceb.addCapabilities("activities");
        }
        if (!ysi.getServicesWithConfig(ParameterListService.class).isEmpty()) {
            instanceb.addCapabilities("parameter-lists");
        }
        return instanceb.build();
    }
}
