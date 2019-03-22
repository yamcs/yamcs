package org.yamcs.xtce;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * An array of values of the type referenced in {@link #type} and have the number of array dimensions as specified in
 * {@link #numberOfDimensions}
 * 
 * @author nm
 *
 */
public class ArrayDataType extends NameDescription implements DataType {
    private static final long serialVersionUID = 1L;

    private DataType type;
    final private int numberOfDimensions;
    private Object[] initialValue;

    public ArrayDataType(String name, int numberOfDimensions) {
        super(name);
        this.numberOfDimensions = numberOfDimensions;
    }

    public ArrayDataType(ArrayDataType t) {
        super(t);
        this.type = t.type;
        this.numberOfDimensions = t.numberOfDimensions;
        this.initialValue = t.initialValue;
    }

    /**
     * Sets the type of the elements of the array
     * 
     * @param type
     */
    public void setElementType(DataType type) {
        this.type = type;
    }

    /**
     * returns the type of the elements of the array
     *
     * @return - the type of the elements of the array
     */
    public DataType getElementType() {
        return type;
    }


    public int getNumberOfDimensions() {
        return numberOfDimensions;
    }

    public Type getValueType() {
        return Type.ARRAY;
    }

    @Override
    public String getTypeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getName());
        for (int i = 0; i < numberOfDimensions; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    @Override
    public void setInitialValue(String initialValue) {
        this.initialValue = parseString(initialValue);
    }

    /**
     * Parse an initial value as an json array
     */
    @Override
    public Object[] parseString(String v) {
        
        Object[] r;
        try {
            JsonElement el = new JsonParser().parse(v);
            if (!(el instanceof JsonArray)) {
                throw new IllegalArgumentException("Expected an array but got a : " + el.getClass());
            }

            JsonArray jarr = (JsonArray) el;
            r = new Object[jarr.size()];
            for (int i = 0; i < jarr.size(); i++) {
                r[i] = type.parseString(jarr.get(i).toString());
            }
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Cannot parse string as json: " + e.getMessage());
        }
        return r;
    }

    @Override
    public Object[] getInitialValue() {
        return initialValue;
    }
}
