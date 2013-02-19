package org.yamcs.archive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ppdb.PpDbFactory;
import org.yamcs.ppdb.PpDefDb;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.xtceproc.XtceDbFactory;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;


import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.yamcs.HornetQAuthPrivilege;
import org.yamcs.Privilege;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;

import static org.yamcs.api.Protocol.*;
import static org.yamcs.protobuf.Yamcs.*;

/**
 *Yarch replay server based on hornetq
 * @author nm
 *
 */
public class ReplayServer extends AbstractExecutionThreadService {
    static Logger log=LoggerFactory.getLogger(ReplayServer.class.getName());

    final int MAX_REPLAYS=200;
    final String instance;

    final YamcsClient msgClient;

    AtomicInteger replayCount=new AtomicInteger();
    
    public ReplayServer(String instance) throws HornetQException, YamcsApiException {
        this.instance=instance;
        YamcsSession ys=YamcsSession.newBuilder().build();
        msgClient=ys.newClientBuilder().setRpcAddress(Protocol.getYarchReplayControlAddress(instance)).build();
    }

    @Override
    protected void startUp() {
        Thread.currentThread().setName(this.getClass().getSimpleName()+"["+instance+"]");
    }
    
    @Override
    public void run() {
        try {
            while(isRunning()) {
                ClientMessage msg=msgClient.rpcConsumer.receive();
                if(msg==null) {
                    if(isRunning()) log.warn("Null message received from the control queue");
                    continue;
                }
                SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
                SimpleString dataAddress=msg.getSimpleStringProperty(DATA_TO_HEADER_NAME);
                if(replyto==null) {
                    if(isRunning()) log.warn("Did not receive a replyto header. Ignoring the request");
                    continue;
                }
                try {
                    String request=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
                    log.debug("received a new request: "+request);
                    if("createReplay".equalsIgnoreCase(request)) {
                        createReplay(msg, replyto, dataAddress);
                    } else  {
                        throw new YamcsException("Unknown request '"+request+"'");
                    }
                } catch (YamcsException e) {
                    msgClient.sendErrorReply(replyto, e);
                }
            }
        } catch (Exception e) {
            log.error("Got exception while processing the requests ", e);
            e.printStackTrace();
        }
    }


    /**
     * create a new packet replay object
     * @param archive instance
     */
    public void createReplay(ClientMessage msg, SimpleString replyto, SimpleString dataAddress) throws Exception {
        if(replayCount.get()>=MAX_REPLAYS) {
            throw new YamcsException("maximum number of replays reached");
        }
        ReplayRequest replayRequest=(ReplayRequest)decode(msg, ReplayRequest.newBuilder());
        
        if( Privilege.usePrivileges ) {
        	Privilege priv = HornetQAuthPrivilege.getInstance( msg );
            List<NamedObjectId> invalidParameters = new ArrayList<NamedObjectId>();
            for( NamedObjectId noi : replayRequest.getParameterRequest().getParameterList() ) {
            	if( ! priv.hasPrivilege( Privilege.Type.TM_PARAMETER, noi.getName() ) ) {
            		invalidParameters.add( noi );
            	}
            }
            
            if( ! invalidParameters.isEmpty() ) {
            	NamedObjectList nol=NamedObjectList.newBuilder().addAllList( invalidParameters ).build();
            	log.warn( "Cannot create replay - No privilege for parameters: {}", invalidParameters );
                throw new YamcsException("InvalidIdentification", "No privilege", nol);
            }
            
            // Now check for packets.
            // If no packets specified, assumed to be all packets they have privilege for
            if( replayRequest.getTmPacketFilterCount() != 0 ) {
                List<NamedObjectId> invalidPackets = new ArrayList<NamedObjectId>();
                Collection<String> allowedPackets = priv.getTmPacketNames( instance, "MDB:OPS Name" );
                for( NamedObjectId noi : replayRequest.getTmPacketFilterList() ) {
                	if( ! allowedPackets.contains( noi.getName() ) ) {
                		invalidPackets.add( noi );
                	}
                }
                if( ! invalidPackets.isEmpty() ) {
                	NamedObjectList nol=NamedObjectList.newBuilder().addAllList( invalidPackets ).build();
                	log.warn( "Cannot create replay - InvalidIdentification for packets: {}", invalidPackets );
                    throw new YamcsException("InvalidIdentification", "Invalid identification", nol);
                }
            }
        }
        
        try {
            YConfiguration c=YConfiguration.getConfiguration("yamcs."+instance);
            String mdbsection=c.getString("mdb");
            YConfiguration mdbc = YConfiguration.getConfiguration("mdb");
            PpDefDb ppdb=null;
            if(mdbc.containsKey(mdbsection, "ppLoaders")) {
                ppdb=PpDbFactory.getInstance(instance);
            }
            YarchReplay yr=new YarchReplay(this, replayRequest, dataAddress, ppdb, XtceDbFactory.getInstance(instance));
            replayCount.incrementAndGet();
            (new Thread(yr)).start();
            StringMessage addr=StringMessage.newBuilder().setMessage(yr.yclient.rpcAddress.toString()).build();
            msgClient.sendReply(replyto,"PACKET_REPLAY_CREATED", addr);
        } catch (final Exception e) {
            log.warn("Got exception when creating a PacketReplay object: ", e);
            e.printStackTrace();
            throw e;
        }
    }

    public void replayFinished() {
        replayCount.decrementAndGet();
    }
    
    @Override
    public void triggerShutdown() {
        try {
            msgClient.close();
        } catch (HornetQException e) {
            log.warn("Got exception when quitting", e);
        }
    }
}
