package org.yamcs.xtceproc;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ItemIdPacketConsumerStruct;
import org.yamcs.ParameterValue;

import org.yamcs.utils.StringConvertors;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

/**
 * 
 * Extracts parameters out of packets based on the XTCE description
 *
 * 
 *  @author mache
 * 
 */

public class XtceTmExtractor {
	Logger log=LoggerFactory.getLogger(this.getClass().getName());
	protected final Subscription subscription;
	private ProcessingStatistics stats=new ProcessingStatistics();

	public final XtceDb xtcedb;
	final SequenceContainer rootContainer;
	ArrayList<ParameterValue> paramResult=new ArrayList<ParameterValue>();
	ArrayList<SequenceContainer> containerResult=new ArrayList<SequenceContainer>();

	/**
	 * Creates a TmExtractor extracting data according to the XtceDb
	 * @param xtcedb
	 */
	public XtceTmExtractor(XtceDb xtcedb) {
		log=LoggerFactory.getLogger(this.getClass().getName());
		this.xtcedb=xtcedb;
		this.subscription=new Subscription(xtcedb);
		rootContainer=xtcedb.getRootSequenceContainer();
	}

	/**
	 * Adds a parameter to the current subscription list: 
	 *  finds all the SequenceContainers in which this parameter may appear and adds them to the list.
	 *  also for each sequence container adds the parameter needed to instantiate the sequence container.
	 * @param param parameter to be added to the current subscription list 
	 */
    public void startProviding(Parameter param) { 
		synchronized(subscription) {
			subscription.addParameter(param);
		}
	}

	/**
	 * adds all parameters to the subscription
	 */
    public void startProvidingAll() {
	    subscription.addAll(rootContainer);
	}
	
    public void stopProviding(Parameter param) {
		//TODO 2.0 do something here
	}
	
	/**
	 * Extract one packets
	 *
	 */
    public void processPacket(ByteBuffer bb, long generationTime){
	    try {
	        //we only support packets that have ccsds secondary header
	        if(bb.capacity()<16) {
	            log.warn("packet smaller than 16 bytes has been received size="+(bb.capacity())+" content:"+StringConvertors.byteBufferToHexString(bb));
	            return;
	        }
	        paramResult=new ArrayList<ParameterValue>();
	        containerResult=new ArrayList<SequenceContainer>();
	        synchronized(subscription) {
	            long aquisitionTime=TimeEncoding.currentInstant(); //we do this in order that all the parameters inside this packet have the same acquisition time
	            ProcessingContext pcontext=new ProcessingContext(bb, 0, 0, subscription, paramResult, containerResult, aquisitionTime, generationTime, stats);
	            pcontext.sequenceContainerProcessor.extract(rootContainer);

	            for(ParameterValue pv:paramResult) {
	                pcontext.parameterTypeProcessor.performLimitChecking(pv.getParameter().getParameterType(), pv);
	            }
	        }
	    } catch (Exception e) {
	        log.error("got exception in tmextractor "+e);
	        e.printStackTrace();
	    }
	}
	
	public void resetStatistics() {
		stats.reset();
	}
		
	public ProcessingStatistics getStatistics(){
		return stats;
	}


    public void startProviding(SequenceContainer sequenceContainer) {
        synchronized(subscription) {
            subscription.addSequenceContainer(sequenceContainer);
        }
    }
    
	public void subscribePackets(List<ItemIdPacketConsumerStruct> iipcs) {
	    synchronized(subscription) {
	        for(ItemIdPacketConsumerStruct i:iipcs) {
	            subscription.addSequenceContainer(i.def);
	        }
	    }
	}

	public ArrayList<ParameterValue> getParameterResult() {
        return paramResult;
    }

    public ArrayList<SequenceContainer> getContainerResult() {
        return containerResult;
    }

    public Subscription getSubscription() {
        return subscription;
    }
    
    @Override
    public String toString() {
        return subscription.toString();
    }
}
