package org.yamcs.security;

import org.yamcs.security.protobuf.AccountRecord;
import org.yamcs.security.protobuf.ServiceAccountRecordDetail;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

/**
 * Represents an non-human service or application registered with Yamcs.
 * <p>
 * The service account is assumed to represent one application only.
 * <p>
 * Note that currently service accounts are only used for impersonation purposes. This is a strong feature equivalent to
 * the superuser attribute that should only be used for confidential applications.
 * <p>
 * In future iterations we may want to add weaker and more selective service accounts, for example by subjecting service
 * accounts to the same permission checks as regular users.
 */
public class ServiceAccount extends Account {

    // Client credentials can be used in the oauth2 layer to generate access tokens
    // for the single application that this service account represents.
    private String applicationId;
    private String applicationHash;

    public ServiceAccount(String name, User createdBy) {
        super(name, createdBy);
    }

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

    public String getApplicationId() {
        return applicationId;
    }

    void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    void setApplicationHash(String applicationHash) {
        this.applicationHash = applicationHash;
    }

    String getApplicationHash() {
        return applicationHash;
    }

    AccountRecord toRecord() {
        var serviceDetailb = ServiceAccountRecordDetail.newBuilder();
        serviceDetailb.setApplicationId(applicationId);
        serviceDetailb.setApplicationHash(applicationHash);
        return newRecordBuilder().setServiceDetail(serviceDetailb).build();
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
