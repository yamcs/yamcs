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
                this.initialValue = convertType(builder.initialValue.toString());
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
        sb.append(type.getTypeAsString().replace("[]", ""));
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
     * 
     * @return true if all dimensions are of fixed size
     */
    public boolean isFixedSize() {
        for (IntegerValue iv : dim) {
            if (!(iv instanceof FixedIntegerValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the size of the nth dimension
     */
    public IntegerValue getDimension(int n) {
        return dim.get(n);
    }

    /**
     * If {@link #isFixedSize()} returns true, this method can be used to get the array flat size
     * 
     * @return
     */
    public int[] getFixedSize() {
        int[] r = new int[dim.size()];
        for (int i = 0; i < dim.size(); i++) {
            FixedIntegerValue fiv = (FixedIntegerValue) dim.get(i);
            if (fiv instanceof FixedIntegerValue) {
                r[i] = (int) ((FixedIntegerValue) fiv).getValue();
            }
        }
        return r;
    }

    /**
     * Parse an initial value specified as an json array. Each element of the array has to be itself an array until
     * reaching the {@link #getNumberOfDimensions()}
     * <p>
     * The return is an java array (Object[]). For multi dimensional arrays each Object it itself an Object[] and so on
     * to reach the number of dimensions,
     * <p>
     * The final Object is of type as returned by the element type {@link DataType#convertType(Object)}
     * 
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object[] convertType(Object value) {
        if (value instanceof String) {
            return parse((String) value, false);
        } else if (value instanceof List) {
            return toArray((List<Object>) value, numberOfDimensions - 1, false);
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    @Override
    public Object parseStringForRawValue(String stringValue) {
        return parse(stringValue, true);
    }

    private Object[] parse(String stringValue, boolean raw) {
        try {
            JsonElement el = JsonParser.parseString(stringValue);
            return toArray(el, numberOfDimensions - 1, raw);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Cannot parse string as json: " + e.getMessage());
        }
    }

    private Object[] toArray(JsonElement jel, int numDim, boolean raw) {
        if (!(jel instanceof JsonArray)) {
            throw new IllegalArgumentException(
                    "Expected '" + jel + "' to be an array but instead it is: " + jel.getClass());
        }
        JsonArray jarr = (JsonArray) jel;
        Object[] r = new Object[jarr.size()];
        if (numDim > 0) {
            for (int i = 0; i < jarr.size(); i++) {
                r[i] = toArray(jarr.get(i), numDim - 1, raw);
            }
        } else if (raw) {
            for (int i = 0; i < jarr.size(); i++) {
                r[i] = type.parseStringForRawValue(jarr.get(i).getAsString());
            }
        } else {
            for (int i = 0; i < jarr.size(); i++) {
                r[i] = type.convertType(jarr.get(i).getAsString());
            }
        }

        return r;
    }

    @SuppressWarnings("unchecked")
    private Object[] toArray(List<Object> arr, int numDim, boolean raw) {
        Object[] r = new Object[arr.size()];
        if (numDim > 0) {
            for (int i = 0; i < arr.size(); i++) {
                Object el = arr.get(i);
                if (!(el instanceof List)) {
                    throw new IllegalArgumentException(
                            "Expected '" + el + "' to be an array but instead it is: " + el.getClass());
                }
                r[i] = toArray((List<Object>) el, numDim - 1, raw);
            }
        } else if (raw) {
            for (int i = 0; i < arr.size(); i++) {
                r[i] = type.parseStringForRawValue((String) arr.get(i));
            }
        } else {
            for (int i = 0; i < arr.size(); i++) {
                r[i] = type.convertType(arr.get(i));
            }
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
        public T setElementType(DataType type) {
            this.type = type;
            return self();
        }

        public void setSize(List<IntegerValue> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Dimension sizes cannot be empty");
            }
            this.dim = list;
            setNumberOfDimensions(dim.size());
        }

        public List<IntegerValue> getSize() {
            return dim;
        }

        public T setNumberOfDimensions(int numberOfDimensions) {
            this.numberOfDimensions = numberOfDimensions;
            return self();
        }

        @Override
        public T setInitialValue(String initialValue) {
            this.initialValue = initialValue;
            return self();
        }

        public boolean isResolved() {
            return type != null;
        }
    }
}
