package org.yamcs.yarch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnSerializerFactory.AbstractColumnSerializer;

import org.yamcs.yarch.protobuf.Db;

import com.google.common.io.ByteStreams;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.CodedOutputStream.OutOfSpaceException;

public class ParameterValueColumnSerializer extends AbstractColumnSerializer<ParameterValue> {

    public ParameterValueColumnSerializer() {
        super(16);
    }

    @Override
    public ParameterValue deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
        int size = stream.readInt();
        if (size > ColumnSerializerFactory.maxBinaryLength) {
            throw new IOException("serialized size too big " + size + ">" + ColumnSerializerFactory.maxBinaryLength);
        }

        Db.ParameterValue.Builder gpvb = Db.ParameterValue.newBuilder();

        try (InputStream limitedInput = ByteStreams.limit(stream, size)) {
            gpvb.mergeFrom(limitedInput);
            return fromProto(cd.getName(), gpvb.build());
        }
    }

    @Override
    public ParameterValue deserialize(ByteBuffer byteBuf, ColumnDefinition cd) throws IOException {
        int size = byteBuf.getInt();
        if (size > ColumnSerializerFactory.maxBinaryLength) {
            throw new IOException("serialized size too big " + size + ">" + ColumnSerializerFactory.maxBinaryLength);
        }

        Db.ParameterValue.Builder gpvb = Db.ParameterValue.newBuilder();

        int limit = byteBuf.limit();
        byteBuf.limit(byteBuf.position() + size);
        gpvb.mergeFrom(CodedInputStream.newInstance(byteBuf));
        byteBuf.limit(limit);
        byteBuf.position(byteBuf.position() + size);

        return fromProto(cd.getName(), gpvb.build());
    }

    @Override
    public void serialize(DataOutputStream stream, ParameterValue pv) throws IOException {
        Db.ParameterValue gpv = toProto(pv);
        int size = gpv.getSerializedSize();
        stream.writeInt(size);
        gpv.writeTo(stream);
    }

    @Override
    public void serialize(ByteBuffer byteBuf, ParameterValue pv) {
        Db.ParameterValue gpv = toProto(pv);
        try {
            int position = byteBuf.position();
            byteBuf.putInt(0);
            CodedOutputStream cos = CodedOutputStream.newInstance(byteBuf);
            gpv.writeTo(cos);
            int size = cos.getTotalBytesWritten();
            byteBuf.putInt(position, size);
            byteBuf.position(position + size + 4);
        } catch (IOException e) {
            if (e instanceof OutOfSpaceException) {
                throw new BufferOverflowException();
            } else {
                throw new UncheckedIOException(e);
            }
        }
    }

    private ParameterValue fromProto(String fqn, Db.ParameterValue gpv) {
        ParameterValue pv = new ParameterValue(fqn);
        pv.setAcquisitionStatus(gpv.getAcquisitionStatus());

        if (gpv.hasEngValue()) {
            pv.setEngineeringValue(ValueUtility.fromGpb(gpv.getEngValue()));
        }

        if (gpv.hasAcquisitionTime()) {
            pv.setAcquisitionTime(gpv.getAcquisitionTime());
        }

        if (gpv.hasExpireMillis()) {
            pv.setExpireMillis(gpv.getExpireMillis());
        }

        if (gpv.hasGenerationTime()) {
            pv.setGenerationTime(gpv.getGenerationTime());
        }
        if (gpv.hasMonitoringResult()) {
            pv.setMonitoringResult(gpv.getMonitoringResult());
        }

        if (gpv.hasRangeCondition()) {
            pv.setRangeCondition(gpv.getRangeCondition());
        }

        if (gpv.hasProcessingStatus()) {
            pv.setProcessingStatus(gpv.getProcessingStatus());
        }

        if (gpv.hasRawValue()) {
            pv.setRawValue(ValueUtility.fromGpb(gpv.getRawValue()));
        }

        return pv;
    }

    public Db.ParameterValue toProto(ParameterValue pv) {

        Db.ParameterValue.Builder gpvb = Db.ParameterValue.newBuilder()
                .setAcquisitionStatus(pv.getAcquisitionStatus())
                .setGenerationTime(pv.getGenerationTime())
                .setProcessingStatus(pv.getProcessingStatus());

        if (pv.getAcquisitionTime() != TimeEncoding.INVALID_INSTANT) {
            gpvb.setAcquisitionTime(pv.getAcquisitionTime());
        }

        if (pv.getEngValue() != null) {
            gpvb.setEngValue(ValueUtility.toGbp(pv.getEngValue()));
        }

        if (pv.getMonitoringResult() != null) {
            gpvb.setMonitoringResult(pv.getMonitoringResult());
        }
        if (pv.getRangeCondition() != null) {
            gpvb.setRangeCondition(pv.getRangeCondition());
        }

        long expireMillis = pv.getExpireMills();
        if (expireMillis >= 0) {
            gpvb.setExpireMillis(expireMillis);
        }

        if (pv.getWatchRange() != null) {
            gpvb.addAlarmRange(BasicParameterValue.toGpbAlarmRange(AlarmLevelType.WATCH, pv.getWatchRange()));
        }
        if (pv.getWarningRange() != null) {
            gpvb.addAlarmRange(BasicParameterValue.toGpbAlarmRange(AlarmLevelType.WARNING, pv.getWarningRange()));
        }
        if (pv.getDistressRange() != null) {
            gpvb.addAlarmRange(BasicParameterValue.toGpbAlarmRange(AlarmLevelType.DISTRESS, pv.getDistressRange()));
        }
        if (pv.getCriticalRange() != null) {
            gpvb.addAlarmRange(BasicParameterValue.toGpbAlarmRange(AlarmLevelType.CRITICAL, pv.getCriticalRange()));
        }
        if (pv.getSevereRange() != null) {
            gpvb.addAlarmRange(BasicParameterValue.toGpbAlarmRange(AlarmLevelType.SEVERE, pv.getSevereRange()));
        }

        if (pv.getRawValue() != null) {
            gpvb.setRawValue(ValueUtility.toGbp(pv.getRawValue()));
        }
        return gpvb.build();
    }
}
