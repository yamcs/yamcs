package org.yamcs;

import java.io.IOException;
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
import org.yamcs.utils.YObjectLoader;


/**
 * Used to create replay or test channels
 * 
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
        try {
            if(ctc.serviceClass!=null) {
                tctms= new YObjectLoader<TcTmService>().loadObject(ctc.serviceClass, yamcsInstance, spec);
            } else {
                TmPacketProvider tm=null;
                ParameterProvider pp=null;
                TcUplinker tc=null;

                if(ctc.tmClass!=null) {
                    tm = new YObjectLoader<TmPacketProvider>().loadObject(ctc.tmClass, yamcsInstance, spec);
                }

                if(ctc.ppClass!=null) {
                    pp = new YObjectLoader<ParameterProvider>().loadObject(ctc.ppClass, yamcsInstance, spec);
                }

                if(ctc.tcClass!=null) {
                    tc = new YObjectLoader<TcUplinker>().loadObject(ctc.tcClass, yamcsInstance, spec);
                }
                tctms=new SimpleTcTmService(tm, pp, tc);
            }

            if(ctc.cmdHistClass!=null) {
                cmdHist = new YObjectLoader<CommandHistory>().loadObject(ctc.tcClass, yamcsInstance, spec);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Cannot load service",e);
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
