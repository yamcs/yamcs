package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Archive.TableData;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Rest.ListTablesResponse;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

public class ArchiveTableRestHandler extends RestHandler {
    
    @Route(path = "/api/archive/:instance/tables", method = "GET")
    public void listTables(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        
        ListTablesResponse.Builder responseb = ListTablesResponse.newBuilder();
        for (TableDefinition def : ydb.getTableDefinitions()) {
            responseb.addTable(ArchiveHelper.toTableInfo(def));
        }
        completeOK(req, responseb.build(), SchemaRest.ListTablesResponse.WRITE);
    }
    
    @Route(path = "/api/archive/:instance/tables/:name", method = "GET")
    public void getTable(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(req, ydb, req.getRouteParam("name"));
        
        TableInfo response = ArchiveHelper.toTableInfo(table);
        completeOK(req, response, SchemaArchive.TableInfo.WRITE);
    }
    
    @Route(path = "/api/archive/:instance/tables/:name/data", method = "GET")
    public void getTableData(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(req, ydb, req.getRouteParam("name"));
        
        List<String> cols = null;        
        if (req.hasQueryParameter("cols")) {
            cols = new ArrayList<>(); // Order, and non-unique
            for (String para : req.getQueryParameterList("cols")) {
                for (String col : para.split(",")) {
                    cols.add(col.trim());
                }
            }
        }
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        SqlBuilder sqlb = new SqlBuilder(table.getName());
        if (cols != null) {
            if (cols.isEmpty()) {
                throw new BadRequestException("No columns were specified");    
            } else {
                cols.forEach(col -> sqlb.select(col));
            }
        }
        sqlb.descend(req.asksDescending(true));
        
        String sql = sqlb.toString();
        TableData.Builder responseb = TableData.newBuilder();
        RestStreams.stream(instance, sql, new RestStreamSubscriber(pos, limit) {
            
            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                responseb.addRecord(rec); // TODO estimate byte size
            }

            @Override
            public void streamClosed(Stream stream) {
                completeOK(req, responseb.build(), SchemaArchive.TableData.WRITE);
            }
        });
        
       
    }
}
