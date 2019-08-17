package org.yamcs.http.api.mdb;

import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Mdb.BatchGetParametersRequest;
import org.yamcs.protobuf.Mdb.GetParameterRequest;
import org.yamcs.protobuf.Mdb.ListParametersRequest;

import com.google.protobuf.Descriptors.FieldDescriptor;

public abstract class AbstractMdbHandler extends RestHandler {

    protected Log log = new Log(getClass());

    @Route(rpc = "yamcs.protobuf.mdb.MDB.GetParameter")
    public void wrapGetParameter(RestRequest req) throws HttpException {
        GetParameterRequest.Builder b = GetParameterRequest.newBuilder();
        for (FieldDescriptor field : GetParameterRequest.getDescriptor().getFields()) {
            if (req.hasRouteParam(field.getJsonName())) {
                b.setField(field, req.getRouteParam(field.getJsonName()));
            } else if (req.hasQueryParameter(field.getJsonName())) {
                b.setField(field, convertToFieldValue(req, field, field.getJsonName()));
            }
        }
        getParameter(req, b.build());
    }

    abstract void getParameter(RestRequest req, GetParameterRequest request) throws HttpException;

    @Route(rpc = "yamcs.protobuf.mdb.MDB.BatchGetParameters")
    public void wrapBatchGetParameters(RestRequest req) throws HttpException {
        BatchGetParametersRequest.Builder b = req.bodyAsMessage(BatchGetParametersRequest.newBuilder());
        for (FieldDescriptor field : BatchGetParametersRequest.getDescriptor().getFields()) {
            if (req.hasRouteParam(field.getJsonName())) {
                b.setField(field, req.getRouteParam(field.getJsonName()));
            }
        }
        batchGetParameters(req, b.build());
    }

    abstract void batchGetParameters(RestRequest req, BatchGetParametersRequest request) throws HttpException;

    @Route(rpc = "yamcs.protobuf.mdb.MDB.ListParameters")
    public void wrapListParameters(RestRequest req) throws HttpException {
        ListParametersRequest.Builder b = ListParametersRequest.newBuilder();
        for (FieldDescriptor field : ListParametersRequest.getDescriptor().getFields()) {
            if (req.hasRouteParam(field.getJsonName())) {
                b.setField(field, req.getRouteParam(field.getJsonName()));
            } else if (req.hasQueryParameter(field.getJsonName())) {
                b.setField(field, convertToFieldValue(req, field, field.getJsonName()));
            }
        }

        listParameters(req, b.build());
    }

    abstract void listParameters(RestRequest req, ListParametersRequest request) throws HttpException;
}
