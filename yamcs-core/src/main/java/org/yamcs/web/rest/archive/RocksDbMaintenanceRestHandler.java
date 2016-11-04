package org.yamcs.web.rest.archive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.cli.Backup;
import org.yamcs.security.Privilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.yarch.rocksdb.RDBFactory;
import org.yamcs.yarch.rocksdb.YRDB;

public class RocksDbMaintenanceRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(RocksDbMaintenanceRestHandler.class);
    
    
    @Route(path = "/api/archive/:instance/rocksdb/properties/:dbpath*", method = "GET")
    public void getProperty(RestRequest req) throws HttpException {
        checkPrivileges(req);
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        String dbpath = req.getRouteParam("dbpath");
        
        RDBFactory rdbFactory = RDBFactory.getInstance(instance);
        if(rdbFactory==null) {
            throw new BadRequestException("No Rocksdb for instance "+instance);
        }

        YRDB yrdb = rdbFactory.getOpenRdb(dbpath);
        if(yrdb == null) {
            yrdb = rdbFactory.getOpenRdb("/"+dbpath);
        }
        if(yrdb == null) {
            throw new BadRequestException("No Open database "+dbpath+" for instance "+instance);
        }
       
        try {
            String s = yrdb.getProperites();
            CharBuffer props = CharBuffer.wrap(s);
            ByteBuf buf = ByteBufUtil.encodeString(req.getChannelHandlerContext().alloc(), props, StandardCharsets.UTF_8);
            completeOK(req, MediaType.PLAIN_TEXT, buf);
        } catch (RocksDBException e) {
            log.error("Error when getting database properties",e);
            completeWithError(req, new InternalServerErrorException(e));
        } finally {
            rdbFactory.dispose(yrdb);
        }
    }
    
    @Route(path = "/api/archive/:instance/rocksdb/list", method = "GET")
    public void listOpenDbs(RestRequest req) throws HttpException {
        checkPrivileges(req);
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        RDBFactory rdbFactory = RDBFactory.getInstance(instance);
        if(rdbFactory==null) {
            throw new BadRequestException("No Rocksdb for instance "+instance);
        }
        List<String> list =rdbFactory.getOpenDbPaths();
        StringBuilder sb = new StringBuilder();
        for(String s:list) {
            sb.append(s).append("\n");
        }
        CharBuffer props = CharBuffer.wrap(sb.toString());
        ByteBuf buf = ByteBufUtil.encodeString(req.getChannelHandlerContext().alloc(), props, StandardCharsets.UTF_8);
        completeOK(req, MediaType.PLAIN_TEXT, buf);
    }
    
    
    @Route(path = "/api/archive/:instance/rocksdb/backup/:dbpath*", method = "POST")
    public void doBackup(RestRequest req) throws HttpException {
        checkPrivileges(req);
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        String dbdir = "/"+req.getRouteParam("dbpath");
        String backupDir = req.getQueryParameter("backupDir");
        if(backupDir==null) throw new BadRequestException("No backup directory specified");
        try {
            Backup.verifyBackupDirectory(backupDir, false);
        } catch (Exception e1) {
            throw new BadRequestException(e1.getMessage());
        }
        Path dbpath = FileSystems.getDefault().getPath(dbdir);
        if(!Files.exists(dbpath)) throw new BadRequestException("Database '"+dbpath+"' does not exist");
        
        RDBFactory rdbFactory = RDBFactory.getInstance(instance);
        if(rdbFactory==null) {
            throw new BadRequestException("No Rocksdb for instance "+instance);
        }

        CompletableFuture<Void> cf = rdbFactory.doBackup(dbdir, backupDir);
        cf.whenComplete((r, e) -> {
            if(e!=null) {
                completeWithError(req, new InternalServerErrorException(e));
            } else {
                completeOK(req);
            }
        });
        
       
    }
    
    private void checkPrivileges(RestRequest req) throws HttpException {
        if(!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.SYSTEM, Privilege.SystemPrivilege.MayControlArchiving.name()))  {
            throw new ForbiddenException("No privilege for this operation");
        }
    }
   
}
