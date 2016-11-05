package org.yamcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.tctm.TcTmService;
import org.yamcs.utils.YObjectLoader;

/**
 * Used to create processors as defined in yprocessor.yaml
 *
 * @author mache
 *
 */
public class ProcessorFactory {
    static Logger log=LoggerFactory.getLogger(YProcessor.class.getName());

    /**
     * Create a channel with the give name, type, creator and spec
     *
     *  type is used to load the tm, parameter and command classes as defined in yprocessor.yaml
     *  spec if not null is passed as an extra argument to those classes - it is used for example when creating replay channels to pass on the data that has to be replayed.
     *       should probably be changed from string to some sort of object.
     *
     * @param yamcsInstance
     * @param name
     * @param type
     * @param creator
     * @param spec
     * @return
     * @throws ProcessorException
     * @throws ConfigurationException
     */
    static public YProcessor create(String yamcsInstance, String name, String type, String creator, Object spec) throws ProcessorException,  ConfigurationException {
        boolean initialized = false;
        TcTmService tctms=null;
        Map<String,Object> processorConfig = null;

        YConfiguration conf=YConfiguration.getConfiguration("yprocessor");
        try {
            if(conf.containsKey(type,"tmtcpp")) {
                Map<String, Object> m = (Map<String, Object>) conf.getMap(type, "tmtcpp");
                String clsName = YConfiguration.getString(m, "class");
                Object args = m.get("args");
                tctms= loadObject(clsName, yamcsInstance, args, spec);
            } else {
                TmPacketProvider tm=null;
                List<ParameterProvider> pps = new ArrayList<ParameterProvider>();
                CommandReleaser tc=null;

                if(conf.containsKey(type,"telemetryProvider")) {
                    Map<String, Object> m = (Map<String, Object>) conf.getMap(type, "telemetryProvider");
                    String tmClass = YConfiguration.getString(m, "class");
                    Object tmArgs = m.get("args");
                    tm = loadObject(tmClass, yamcsInstance, tmArgs, spec);
                    initialized = true;
                } else {//TODO: it should work without telemetryProvider (currently causes a NPE in Channel.java)
                    throw new ConfigurationException("No telemetryProvider specified for channel of type '"+type+"' in yprocessor.yaml");
                }

                if(conf.containsKey(type,"parameterProviders")) {
                    List<Map<String, Object>> l = conf.getList(type, "parameterProviders");
                    for(Map<String, Object> m:l) {
                        String paramClass = YConfiguration.getString(m, "class");
                        Object paramArgs = m.get("args");
                        ParameterProvider pp = loadObject(paramClass, yamcsInstance, paramArgs, spec);
                        pps.add(pp);
                    }
                    initialized = true;
                }
                if(conf.containsKey(type, "commandReleaser")) {
                    Map<String, Object> m = (Map<String, Object>) conf.getMap(type, "commandReleaser");
                    String commandClass = YConfiguration.getString(m, "class");
                    Object commandArgs = m.get("args");
                    tc =  loadObject(commandClass, yamcsInstance, commandArgs, spec);
                    initialized = true;
                }
                if(conf.containsKey(type, "config")) {
                    processorConfig = (Map<String, Object>) conf.getMap(type, "config");
                }
                tctms=new SimpleTcTmService(tm, pps, tc);
                if(!initialized) {
                    throw new ConfigurationException("For channel type '"+type+"', none of  telemetryProvider, parameterProviders or commandReleaser specified");
                }
            }
        } catch (IOException e) {
            throw new ConfigurationException("Cannot load service",e);
        }

        return create(yamcsInstance, name, type, tctms, creator, processorConfig);
    }
    /**
     * loads objects but passes only non null parameters
     */
    static private <T> T loadObject(String className, String yamcsInstance, Object args, Object spec) throws ConfigurationException, IOException {
        List<Object> newargs = new ArrayList<Object>();
        newargs.add(yamcsInstance);

        if(args!=null) {
            newargs.add(args);
        }
        if(spec!=null) {
            newargs.add(spec);
        }
        return new YObjectLoader<T>().loadObject(className, newargs.toArray());
    }

    static public YProcessor create(String instance, String name, String type, TcTmService tctms, String creator) throws ProcessorException, ConfigurationException {
        return create(instance, name, type, tctms, creator, null);
    }
    /**
     *  Create a Processor by specifying the service.
     *  The type is not used in this case, except for showing it in the yamcs monitor.
     **/
    static public YProcessor create(String instance, String name, String type, TcTmService tctms, String creator, Map<String, Object> config) throws ProcessorException, ConfigurationException {
        YProcessor yproc = new YProcessor(instance, name, type, creator);

        yproc.init(tctms, config);
        return yproc;
    }
}
