package org.yamcs.ui.packetviewer;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.yamcs.parameter.ContainerParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.ParameterType;

@SuppressWarnings("serial")
public class ParametersTableModel extends AbstractTableModel {

    List<ParameterValue> pvList = new ArrayList<>();
    static final String[] COLUMNS = { "Name", "Eng Value",
            "Raw Value", "Nominal Low", "Nominal High", "Danger Low",
            "Danger High", "Bit Offset", "Bit Size", "Calibration" };

    @Override
    public int getRowCount() {
        return pvList.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return COLUMNS[columnIndex];
    }

    public ParameterValue getParameterValue(int rowIndex) {
        return pvList.get(rowIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ParameterValue pv = pvList.get(rowIndex);
        switch (columnIndex) {
        case 0:
            return pv.getParameter();
        case 1:
            return (pv.getEngValue() != null) ? pv.getEngValue().toString() : null;
        case 2:
            return (pv.getRawValue() != null) ? pv.getRawValue().toString() : null;
        case 3:
            return pv.getWarningRange() == null ? "" : Double.toString(pv.getWarningRange().getMin());
        case 4:
            return pv.getWarningRange() == null ? "" : Double.toString(pv.getWarningRange().getMax());
        case 5:
            return pv.getCriticalRange() == null ? "" : Double.toString(pv.getCriticalRange().getMin());
        case 6:
            return pv.getCriticalRange() == null ? "" : Double.toString(pv.getCriticalRange().getMax());
        case 7:
            return (pv instanceof ContainerParameterValue)
                    ? String.valueOf(((ContainerParameterValue) pv).getAbsoluteBitOffset())
                    : null;
        case 8:
            return (pv instanceof ContainerParameterValue) ? String.valueOf(((ContainerParameterValue) pv).getBitSize())
                    : null;
        case 9:
            ParameterType paramtype = pv.getParameter().getParameterType();
            if (paramtype instanceof EnumeratedParameterType) {
                return paramtype;
            } else if (paramtype instanceof BaseDataType) {
                DataEncoding encoding = ((BaseDataType) paramtype).getEncoding();
                Calibrator calib = null;
                if (encoding instanceof IntegerDataEncoding) {
                    calib = ((IntegerDataEncoding) encoding).getDefaultCalibrator();
                } else if (encoding instanceof FloatDataEncoding) {
                    calib = ((FloatDataEncoding) encoding).getDefaultCalibrator();
                }
                return calib == null ? "" : calib.toString();
            } else {
                return null;
            }
        default:
            return null;
        }
    }

    public void clear() {
        pvList.clear();
    }

    public void addRow(ParameterValue pv) {
        pvList.add(pv);
    }
}
