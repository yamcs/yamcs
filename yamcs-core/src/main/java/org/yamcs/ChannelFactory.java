package org.yamcs;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TcUplinker;
import org.yamcs.tctm.TmPacketProvider;


/**
 * Currently this is only used when creating replay channels.
 * It should be removed; the replay channel can be created directly without this generic stuff TODO.
 * @author mache
 *
 */
public class ChannelFactory {
    static Logger log=LoggerFactory.getLogger(Channel.class.getName());
    static Map<String, ChannelTypeConfig> types;
    
    
    static public Channel create(String yamcsInstance, String name, String type, String spec, String creator) throws ChannelException,  ConfigurationException {
        Channel channel=new Channel(yamcsInstance, name, type, spec, creator);

        initTypes();
        String lctype=type.toLowerCase();
        if(!types.containsKey(lctype)) throw new ChannelException("unknown channel type '"+type+"'. Available types are "+types.keySet());
        TcTmService tctms=null;
        CommandHistory cmdHist=null;
        
        ChannelTypeConfig ctc=types.get(lctype);
        if(ctc.serviceClass!=null) {
            tctms=(TcTmService) loadServiceFromClass(ctc.serviceClass, yamcsInstance, spec);
        } else {
            TmPacketProvider tm=null;
            ParameterProvider pp=null;
            TcUplinker tc=null;
            
            if(ctc.tmClass!=null) {
                tm=(TmPacketProvider) loadServiceFromClass(ctc.tmClass, yamcsInstance, spec);
            }

            if(ctc.ppClass!=null) {
                pp=(ParameterProvider)loadServiceFromClass(ctc.ppClass, yamcsInstance, spec);
            }

            if(ctc.tcClass!=null) {
                tc=(TcUplinker)loadServiceFromClass(ctc.tcClass, yamcsInstance, spec);
            }
            tctms=new SimpleTcTmService(tm, pp, tc);
        }

        if(ctc.cmdHistClass!=null) {
            cmdHist=(CommandHistory)loadServiceFromClass(ctc.tcClass, yamcsInstance, spec);
        }
    
        channel.init(tctms, cmdHist);
        return channel;
    }
    
    /**
     *  Create a Channel by specifying the service.
     *  The type is not used in this case, except for showing it in the yamcs monitor.
     **/
    static public Channel create(String instance, String name, String type, String spec, TcTmService tctms, String creator, CommandHistory cmdHist) throws ChannelException, ConfigurationException {
        Channel channel=new Channel(instance, name, type, spec, creator);
        
        channel.init(tctms, cmdHist);
        return channel;
    }

    private static Object loadServiceFromClass(String className, String yamcsInstance, String spec) throws ConfigurationException {
        try {
            Class cl=Class.forName(className);
            Constructor ctr=cl.getConstructor(String.class, String.class);
            return ctr.newInstance(yamcsInstance, spec);
        } catch (InvocationTargetException e) {
            Throwable t=e.getCause();
            if(t instanceof ConfigurationException) {
                throw (ConfigurationException) t;
            } else {
                throw new ConfigurationException("Cannot instantiate service from class " +className, t);
            }
        } catch (Exception e) {
            throw new ConfigurationException("Cannot instantiate service from class "+className, e);
        }
    }


    
    static private synchronized void initTypes() throws ConfigurationException {
        if(types!=null) return;
        YConfiguration conf=YConfiguration.getConfiguration("channel");
        types=new HashMap<String, ChannelTypeConfig>();
        List<String> typesarray=conf.getList("types");
        for(String s:typesarray) {
            ChannelTypeConfig ctc=new ChannelTypeConfig();
            boolean initialized=false;
        	if(conf.containsKey(s,"tmtcpp")) {
        		ctc.serviceClass=conf.getString(s, "tmtcpp");	
                initialized=true;
            } else {
            	if(conf.containsKey(s,"tm")) {
            		ctc.tmClass=conf.getString(s, "tm");
                    initialized=true;
                }
            	if(conf.containsKey(s,"pp")) {
                    ctc.ppClass=conf.getString(s, "pp");
                    initialized=true;
                }
            	if(conf.containsKey(s,"tc")) {
                    ctc.tcClass=conf.getString(s, "tc");
                }
            }
            if(!initialized) throw new ConfigurationException("TM/PP/TC services not defined for channel type "+s);
            types.put(s.toLowerCase(), ctc);
        }
    }
    
    static class ChannelTypeConfig {
        //this is an entry from channel.properties
        // either serviceClass or tm/pp/tc are set
        String serviceClass;
        String tmClass;
        String ppClass;
        String tcClass;
        String cmdHistClass;
    }

    
}
