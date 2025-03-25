package org.yamcs.mdb;

import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.UnsignedLong;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.BaseDataType;

/**
 * Calibrates float32/float64/sint32/sint64/uint32/uint64 into float32/float64/sint32/sint64/uint32/uint64
 * <p>
 * Converts the output to the expected type (the engineering value type for calibrations and the raw value type for
 * decalibrations)
 */
public class NumericCalibratorProc implements CalibratorProc {
    final NumericCalibrator numericCalibrator;
    BaseDataType dataType;

    public NumericCalibratorProc(BaseDataType dataType, NumericCalibrator numericCalibrator) {
        this.dataType = dataType;
        this.numericCalibrator = numericCalibrator;
    }

    @Override
    public Value calibrate(Value rawValue, ProcessingContext pctx) {
        return transform(rawValue, dataType.getValueType());
    }

    @Override
    public Value decalibrate(Value engValue, ProcessingContext pctx) {
        return transform(engValue, DataEncodingUtils.rawValueType(dataType.getEncoding()));
    }

    private Value transform(Value value, Type targetType) {
        // first convert the value to double
        double uncalibrated = switch (value.getType()) {
        case DOUBLE -> value.getDoubleValue();
        case FLOAT -> value.getFloatValue();
        case SINT32 -> value.getSint32Value();
        case SINT64 -> value.getSint64Value();
        case UINT32 -> value.getUint32Value() & 0xFFFFFFFFL;
        case UINT64 -> UnsignedLong.toDouble(value.getUint64Value());
        case STRING -> {
            try {
                yield Double.parseDouble(value.getStringValue());
            } catch (NumberFormatException e) {
                throw new XtceProcessingException(
                        "failed to parse string '" + value.getStringValue() + "' to double");
            }
        }
        default -> {
            throw new XtceProcessingException("Cannot numerically calibrate a raw value of type " + value.getType());
        }
        };

        // then calibrate
        double calibrated = numericCalibrator.calibrate(uncalibrated);

        // and then convert the result to the target type
        return switch (targetType) {
        case SINT32 -> ValueUtility.getSint32Value((int) Math.round(calibrated));
        case SINT64 -> ValueUtility.getSint64Value(Math.round(calibrated));
        case UINT32 -> ValueUtility.getUint32Value(calibrated >= 0 ? (int) Math.round(calibrated) : 0);
        case UINT64 -> ValueUtility.getUint64Value(calibrated >= 0 ? Math.round(calibrated) : 0);
        case FLOAT -> ValueUtility.getFloatValue((float) calibrated);
        case DOUBLE -> ValueUtility.getDoubleValue(calibrated);
        default -> {
            throw new IllegalStateException("Cannot convert value to " + targetType);
        }
        };
    }
    
    /**
     * performs double to double calibration using the inner calibrator
     */
    public double calibrate(double v) {
        return numericCalibrator.calibrate(v);
    }

    @Override
    public String toString() {
        return "NumericCalibratorProc [dataType=" + dataType + ", numericCalibrator=" + numericCalibrator + "]";
    }

}
