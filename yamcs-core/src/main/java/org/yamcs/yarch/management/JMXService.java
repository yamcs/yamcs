package org.yamcs.yarch.management;

import java.lang.management.ManagementFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;


public class JMXService {
    final MBeanServer mbeanServer;
    static Logger log = LoggerFactory.getLogger(JMXService.class.getName());
    final String tld="yarch";
    static private JMXService jmxService;
    
    public static void setup() throws ConfigurationException {
        try {
            jmxService = new JMXService();
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }
    
    public static JMXService getInstance() {
        return jmxService;
    }
    
    private JMXService() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, MalformedObjectNameException, NullPointerException {
        mbeanServer = ManagementFactory.getPlatformMBeanServer();
    }
    
    public void registerStream(String instance, Stream stream) {
        StreamControlImpl sci;
        try {
            sci = new StreamControlImpl(stream);
            mbeanServer.registerMBean(sci, ObjectName.getInstance(tld+"."+instance+":type=streams,name="+stream.getName()));
        } catch (Exception e) {
            log.warn("Got exception when registering a stream: ", e);
        }
    }
    
    public void unregisterStream(String instance, String streamName) {
        try {
            mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+instance+":type=streams,name="+streamName));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a stream: ", e);
        }
    }
 
    public void registerTable(String instance, TableDefinition table) {
        
        try {
            TableControlImpl tci = new TableControlImpl(table);
            mbeanServer.registerMBean(tci, ObjectName.getInstance(tld+"."+instance+":type=tables,name="+table.getName()));
        } catch (Exception e) {
            log.warn("Got exception when registering a stream: ", e);
        }
    }
    
    public void unregisterTable(String instance, String tableName) {
        try {
            mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+instance+":type=tables,name="+tableName));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a stream: ", e);
        }
    }
}
