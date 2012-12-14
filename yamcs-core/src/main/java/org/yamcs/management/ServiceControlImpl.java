package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import com.google.common.util.concurrent.Service;

public class ServiceControlImpl  extends StandardMBean implements ServiceControl {
    Service service;
    ServiceControlImpl(Service service) throws NotCompliantMBeanException {
        super(ServiceControl.class);
        this.service=service;
    }
    
    @Override
    public String getDescription() {
        return service.toString();
    }
    
    @Override
    public String getState() {
        return service.state().toString();
    }
}
