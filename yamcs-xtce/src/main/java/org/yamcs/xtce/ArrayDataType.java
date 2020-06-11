package org.yamcs.xtce;

import java.util.List;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.gson.Gson;
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

    List<IntegerValue> dim;
    private DataType type;
    private int numberOfDimensions;
    private Object[] initialValue;

    public ArrayDataType(Builder<?> builder) {
        super(builder);

        if (builder.type == null) {
            throw new IllegalArgumentException("Array element type cannot be null");
        }
        this.dim = builder.dim;
        this.type = builder.type;
        this.numberOfDimensions = builder.numberOfDimensions;

        if (builder.initialValue != null) {
            if (builder.initialValue instanceof Object[]) {
                this.initialValue = (Object[]) builder.initialValue;
            } else {
                this.initialValue = parseString(builder.initialValue.toString());
            }
        }
    }

    public ArrayDataType(String name, int numberOfDimensions) {
        super(name);
        this.numberOfDimensions = numberOfDimensions;
    }

    public ArrayDataType(ArrayDataType t) {
        super(t);
        this.type = t.type;
        this.numberOfDimensions = t.numberOfDimensions;
        this.initialValue = t.initialValue;
        this.dim = t.dim;
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

    @Override
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

    /**
     * Return the dimension list (defined as from XTCE 1.2). The list here is not really used except for populating the
     * {@link ArrayParameterEntry#dim} at the MDB load.
     * 
     * @return
     */
    public List<IntegerValue> getSize() {
        return dim;
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
                r[i] = type.parseString(jarr.get(i).getAsString());
            }
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Cannot parse string as json: " + e.getMessage());
        }
        return r;
    }

    @Override
    public String toString(Object v) {
        if (v instanceof Object[]) {
            Object[] v1 = (Object[]) v;
            String[] v2 = new String[v1.length];
            for (int i = 0; i < v1.length; i++) {
                v2[i] = type.toString(v1[i]);
            }
            Gson gson = new Gson();
            return gson.toJson(v2);
        } else {
            throw new IllegalArgumentException("Can only convert arrays not " + v.getClass());
        }
    }

    @Override
    public Object[] getInitialValue() {
        return initialValue;
    }

    public static abstract class Builder<T extends Builder<T>> extends NameDescription.Builder<T>
            implements DataType.Builder<T> {
        List<IntegerValue> dim;
        private DataType type;
        private int numberOfDimensions;
        private Object initialValue;

        public Builder() {
        }

        public Builder(ArrayDataType dataType) {
            super(dataType);
            this.dim = dataType.dim;
            this.type = dataType.type;
            this.numberOfDimensions = dataType.numberOfDimensions;
            this.initialValue = dataType.initialValue;
        }

        /**
         * Sets the type of the elements of the array
         * 
         * @param type
         */
        public void setElementType(DataType type) {
            this.type = type;
        }

        public void setSize(List<IntegerValue> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Dimension sizes cannot be empty");
            }
            this.dim = list;
            setNumberOfDimensions(dim.size());
        }

        public T setNumberOfDimensions(int numberOfDimensions) {
            this.numberOfDimensions = numberOfDimensions;
            return self();
        }

        public void setInitialValue(String initialValue) {
            this.initialValue = initialValue;
        }

        public boolean isResolved() {
            return type != null;
        }
    }
}
