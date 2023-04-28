package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DataTypeProcessorTest {

    @Test
    public void testSignedSizeInBits() {
        assertEquals(2, DataTypeProcessor.signedSizeInBits(1));
        assertEquals(1, DataTypeProcessor.signedSizeInBits(-1));
        assertEquals(2, DataTypeProcessor.signedSizeInBits(-2));
        assertEquals(3, DataTypeProcessor.signedSizeInBits(2));
        assertEquals(3, DataTypeProcessor.signedSizeInBits(-3));
        assertEquals(3, DataTypeProcessor.signedSizeInBits(3));

        assertEquals(1, DataTypeProcessor.signedSizeInBits(0));

    }
}
