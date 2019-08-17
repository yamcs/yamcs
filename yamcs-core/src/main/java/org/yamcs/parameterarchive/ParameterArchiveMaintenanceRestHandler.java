package org.yamcs.parameterarchive;

import java.util.Arrays;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.security.SystemPrivilege;

/**
 * Provides some maintenance operations on the parameter archive
 * 
 * @author nm
 *
 */
public class ParameterArchiveMaintenanceRestHandler extends RestHandler {

    /**
     * Request to (re)build the parameterArchive between start and stop
     * 
     */
    @Route(rpc = "yamcs.protobuf.archive.ParameterArchive.RebuildRange", offThread = true)
    public void reprocess(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        if (!req.hasQueryParameter("start")) {
            throw new BadRequestException("no start specified");
        }
        if (!req.hasQueryParameter("stop")) {
            throw new BadRequestException("no stop specified");
        }
        long start = req.getQueryParameterAsDate("start");
        long stop = req.getQueryParameterAsDate("stop");

        ParameterArchive parchive = getParameterArchive(instance);
        try {
            parchive.reprocess(start, stop);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        completeOK(req);
    }

    @Route(rpc = "yamcs.protobuf.archive.ParameterArchive.DeletePartitions")
    public void deletePartition(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        if (!req.hasQueryParameter("start")) {
            throw new BadRequestException("no start specified");
        }
        if (!req.hasQueryParameter("stop")) {
            throw new BadRequestException("no stop specified");
        }
        long start = req.getQueryParameterAsDate("start");
        long stop = req.getQueryParameterAsDate("stop");

        ParameterArchive parchive = getParameterArchive(instance);
        try {
            List<Partition> removed = parchive.deletePartitions(start, stop);
            StringBuilder sb = new StringBuilder();
            sb.append("removed the following partitions: ");
            boolean first = true;
            for (Partition p : removed) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(p.toString());
            }
            StringMessage sm = StringMessage.newBuilder().setMessage(sb.toString()).build();

            completeOK(req, sm);

        } catch (RocksDBException e) {
            throw new InternalServerErrorException(e.getMessage());
        }

    }

    @Route(rpc = "yamcs.protobuf.archive.ParameterArchive.GetArchiveInfo")
    public void archiveInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        String fqn = req.getRouteParam("name");
        ParameterArchive parchive = getParameterArchive(instance);
        ParameterIdDb pdb = parchive.getParameterIdDb();
        ParameterId[] pids = pdb.get(fqn);
        StringMessage sm = StringMessage.newBuilder().setMessage(Arrays.toString(pids)).build();
        completeOK(req, sm);
    }

    private ParameterArchive getParameterArchive(String instance) throws BadRequestException {
        List<ParameterArchive> l = yamcsServer.getServices(instance, ParameterArchive.class);

        if (l.isEmpty()) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }

        return l.get(0);
    }
}
