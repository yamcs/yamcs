package org.yamcs.http.api;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.yamcs.InstanceMetadata;
import org.yamcs.Processor;
import org.yamcs.ProcessorServiceWithConfig;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.YamcsVersion;
import org.yamcs.api.Observer;
import org.yamcs.archive.CcsdsTmIndex;
import org.yamcs.filetransfer.FileTransferService;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.AbstractManagementApi;
import org.yamcs.protobuf.CreateInstanceRequest;
import org.yamcs.protobuf.GetInstanceRequest;
import org.yamcs.protobuf.GetInstanceTemplateRequest;
import org.yamcs.protobuf.GetServiceRequest;
import org.yamcs.protobuf.InstanceTemplate;
import org.yamcs.protobuf.ListInstanceTemplatesResponse;
import org.yamcs.protobuf.ListInstancesRequest;
import org.yamcs.protobuf.ListInstancesResponse;
import org.yamcs.protobuf.ListServicesRequest;
import org.yamcs.protobuf.ListServicesResponse;
import org.yamcs.protobuf.ReconfigureInstanceRequest;
import org.yamcs.protobuf.RestartInstanceRequest;
import org.yamcs.protobuf.RootDirectory;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.StartInstanceRequest;
import org.yamcs.protobuf.StartServiceRequest;
import org.yamcs.protobuf.StopInstanceRequest;
import org.yamcs.protobuf.StopServiceRequest;
import org.yamcs.protobuf.SystemInfo;
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
import org.yamcs.utils.parser.FilterParser.Result;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.TokenMgrError;
import org.yamcs.xtce.XtceDb;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.protobuf.Empty;

public class ManagementApi extends AbstractManagementApi<Context> {

    private static final Log log = new Log(ManagementApi.class);

    public static final Pattern ALLOWED_INSTANCE_NAMES = Pattern.compile("\\w[\\w\\.-]*");

    @Override
    public void getSystemInfo(Context ctx, Empty request, Observer<SystemInfo> observer) {
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Access is limited to superusers");
        }

        YamcsServer yamcs = YamcsServer.getServer();

        SystemInfo.Builder b = SystemInfo.newBuilder()
                .setYamcsVersion(YamcsVersion.VERSION)
                .setRevision(YamcsVersion.REVISION)
                .setServerId(yamcs.getServerId());

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        b.setUptime(runtime.getUptime());
        b.setJvm(runtime.getVmName() + " " + runtime.getVmVersion() + " (" + runtime.getVmVendor() + ")");
        b.setWorkingDirectory(new File("").getAbsolutePath());
        b.setConfigDirectory(yamcs.getConfigDirectory().toAbsolutePath().toString());
        b.setDataDirectory(yamcs.getDataDirectory().toAbsolutePath().toString());
        b.setCacheDirectory(yamcs.getCacheDirectory().toAbsolutePath().toString());
        b.setJvmThreadCount(Thread.activeCount());

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        b.setHeapMemory(heap.getCommitted());
        b.setUsedHeapMemory(heap.getUsed());
        if (heap.getMax() != -1) {
            b.setMaxHeapMemory(heap.getMax());
        }
        MemoryUsage nonheap = memory.getNonHeapMemoryUsage();
        b.setNonHeapMemory(nonheap.getCommitted());
        b.setUsedNonHeapMemory(nonheap.getUsed());
        if (nonheap.getMax() != -1) {
            b.setMaxNonHeapMemory(nonheap.getMax());
        }

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        b.setOs(os.getName() + " " + os.getVersion());
        b.setArch(os.getArch());
        b.setAvailableProcessors(os.getAvailableProcessors());
        b.setLoadAverage(os.getSystemLoadAverage());

        try {
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                FileStore store = Files.getFileStore(root);
                b.addRootDirectories(RootDirectory.newBuilder()
                        .setDirectory(root.toString())
                        .setType(store.type())
                        .setTotalSpace(store.getTotalSpace())
                        .setUnallocatedSpace(store.getUnallocatedSpace())
                        .setUsableSpace(store.getUsableSpace()));
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        observer.complete(b.build());
    }

    @Override
    public void listInstanceTemplates(Context ctx, Empty request,
            Observer<ListInstanceTemplatesResponse> observer) {
        ListInstanceTemplatesResponse.Builder templatesb = ListInstanceTemplatesResponse.newBuilder();

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
        Predicate<YamcsServerInstance> filter = getFilter(request.getFilterList());
        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
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

        Map<String, Object> templateArgs = new HashMap<>(request.getTemplateArgsMap());
        Map<String, String> labels = new HashMap<>(request.getLabelsMap());
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
        Map<String, Object> templateArgs = new HashMap<>(request.getTemplateArgsMap());
        Map<String, String> labels = request.getLabelsMap();
        // Not (yet) supported via HTTP. If we do, should probably use JSON
        Map<String, Object> customMetadata = Collections.emptyMap();
        InstanceMetadata metadata = new InstanceMetadata();
        request.getLabelsMap().forEach((k, v) -> metadata.putLabel(k, v));

        CompletableFuture<YamcsServerInstance> cf = CompletableFuture.supplyAsync(() -> {
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

    @Override
    public void listServices(Context ctx, ListServicesRequest request, Observer<ListServicesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        YamcsServer yamcs = YamcsServer.getServer();

        String instance = request.getInstance();
        boolean global = false;
        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            verifyInstance(instance);
        }

        ListServicesResponse.Builder responseb = ListServicesResponse.newBuilder();

        if (global) {
            for (ServiceWithConfig serviceWithConfig : yamcs.getGlobalServices()) {
                responseb.addServices(toServiceInfo(serviceWithConfig, null, null));
            }
        } else {
            YamcsServerInstance ysi = yamcs.getInstance(instance);
            for (ServiceWithConfig serviceWithConfig : ysi.getServices()) {
                responseb.addServices(toServiceInfo(serviceWithConfig, instance, null));
            }
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getService(Context ctx, GetServiceRequest request, Observer<ServiceInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        YamcsServer yamcs = YamcsServer.getServer();

        String instance = request.getInstance();
        boolean global = false;
        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            verifyInstance(instance);
        }
        String serviceName = request.getName();
        if (global) {
            ServiceWithConfig serviceWithConfig = yamcs.getGlobalServiceWithConfig(serviceName);
            if (serviceWithConfig == null) {
                throw new NotFoundException();
            }

            ServiceInfo serviceInfo = toServiceInfo(serviceWithConfig, null, null);
            observer.complete(serviceInfo);
        } else {
            YamcsServerInstance ysi = yamcs.getInstance(instance);
            ServiceWithConfig serviceWithConfig = ysi.getServiceWithConfig(serviceName);
            if (serviceWithConfig == null) {
                throw new NotFoundException();
            }

            ServiceInfo serviceInfo = toServiceInfo(serviceWithConfig, instance, null);
            observer.complete(serviceInfo);
        }
    }

    @Override
    public void startService(Context ctx, StartServiceRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        YamcsServer yamcs = YamcsServer.getServer();

        String instance = request.getInstance();
        String serviceName = request.getName();

        boolean global = false;
        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            verifyInstance(instance);
        }

        try {
            if (global) {
                ServiceWithConfig service = yamcs.getGlobalServiceWithConfig(serviceName);
                yamcs.startGlobalService(service.getName());
            } else {
                ServiceWithConfig service = yamcs.getInstance(instance)
                        .getServiceWithConfig(serviceName);
                yamcs.getInstance(instance).startService(service.getName());
            }
            observer.complete(Empty.getDefaultInstance());
        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void stopService(Context ctx, StopServiceRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        YamcsServer yamcs = YamcsServer.getServer();

        String instance = request.getInstance();
        String serviceName = request.getName();

        boolean global = false;
        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            verifyInstance(instance);
        }

        try {
            Service s;
            if (global) {
                s = yamcs.getGlobalService(serviceName);
            } else {
                s = yamcs.getInstance(instance).getService(serviceName);
            }
            if (s == null) {
                throw new NotFoundException("No service by name '" + serviceName + "'");
            }

            s.stopAsync();
            observer.complete(Empty.getDefaultInstance());
        } catch (Exception e) {
            observer.completeExceptionally(e);
        }
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

    public static ServiceInfo toServiceInfo(ServiceWithConfig serviceWithConfig, String instance, String processor) {
        ServiceInfo.Builder serviceb = ServiceInfo.newBuilder()
                .setName(serviceWithConfig.getName())
                .setClassName(serviceWithConfig.getServiceClass())
                .setState(ServiceState.valueOf(serviceWithConfig.getService().state().name()));
        if (instance != null) {
            serviceb.setInstance(instance);
        }
        if (processor != null) {
            serviceb.setProcessor(processor);
        }
        return serviceb.build();
    }

    public static ServiceInfo toServiceInfo(ProcessorServiceWithConfig serviceWithConfig, String instance,
            String processor) {
        ServiceInfo.Builder serviceb = ServiceInfo.newBuilder()
                .setName(serviceWithConfig.getName())
                .setClassName(serviceWithConfig.getServiceClass())
                .setState(ServiceState.valueOf(serviceWithConfig.getService().state().name()));
        if (instance != null) {
            serviceb.setInstance(instance);
        }
        if (processor != null) {
            serviceb.setProcessor(processor);
        }
        return serviceb.build();
    }

    private static InstanceTemplate toInstanceTemplate(Template template) {
        InstanceTemplate.Builder templateb = InstanceTemplate.newBuilder()
                .setName(template.getName());

        if (template.getDescription() != null) {
            templateb.setDescription(template.getDescription());
        }

        for (Variable variable : template.getVariables()) {
            TemplateVariable.Builder varb = TemplateVariable.newBuilder()
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
            XtceDb mdb = yamcs.getInstance(yamcsInstance.getName()).getXtceDb();
            if (mdb != null) {
                instanceb.setMissionDatabase(MdbApi.toMissionDatabase(yamcsInstance.getName(), mdb));
            }
        }

        String template = ysi.getTemplate();
        if (template != null) {
            instanceb.setTemplate(template);
            for (Entry<String, Object> arg : ysi.getTemplateArgs().entrySet()) {
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
        }
        return instanceb.build();
    }
}
