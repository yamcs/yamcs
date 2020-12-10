package org.yamcs;

/**
 * Holder for a service together with its name and config. Services are used at three levels:
 * <ul>
 * <li>Yamcs server global services
 * <li>Yamcs instance specific services
 * <li>Processor specific services
 * </ul>
 * 
 * @author nm
 */
public class ServiceWithConfig {
    final YamcsService service;
 
    final String serviceClass;
    final String name;
    final YConfiguration args;
    final boolean enableAtStartup;

    public ServiceWithConfig(YamcsService service, String serviceClass, String name, YConfiguration args, boolean enabledAtStartup) {
        this.service = service;
        this.serviceClass = serviceClass;
        this.name = name;
        this.args = args;
        this.enableAtStartup = enabledAtStartup;
    }

    public ServiceWithConfig(YamcsService service, String serviceClass, String name, YConfiguration args) {
        this(service, serviceClass, name, args, true);
    }

    public YamcsService getService() {
        return service;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getName() {
        return name;
    }

    public Object getArgs() {
        return args;
    }
    
    @Override
    public String toString() {
        return "ServiceWithConfig [service=" + service + ", serviceClass=" + serviceClass + ", name=" + name + ", args="
                + args + "]";
    }

}
