package org.yamcs.ui.packetviewer;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.DefaultTableModel;

import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

public class PacketsTableModel extends DefaultTableModel {
    
    private static final long serialVersionUID = 1L;
    private static final String[] FIXED_COLUMNS = {"#", "Generation Time", "Packet Name"};
    
    private int continuousRowCount = 0; // Always increases, even when rows were removed
    private List<Parameter> shownColumnParameters = new ArrayList<>();
    
    public PacketsTableModel() {
        super(FIXED_COLUMNS, 0);
    }

    @Override
    public Class<?> getColumnClass(int column) { 
        if (column == 0) // #
            return Integer.class;
        else if (column == 1) // Generation Time
            return Long.class;
        else if (column == 2) // Name
            return ListPacket.class;
        else // Transposed Parameter
            return Object.class;
    }

    @Override
    public String getColumnName(int column) {
        if(column<FIXED_COLUMNS.length) {
            return FIXED_COLUMNS[column];
        } else {
            return shownColumnParameters.get(column-FIXED_COLUMNS.length).getName();
        }
    }

    public void addParameterColumn(Parameter p) {
        shownColumnParameters.add(p);
        addColumn(p.getName());
    }

    public void resetParameterColumns() {
        shownColumnParameters = new ArrayList<>();
    }
    
    public void addPacket(ListPacket packet) {
        List<Object> row = new ArrayList<Object>();
        row.add(++continuousRowCount);
        row.add( TimeEncoding.toCombinedFormat(packet.getGenerationTime()));
        row.add(packet);
        for(Parameter p:shownColumnParameters) {
            ParameterValue pv = packet.getParameterColumn(p);
            if(pv!=null) {
                row.add(getValue(pv));
            } else {
                row.add(null);
            }
        }
        addRow(row.toArray());
    }
    
    private Object getValue(ParameterValue pv) {
        Value v = pv.getEngValue();
        
        if(v==null) {
            return getValue(pv.getRawValue());
        } else {
            return getValue(v);
        }
    }
    
    private Object getValue(Value v) {
        return ValueUtility.getYarchValue(v);
    }

    public void clear() {
        setRowCount(0);
        continuousRowCount = 0;
    }
}
