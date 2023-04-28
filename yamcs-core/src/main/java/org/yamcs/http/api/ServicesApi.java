package org.yamcs.http.api;

import org.yamcs.ProcessorServiceWithConfig;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractServicesApi;
import org.yamcs.protobuf.GetServiceRequest;
import org.yamcs.protobuf.ListServicesRequest;
import org.yamcs.protobuf.ListServicesResponse;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;
import org.yamcs.protobuf.StartServiceRequest;
import org.yamcs.protobuf.StopServiceRequest;
import org.yamcs.security.SystemPrivilege;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;
import com.google.protobuf.Empty;

public class ServicesApi extends AbstractServicesApi<Context> {

    @Override
    public void listServices(Context ctx, ListServicesRequest request, Observer<ListServicesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlServices);
        YamcsServer yamcs = YamcsServer.getServer();

        String instance = request.getInstance();
        boolean global = false;
        if (YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            InstancesApi.verifyInstance(instance);
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
            InstancesApi.verifyInstance(instance);
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
            InstancesApi.verifyInstance(instance);
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
            InstancesApi.verifyInstance(instance);
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

    public static ServiceInfo toServiceInfo(ServiceWithConfig serviceWithConfig, String instance, String processor) {
        var service = serviceWithConfig.getService();
        var serviceb = ServiceInfo.newBuilder()
                .setName(serviceWithConfig.getName())
                .setClassName(serviceWithConfig.getServiceClass())
                .setState(ServiceState.valueOf(service.state().name()));
        if (instance != null) {
            serviceb.setInstance(instance);
        }
        if (processor != null) {
            serviceb.setProcessor(processor);
        }
        if (service.state() == State.FAILED) {
            var cause = service.failureCause();
            var failureMessage = cause.getMessage();
            if (failureMessage == null) {
                failureMessage = cause.getClass().getName();
            }
            serviceb.setFailureMessage(failureMessage);
            serviceb.setFailureCause(toString(cause));
        }
        return serviceb.build();
    }

    private static String toString(Throwable t) {
        var sb = new StringBuffer();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement ste : t.getStackTrace()) {
            sb.append("\t").append(ste.toString()).append("\n");
        }
        Throwable cause = t.getCause();
        while (cause != null && cause != t) {
            sb.append("Caused by: ").append(cause.toString()).append("\n");
            for (StackTraceElement ste : cause.getStackTrace()) {
                sb.append("\t").append(ste.toString()).append("\n");
            }
            cause = cause.getCause();
        }
        return sb.toString();
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
}
