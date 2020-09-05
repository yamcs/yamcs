package org.yamcs;

public class ProcessorServiceWithConfig {
    final ProcessorService service;

    final String serviceClass;
    final String name;
    final YConfiguration config;

    public ProcessorServiceWithConfig(ProcessorService service, String serviceClass, String name, YConfiguration config) {
        if (config == null) {
            throw new NullPointerException("Config cannot be null (use Yconfiguration.emptyConfig() if necessary");
        }
        this.service = service;
        this.serviceClass = serviceClass;
        this.name = name;
        this.config = config;

    }

    public ProcessorService getService() {
        return service;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getName() {
        return name;
    }

    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return "ServiceWithConfig [service=" + service + ", serviceClass=" + serviceClass + ", name=" + name
                + ", config=" + config + "]";
    }
}
