package org.yamcs.container;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.InvalidIdentification;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;


/**
 * This sits in front of the ContainerRequestManager and implements subscriptions based on NamedObjectId 
 * taking care to send to the consumers the containers with the requested id.
 *
 * 
 * TODO: check privileges and subscription limits
 * 
 * @author nm
 *
 */
public class ContainerWithIdRequestHelper implements ContainerConsumer {
    ContainerRequestManager crm;
    final ContainerWithIdConsumer listener;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    List<ContainerWithId> subscription = new  ArrayList<ContainerWithId>();
    
    

    public ContainerWithIdRequestHelper(ContainerRequestManager crm, ContainerWithIdConsumer listener) {
	this.crm = crm;
	this.listener = listener;
    }

    public void subscribe(NamedObjectId id) throws InvalidIdentification {
        XtceDb xtcedb = crm.getXtceDb();
        SequenceContainer sc = xtcedb.getSequenceContainer(id);
        if(sc==null) {
            throw new InvalidIdentification(id);
        }
        ContainerWithId cwi = new ContainerWithId(sc,  id);
        subscription.add(cwi);
        crm.subscribe(this, sc);
    }

    public synchronized void subscribeAll(ContainerWithIdConsumer subscriber) {
        crm.subscribeAll(this);
    }
        
    @Override
    public void processContainer(ContainerExtractionResult cer) {
        SequenceContainer container = cer.getContainer();
        ByteBuffer content = cer.getContainerContent();
        boolean found = false;
        for(ContainerWithId cwi: subscription) {
            if(cwi.def==container) {
                listener.processContainer(cwi, cer);
                found = true;
            }
        }
        if(!found) { //comes from subscribeAll
            listener.processContainer(new ContainerWithId(container, null), cer);
        }
    }
}

