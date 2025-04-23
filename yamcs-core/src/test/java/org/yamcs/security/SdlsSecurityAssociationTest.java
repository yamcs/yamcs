package org.yamcs.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.security.SdlsSecurityAssociation.VerificationStatusCode;
import org.yamcs.utils.ByteArrayUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class SdlsSecurityAssociationTest {
    final Path RESOURCE_DIR = Paths.get("src", "test", "resources", "sdls");
    byte[] key, authMask, frame, inputPlaintext, inputPrimaryHeader;
    int dataStart, dataEnd;
    SdlsSecurityAssociation sa;

    @BeforeEach
    void prepareFrame() throws IOException {
        // Load the key
        Path keypath = RESOURCE_DIR.resolve("presharedkey");
        this.key = Files.readAllBytes(keypath);

        // We have an example frame of length:
        int frameLength = 38;
        frame = new byte[frameLength];
        // Header size is 6 for primary header + security header size
        int headerSize = 6 + SdlsSecurityAssociation.getHeaderSize();
        // So data start is after the headers
        dataStart = headerSize;
        // And data end is before security trailer
        dataEnd = frameLength - SdlsSecurityAssociation.getTrailerSize();

        authMask = new byte[6];
        authMask[1] = 0b0000_1110; // authenticate virtual channel ID

        // Create the primary header: all 1s except the field to be authenticated
        inputPrimaryHeader = new byte[6];
        Arrays.fill(inputPrimaryHeader, 0, 6, (byte) 1);
        inputPrimaryHeader[1] = 0b0000_1000; // make the virtual channel ID = 4
        System.arraycopy(inputPrimaryHeader, 0, frame, 0, inputPrimaryHeader.length);

        // Create some plaintext
        inputPlaintext = new byte[dataEnd - dataStart];
        assertEquals(frameLength - 6 - SdlsSecurityAssociation.getOverheadBytes(), inputPlaintext.length);
        Arrays.fill(inputPlaintext, (byte) 69);
        //new Random().nextBytes(inputPlaintext);
        System.arraycopy(inputPlaintext, 0, frame, dataStart, inputPlaintext.length);

        // Create SA
        sa = new SdlsSecurityAssociation(key, (short) 42, authMask);

    }

    @Test
    void givenWrongKey_failDecrypt() throws IOException {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Try to decrypt with a wrong key, and fail
        Path wrongKeypath = RESOURCE_DIR.resolve("wrong-presharedkey");
        byte[] wrongKey = Files.readAllBytes(wrongKeypath);
        SdlsSecurityAssociation wrongSa = new SdlsSecurityAssociation(wrongKey, (short) 42, authMask);
        assertEquals(VerificationStatusCode.MacVerificationFailure, wrongSa.processSecurity(frame, 0, dataStart, frame.length));
    }

    @Test
    void givenWrongSpi_failVerify() throws IOException {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Try to decrypt with a wrong SPI
        Path wrongKeypath = RESOURCE_DIR.resolve("presharedkey");
        byte[] wrongKey = Files.readAllBytes(wrongKeypath);
        SdlsSecurityAssociation wrongSa = new SdlsSecurityAssociation(wrongKey, (short) 1, authMask);
        assertEquals(VerificationStatusCode.InvalidSPI, wrongSa.processSecurity(frame, 0, dataStart, frame.length));
    }

    @Test
    void whenBitFlipPrimaryHeaderAuthdField_failAuth() {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Bitflip VCID of primary header
        int vcid_octet_frame_primary_header = 1;
        byte oldValue = frame[vcid_octet_frame_primary_header];
        frame[vcid_octet_frame_primary_header] = 0b0000_1100;

        assertEquals(VerificationStatusCode.MacVerificationFailure, sa.processSecurity(frame, 0, dataStart, frame.length));
    }

    @Test
    void whenBitFlipSecurityHeader_failVerifySPI() {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Bit-flip SPID of security header
        int spid_octet_sec_header = 6;
        byte oldValue = frame[spid_octet_sec_header];
        frame[spid_octet_sec_header] = (byte)(oldValue ^ 1);

        assertEquals(VerificationStatusCode.InvalidSPI, sa.processSecurity(frame, 0, dataStart, frame.length));
    }

    @Test
    void whenBitFlipNonAuthData_succeed() {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Master channel frame count is explicitly excluded from authentication in standard
        // Bitflip shouldn't cause a problem
        int mc_frcnt_octet_fph = 2;
        // Need to make a copy here because decryption will succeed
        byte oldValue = frame[mc_frcnt_octet_fph];
        frame[mc_frcnt_octet_fph] = (byte)(oldValue ^ 1);

        assertEquals(VerificationStatusCode.NoFailure, sa.processSecurity(frame, 0, dataStart, frame.length));

        // Check the decrypted data matches the original input
        byte[] plaintext2 = Arrays.copyOfRange(frame, dataStart, dataEnd);
        assertArrayEquals(inputPlaintext, plaintext2);
    }

    @Test
    void whenWrongSecurityTag_failAuth() {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Mess with the security tag and fail
        int security_tag_first_byte = dataEnd + 1;
        byte oldValue = frame[security_tag_first_byte];
        frame[security_tag_first_byte] = (byte) (oldValue ^ 1);

        assertEquals(VerificationStatusCode.MacVerificationFailure, sa.processSecurity(frame, 0, dataStart, frame.length));
    }

    @Test
    void whenCorruptedCipherText_failAuth() {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Mess with the ciphertext and fail
        int second_ciphertext_byte = dataStart + 1;
        byte oldValue = frame[second_ciphertext_byte];
        frame[second_ciphertext_byte] = (byte) (oldValue ^ 1);

        assertEquals(VerificationStatusCode.MacVerificationFailure, sa.processSecurity(frame, 0, dataStart, frame.length));
    }

    @Test
    void testEncryptDecrypt() {
        // Encrypt frame
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));

        // Check the data is encrypted
        byte[] ciphertext = Arrays.copyOfRange(frame, dataStart, dataEnd);
        assertFalse(Arrays.equals(inputPlaintext, ciphertext));

        // Decrypt frame
        assertEquals(VerificationStatusCode.NoFailure, sa.processSecurity(frame, 0, dataStart, frame.length));

        // Check the decrypted data matches the original input
        byte[] plaintext2 = Arrays.copyOfRange(frame, dataStart, dataEnd);
        assertArrayEquals(inputPlaintext, plaintext2);
    }

    @Test
    void testIvNotReused() {
        // Encrypt once, get IV
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));
        int firstSpi = ByteArrayUtils.decodeUnsignedShort(frame, dataStart - SdlsSecurityAssociation.GCM_IV_LEN_BYTES - 2);
        byte[] firstIv = Arrays.copyOfRange(frame, dataStart - SdlsSecurityAssociation.GCM_IV_LEN_BYTES, dataStart);

        // Encrypt again, get IV (we can use the already encrypted data, the actual content doesn't matter in this test)
        assertDoesNotThrow(() -> sa.applySecurity(frame, 0, dataStart, frame.length));
        int secondSpi = ByteArrayUtils.decodeUnsignedShort(frame, dataStart - SdlsSecurityAssociation.GCM_IV_LEN_BYTES - 2);
        byte[] secondIv = Arrays.copyOfRange(frame, dataStart - SdlsSecurityAssociation.GCM_IV_LEN_BYTES, dataStart);

        // Check that the SPI is the same, but the IV changes
        assertEquals(firstSpi, secondSpi);
        assertFalse(Arrays.equals(firstIv, secondIv));
    }
}
