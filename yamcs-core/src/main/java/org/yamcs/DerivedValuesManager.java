package org.yamcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.derivedvalues.DerivedValues_XTCE;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

import com.google.common.util.concurrent.AbstractService;

/**
 * takes care of derived values. It subscribes in a normal way to the other parameters
 * but it gets the updated values of subscribed parameters via the special method updateDerivedValues instead
 * of the normal UpdateItems 
 *
 * @author mache
 *
 */
public class DerivedValuesManager extends AbstractService implements ParameterProvider, ParameterConsumer {
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	
	//the id used for suscribing to the parameterManager
	int subscriptionId;
	List<DerivedValue> derivedValues=new ArrayList<DerivedValue>();
	NamedDescriptionIndex<Parameter> dvIndex=new NamedDescriptionIndex<Parameter>();
	
	ArrayList<DerivedValue> requestedValues=new ArrayList<DerivedValue>();
	ParameterRequestManager parameterRequestManager;
	
	public DerivedValuesManager(String yamcsInstance, ParameterRequestManager parameterRequestManager, XtceDb xtcedb) throws ConfigurationException {
		if(parameterRequestManager!=null) {
			//it is invoked with parameterRequestManager=null from the MDB Loader TODO: fix this mess
			this.parameterRequestManager=parameterRequestManager;
			try {
				subscriptionId=parameterRequestManager.addRequest(new ArrayList<NamedObjectId>(0), this);
			} catch (InvalidIdentification e) {
				log.error("InvalidIdentification while subscribing to the parameterRequestManager with an empty subscription list", e);
			}
		}
		addAll(new DerivedValues_XTCE(xtcedb).getDerivedValues());
		YConfiguration yconf=YConfiguration.getConfiguration("yamcs."+yamcsInstance);
		String mdbconfig=yconf.getString("mdb");
		YConfiguration conf=YConfiguration.getConfiguration("mdb");
		if(conf.containsKey(mdbconfig, "derivedValuesProviders")) {
		    List<String> providers=conf.getList(mdbconfig, "derivedValuesProviders");
		    for(String p:providers) {
		        Class<DerivedValuesProvider> c;
		        try {
		            c = (Class<DerivedValuesProvider>) Class.forName(p);
		            DerivedValuesProvider provider=c.newInstance();
		            addAll(provider.getDerivedValues());
		        } catch (ClassNotFoundException e) {
		            throw new ConfigurationException("Cannot load derived value provider from class "+p, e);
		        } catch (InstantiationException e) {
		            throw new ConfigurationException("Cannot load derived value provider from class "+p, e);
		        } catch (IllegalAccessException e) {
		            throw new ConfigurationException("Cannot load derived value provider from class "+p, e);
		        }
		    }
		} else {
		    log.info("No derived value provider defined in MDB.yaml");
		}
	}
	
	public void addAll(Collection<DerivedValue> dvalues) {
		derivedValues.addAll(dvalues);
		for(DerivedValue dv:dvalues) {
			dvIndex.add(dv.def);
		}
	}
	
	public int getSubscriptionId() {
		return subscriptionId;
	}

	@Override
    public void startProviding(Parameter paramDef) {
		for (DerivedValue dv:derivedValues){
			if(dv.def==paramDef) {
				requestedValues.add(dv);
				try {
					parameterRequestManager.addItemsToRequest(subscriptionId, Arrays.asList(dv.getArgumentIds()));
				} catch (InvalidIdentification e) {
					log.error("InvalidIdentification caught when subscribing to the items required for the derived value "+dv.def+"\n\t The invalid items are:"+e.invalidParameters, e);
				} catch (InvalidRequestIdentification e) {
					log.error("InvalidRequestIdentification caught when subscribing to the items required for the derived value "+dv.def, e);
				}
				return;
			}
		}
	}
	
	@Override
	public void startProvidingAll() {
	    // TODO Auto-generated method stub

	}
	//TODO 2.0 unsubscribe from the requested values
	@Override
    public void stopProviding(Parameter paramDef) {
		for (Iterator<DerivedValue> it=requestedValues.iterator();it.hasNext(); ){
			DerivedValue dv=it.next();
			if(dv.def==paramDef) {
				it.remove();
				return;
			}
		}
	}

	@Override
    public boolean canProvide(NamedObjectId itemId) {
		try {
			getParameter(itemId) ;
		} catch (InvalidIdentification e) {
			return false;
		}
		return true;
	}

	
	@Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
		Parameter p;
		if(paraId.hasNamespace()) {
			p=dvIndex.get(paraId.getNamespace(), paraId.getName());
		} else {
			p=dvIndex.get(paraId.getName());
		}
		if(p!=null) {
			return p;
		} else {
			throw new InvalidIdentification();
		}
	}

	public ArrayList<ParameterValue> updateDerivedValues(ArrayList<ParameterValueWithId> items) {
		HashSet<DerivedValue> needUpdate=new HashSet<DerivedValue>();
		for(Iterator<ParameterValueWithId> it=items.iterator();it.hasNext();) {
			ParameterValueWithId pvwi=it.next();
			for(Iterator<DerivedValue> it1=requestedValues.iterator();it1.hasNext();) {
				DerivedValue dv=it1.next();
				for(int i=0;i<dv.getArgumentIds().length;i++) {
					if(dv.getArgumentIds()[i]==pvwi.getId()) {
						dv.args[i]=pvwi.getParameterValue();
						needUpdate.add(dv);
					}
				}
			}
		}
		long acqTime=TimeEncoding.currentInstant();
		
		ArrayList<ParameterValue> r=new ArrayList<ParameterValue>();
		for(DerivedValue dv:needUpdate) {
			try{
				dv.setAcquisitionTime(acqTime);
				dv.updateValue();
				if(dv.isUpdated()) {
					r.add(dv);
					dv.setGenerationTime(items.get(0).getParameterValue().getGenerationTime());
				}
			} catch (Exception e) {
				log.warn("got exception when updating derived value "+dv.def+": "+Arrays.toString(e.getStackTrace()));
			}
		}
		return r;
	}

	@Override
    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
		//do nothing. everything is done in the updateDerivedValues method
	}

    @Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
        // do nothing,  everything is done in the updateDerivedValues method
    }

    @Override
    protected void doStart() {
       notifyStarted();
    }

    @Override
    protected void doStop() {
    	notifyStopped();
    }

    @Override
    public String getDetailedStatus() {
        return "processing "+requestedValues.size()+" out of "+derivedValues.size()+" parameters";
    }
}
