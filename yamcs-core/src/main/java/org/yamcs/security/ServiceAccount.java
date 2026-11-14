package org.yamcs.security;

import org.yamcs.security.protobuf.AccountRecord;
import org.yamcs.security.protobuf.ServiceAccountRecordDetail;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

/**
 * Service account stored by an older version of Yamcs.
 * <p>
 * Service accounts are no longer supported. Existing records are still loaded so that the directory remains
 * consistent (their ids and names stay reserved), but they cannot be used to authenticate and are not exposed through
 * any API.
 */
class ServiceAccount extends Account {

    private String applicationId;
    private String applicationHash;

    ServiceAccount(AccountRecord record) {
        super(record);
        ServiceAccountRecordDetail serviceDetail = record.getServiceDetail();
        applicationId = serviceDetail.getApplicationId();
        applicationHash = serviceDetail.getApplicationHash();
    }

    ServiceAccount(Tuple tuple) {
        super(tuple);
        ServiceAccountRecordDetail serviceDetail = tuple.getColumn(DirectoryDb.ACCOUNT_CNAME_SERVICE_DETAIL);
        applicationId = serviceDetail.getApplicationId();
        applicationHash = serviceDetail.getApplicationHash();
    }

    @Override
    public Tuple toTuple(boolean forUpdate) {
        var tuple = super.toTuple(forUpdate);

        var serviceDetailb = ServiceAccountRecordDetail.newBuilder();
        serviceDetailb.setApplicationId(applicationId);
        serviceDetailb.setApplicationHash(applicationHash);

        tuple.addColumn(DirectoryDb.ACCOUNT_CNAME_SERVICE_DETAIL,
                DataType.protobuf(ServiceAccountRecordDetail.class), serviceDetailb.build());

        return tuple;
    }
}
