package org.yamcs.parameterarchive;


import java.util.Arrays;
import java.util.NavigableMap;

import org.rocksdb.RocksDBException;
import org.yamcs.YamcsServer;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.parameterarchive.ParameterIdDb.ParameterId;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.security.Privilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;

import io.netty.channel.ChannelFuture;

/**
 * Provides some maintenance operations on the parameter archive
 * @author nm
 *
 */
public class ParameterArchiveMaintenanceRestHandler extends RestHandler {
    /**
     * Request to (re)build the parameterArchive between start and stop
     * 
     */
    @Route(path = "/api/archive/:instance/parameterArchive/rebuild")
    public ChannelFuture reprocess(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        checkPrivileges(req);
            
        if(!req.hasQueryParameter("start")) {
            throw new BadRequestException("no start specified");
        }
        if(!req.hasQueryParameter("stop")) {
            throw new BadRequestException("no stop specified");
        }
        long start = req.getQueryParameterAsDate("start");
        long stop = req.getQueryParameterAsDate("stop");
        
        
        ParameterArchive parchive = getParameterArchive(instance);
        try {
            parchive.reprocess(start, stop);
        } catch (IllegalArgumentException e){
            throw new BadRequestException(e.getMessage());
        }
        
        return sendOK(req);
    }
    
    @Route(path = "/api/archive/:instance/parameterArchive/deletePartitions")
    public ChannelFuture deletePartition(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        checkPrivileges(req);
            
        if(!req.hasQueryParameter("start")) {
            throw new BadRequestException("no start specified");
        }
        if(!req.hasQueryParameter("stop")) {
            throw new BadRequestException("no stop specified");
        }
        long start = req.getQueryParameterAsDate("start");
        long stop = req.getQueryParameterAsDate("stop");
        
        
        ParameterArchive parchive = getParameterArchive(instance);
        try {
            NavigableMap<Long, Partition> removed = parchive.deletePartitions(start, stop);
            StringBuilder sb = new StringBuilder();
            sb.append("removed the following partitions: ");
            boolean first = true;
            for(Partition p: removed.values()) {
                if(first) first= false; else sb.append(", ");
                sb.append(p.toString());
            }
            StringMessage sm = StringMessage.newBuilder().setMessage(sb.toString()).build();
            
            return sendOK(req, sm, org.yamcs.protobuf.SchemaYamcs.StringMessage.WRITE);
            
        } catch (RocksDBException e){
            throw new InternalServerErrorException(e.getMessage());
        }
        
       
    }
    
    @Route(path = "/api/archive/:instance/parameterArchive/info/parameter/:name*")
    public ChannelFuture archiveInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        checkPrivileges(req);
        
        String fqn = req.getRouteParam("name");
        ParameterArchive parchive = getParameterArchive(instance);
        ParameterIdDb pdb = parchive.getParameterIdDb();
        ParameterId[] pids = pdb.get(fqn);
        StringMessage sm = StringMessage.newBuilder().setMessage(Arrays.toString(pids)).build();
        return sendOK(req, sm, org.yamcs.protobuf.SchemaYamcs.StringMessage.WRITE);
    }
   
    
    private static ParameterArchive getParameterArchive(String instance) throws BadRequestException {
        ParameterArchive parameterArchive = YamcsServer.getService(instance, ParameterArchive.class);
        if (parameterArchive == null) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }
        return parameterArchive;
    }
    
    private void checkPrivileges(RestRequest req) throws HttpException {
        if(!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.SYSTEM, Privilege.SystemPrivilege.MayControlArchiving.name()))  {
            throw new ForbiddenException("No privilege for this operation");
        }
    }
    
}
