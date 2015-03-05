package org.yamcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.tctm.SimpleTcTmService;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.utils.YObjectLoader;

/**
 * Used to create channels as defined in channel.yaml
 * 
 * @author mache
 *
 */
public class ChannelFactory {
	static Logger log=LoggerFactory.getLogger(Channel.class.getName());

	/**
	 * Create a channel with the give name, type, creator and spec
	 * 
	 *  type is used to load the tm, parameter and command classes as defined in channel.yaml
	 *  spec if not null is passed as an extra argument to those classes - it is used for example when creating replay channels to pass on the data that has to be replayed.
	 *       should probably be changed from string to some sort of object.
	 * 
	 * @param yamcsInstance
	 * @param name
	 * @param type
	 * @param creator
	 * @param spec
	 * @return
	 * @throws ChannelException
	 * @throws ConfigurationException
	 */
	@SuppressWarnings("unchecked")
	static public Channel create(String yamcsInstance, String name, String type, String creator, String spec) throws ChannelException,  ConfigurationException {
		boolean initialized = false;
		TcTmService tctms=null;
		YConfiguration conf=YConfiguration.getConfiguration("channel");
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
					throw new ConfigurationException("No telemetryProvider specified for channel of type '"+type+"' in channel.yaml");
				}
				
				if(conf.containsKey(type,"parameterProviders")) {
					List<Map<String, Object>> l = (List<Map<String, Object>>) conf.getList(type, "parameterProviders");
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
				tctms=new SimpleTcTmService(tm, pps, tc);
				if(!initialized) {
					throw new ConfigurationException("For channel type '"+type+"', none of  telemetryProvider, parameterProviders or commandReleaser specified");
				}
			}
		} catch (IOException e) {
			throw new ConfigurationException("Cannot load service",e);
		}
		
		return create(yamcsInstance, name, type, tctms, creator);
	}
	/**
	 * loads objects but passes only non null parameters
	 */
	static private <T> T loadObject(String className, String yamcsInstance, Object args, String spec) throws ConfigurationException, IOException {
		List<Object> params = new ArrayList<Object>();
		params.add(yamcsInstance);

		if(args!=null) {
			params.add(args);
		}
		if(spec!=null) {
			params.add(spec);
		}
		return new YObjectLoader<T>().loadObject(className, params.toArray());
	}


	/**
	 *  Create a Channel by specifying the service.
	 *  The type is not used in this case, except for showing it in the yamcs monitor.
	 **/
	static public Channel create(String instance, String name, String type, TcTmService tctms, String creator) throws ChannelException, ConfigurationException {
		Channel channel=new Channel(instance, name, type, creator);

		channel.init(tctms);
		return channel;
	}
}
