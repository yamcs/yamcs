package org.yamcs.xtce;

/**
 * AggegateParameters are analogous to a C struct, they are an aggregation of related data items. Each of these data
 * items is defined here as a 'Member'
 * 
 * @author nm
 *
 */
public class AggregateParameterType extends AggregateDataType implements ParameterType {
    public AggregateParameterType(String name) {
        super(name);
    }

    public AggregateParameterType(AggregateParameterType t) {
        super(t);
    }

    private static final long serialVersionUID = 1L;

   
    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public Object parseString(String stringValue) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object parseStringForRawValue(String stringValue) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setEncoding(DataEncoding dataEncoding) {
        throw new UnsupportedOperationException("aggregate parameters do not support encodings");
    }

    @Override
    public AggregateParameterType copy() {
        return new AggregateParameterType(this);
    }

    @Override
    public void setInitialValue(String initialValue) {
    }
}
