package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Archive.TableData;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Rest.ListTablesResponse;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

public class ArchiveTableRequestHandler extends RestRequestHandler {

    @Override
    protected RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listTables(req, ydb);            
        } else {
            String tableName = req.getPathSegment(pathOffset);
            TableDefinition table = ydb.getTable(tableName);
            if (table == null) {
                throw new NotFoundException(req, "No table named '" + tableName + "'");
            } else {
                return handleTableRequest(req, pathOffset + 1, table);
            }
        }
    }
    
    private RestResponse handleTableRequest(RestRequest req, int pathOffset, TableDefinition table) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return getTable(req, table);
        } else {
            String resource = req.getPathSegment(pathOffset);
            switch (resource) {
            case "data":
                return getTableData(req, table);
            default:
                throw new NotFoundException(req, "No resource '" + resource + "' for table '" + table.getName() + "'");                
            }
        }
    }
    
    private RestResponse listTables(RestRequest req, YarchDatabase ydb) throws RestException {
        ListTablesResponse.Builder responseb = ListTablesResponse.newBuilder();
        for (TableDefinition def : ydb.getTableDefinitions()) {
            responseb.addTable(ArchiveHelper.toTableInfo(def));
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListTablesResponse.WRITE);
    }
    
    private RestResponse getTable(RestRequest req, TableDefinition table) throws RestException {
        TableInfo response = ArchiveHelper.toTableInfo(table);
        return new RestResponse(req, response, SchemaArchive.TableInfo.WRITE);
    }
    
    private RestResponse getTableData(RestRequest req, TableDefinition table) throws RestException {
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
        
        StringBuilder buf = new StringBuilder("select ");
        if (cols == null) {
            buf.append("*");
        } else if (cols.isEmpty()) {
            throw new BadRequestException("No columns are specified.");
        } else {
            for (int i = 0; i < cols.size(); i++) {
                if (i != 0) buf.append(", ");
                buf.append(cols.get(i));
            }
        }
        buf.append(" from ").append(table.getName());
        if (RestUtils.asksDescending(req, true)) {
            buf.append(" order desc");
        }
        
        String sql = buf.toString();
        
        TableData.Builder responseb = TableData.newBuilder();
        RestStreams.streamAndWait(req, sql, new RestStreamSubscriber(pos, limit) {
            
            @Override
            public void onTuple(Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                responseb.addRecord(rec); // TODO estimate byte size
            }
        });
        
        return new RestResponse(req, responseb.build(), SchemaArchive.TableData.WRITE);
    }
}
