package org.yamcs.xtce;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Set;

/**
 * Describes how a particular piece of data is sent or received from some non-native, off-platform device. (e.g. a
 * spacecraft)
 * <p>
 * 
 * DIFFERS_FROM_XTCE: XTCE defines known encodings for the usual types (e.g. twosComplement for signed integers) and
 * allows a catch all using a BinaryDataEncoding with a custom algorithm. We consider this approach as flawed and
 * inconsistent: whereas FloatDataEncoding converts from binary to float, IntegerDataEncoding converts from binary to
 * integer, etc, the BinaryDataEncoding would convert from binary to anything and it cannot be known into what by just
 * looking at it.
 * 
 * Therefore in Yamcs we allow the catch all custom algorithm for all encodings and the BinaryDataEncoding can only
 * convert from binary to binary.
 * 
 *
 */
public abstract class DataEncoding implements Serializable {
    private static final long serialVersionUID = 4L;

    /**
     * size in bits if known. If the size in bits is variable, it should be set to -1.
     */
    protected int sizeInBits;

    transient ByteOrder byteOrder = ByteOrder.BIG_ENDIAN; // DIFFERS_FROM_XTCE in xtce is very complicated

    // the algorithm will be used to convert from binary to raw value
    protected Algorithm fromBinaryTransformAlgorithm;

    // the algorithm will be used to convert from raw value to binary
    protected Algorithm toBinaryTransformAlgorithm;

    /**
     * copy constructor
     * 
     * @param ide
     */
    DataEncoding(DataEncoding de) {
        this.sizeInBits = de.sizeInBits;
        this.byteOrder = de.byteOrder;
        this.fromBinaryTransformAlgorithm = de.fromBinaryTransformAlgorithm;
        this.toBinaryTransformAlgorithm = de.toBinaryTransformAlgorithm;
    }

    DataEncoding(Builder<?> builder, int defaultSizeInBits) {
        this.sizeInBits = defaultSizeInBits;

        if (builder.sizeInBits != null) {
            this.sizeInBits = builder.sizeInBits;
        }

        if (builder.byteOrder != null) {
            this.byteOrder = builder.byteOrder;
        }

        this.fromBinaryTransformAlgorithm = builder.fromBinaryTransformAlgorithm;
        this.toBinaryTransformAlgorithm = builder.toBinaryTransformAlgorithm;

        if (builder.baseEncoding != null) {
            DataEncoding baseEncoding = builder.baseEncoding;

            if (builder.sizeInBits == null) {
                this.sizeInBits = baseEncoding.sizeInBits;
            }
            if (builder.byteOrder == null) {
                this.byteOrder = baseEncoding.byteOrder;
            }
            if (builder.fromBinaryTransformAlgorithm == null) {
                this.fromBinaryTransformAlgorithm = baseEncoding.fromBinaryTransformAlgorithm;
            }
            if (builder.toBinaryTransformAlgorithm == null) {
                this.toBinaryTransformAlgorithm = baseEncoding.toBinaryTransformAlgorithm;
            }
        }
    }

    /**
     * Returns the size in bits of data encoded according to this encoding. For some encodings like
     * {@link StringDataEncoding} the size may be variable (depending on the data to be encoded). In this cases it
     * returns -1.
     * 
     * @return size in bits or -1 if the size is unknown
     */
    public int getSizeInBits() {
        return sizeInBits;
    }

    public void setSizeInBits(int sizeInBits) {
        this.sizeInBits = sizeInBits;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    // these two methods are used for serialisation because ByteOrder is not serializable
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            out.writeInt(0);
        } else {
            out.writeInt(1);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int o = in.readInt();
        if (o == 0) {
            byteOrder = ByteOrder.BIG_ENDIAN;
        } else {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        }
    }

    /**
     * parses the string into a java object of the correct type Has to match the DataEncodingDecoder (so probably it
     * should be moved there somehow: TODO)
     */
    public abstract Object parseString(String stringValue);

    public Algorithm getFromBinaryTransformAlgorithm() {
        return fromBinaryTransformAlgorithm;
    }

    public void setFromBinaryTransformAlgorithm(Algorithm fromBinaryTransformAlgorithm) {
        this.fromBinaryTransformAlgorithm = fromBinaryTransformAlgorithm;
    }

    public Algorithm getToBinaryTransformAlgorithm() {
        return toBinaryTransformAlgorithm;
    }

    public void setToBinaryTransformAlgorithm(Algorithm toBinaryTransformAlgorithm) {
        this.toBinaryTransformAlgorithm = toBinaryTransformAlgorithm;
    }

    public Set<Parameter> getDependentParameters() {
        return Collections.emptySet();
    }

    /**
     * Create a shallow copy of the data encoding
     * 
     * @return
     */
    public abstract DataEncoding copy();

    public abstract static class Builder<T extends Builder<T>> {
        protected Integer sizeInBits;
        transient ByteOrder byteOrder = null;
        private Algorithm fromBinaryTransformAlgorithm;
        private Algorithm toBinaryTransformAlgorithm;
        DataEncoding baseEncoding;

        public Builder(DataEncoding encoding) {
            this.sizeInBits = encoding.sizeInBits;
            this.byteOrder = encoding.byteOrder;
            this.fromBinaryTransformAlgorithm = encoding.fromBinaryTransformAlgorithm;
            this.toBinaryTransformAlgorithm = encoding.toBinaryTransformAlgorithm;
        }

        public Builder() {
        }

        public T setSizeInBits(Integer sizeInBits) {
            this.sizeInBits = sizeInBits;
            return self();
        }

        public T setFromBinaryTransformAlgorithm(Algorithm alg) {
            this.fromBinaryTransformAlgorithm = alg;
            return self();
        }

        public T setToBinaryTransformAlgorithm(Algorithm alg) {
            this.toBinaryTransformAlgorithm = alg;
            return self();
        }

        public T setByteOrder(ByteOrder byteOrder) {
            this.byteOrder = byteOrder;
            return self();
        }

        public ByteOrder getByteOrder() {
            return byteOrder;
        }

        public Algorithm getFromBinaryTransformAlgorithm() {
            return fromBinaryTransformAlgorithm;
        }

        public Algorithm getToBinaryTransformAlgorithm() {
            return toBinaryTransformAlgorithm;
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        protected abstract DataEncoding build();

        public Integer getSizeInBits() {
            return sizeInBits;
        }

    }

    public abstract Builder<?> toBuilder();
}
