package org.yamcs.security.sdls;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.security.sdls.SdlsSecurityAssociation.VerificationStatusCode;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

public class SecurityAssociationAes256Gcm128Test {
    final Path RESOURCE_DIR = Paths.get("src", "test", "resources", "sdls");
    byte[] key, authMask, frame, inputPlaintext, inputPrimaryHeader;
    int dataStart, dataEnd, seqNumWindow;
    final int secHeaderStart = 6;
    SdlsSecurityAssociation saSend, saRecv;

    @BeforeEach
    void prepareFrame() throws IOException {
        // Load the key
        Path keypath = RESOURCE_DIR.resolve("presharedkey");
        this.key = Files.readAllBytes(keypath);

        // Create SA
        saSend = new SecurityAssociationAes256Gcm128(key, (short) 42, seqNumWindow, true);
        saRecv = new SecurityAssociationAes256Gcm128(key, (short) 42, seqNumWindow, true);

        // We have an example frame of length:
        int frameLength = 42;
        frame = new byte[frameLength];
        // Header size is 6 for primary header + security header size
        // So data start is after the headers
        dataStart = 6 + saSend.getHeaderSize();
        // And data end is before security trailer
        dataEnd = frameLength - saSend.getTrailerSize();

        authMask = StandardAuthMask.TM(0, saSend.securityHdrAuthMask());

        // Create the primary header: all 1s except the field to be authenticated
        inputPrimaryHeader = new byte[6];
        Arrays.fill(inputPrimaryHeader, 0, 6, (byte) 1);
        inputPrimaryHeader[1] = 0b0000_1000; // make the virtual channel ID = 4
        System.arraycopy(inputPrimaryHeader, 0, frame, 0, inputPrimaryHeader.length);

        // Create some plaintext
        inputPlaintext = new byte[dataEnd - dataStart];
        assertEquals(frameLength - 6 - saSend.getOverheadBytes(), inputPlaintext.length);
        Arrays.fill(inputPlaintext, (byte) 69);
        // new Random().nextBytes(inputPlaintext);
        System.arraycopy(inputPlaintext, 0, frame, dataStart, inputPlaintext.length);

        seqNumWindow = 5;

        // saSend starts from 0, saRecv is supposed to have the last number
        saRecv.setSeqNum(StringConverter.hexStringToArray("FFFFFFFFFFFFFFFFFFFFFFFF"));
    }

    @Test
    void givenWrongKey_failDecrypt() throws IOException {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Try to decrypt with a wrong key, and fail
        Path wrongKeypath = RESOURCE_DIR.resolve("wrong-presharedkey");
        byte[] wrongKey = Files.readAllBytes(wrongKeypath);
        SdlsSecurityAssociation wrongSa = new SecurityAssociationAes256Gcm128(wrongKey, (short) 42,
                seqNumWindow, true);
        assertEquals(VerificationStatusCode.MacVerificationFailure, wrongSa.processSecurity(frame, 0, secHeaderStart,
                frame.length, authMask));
    }

    @Test
    void givenWrongSpi_failVerify() throws IOException {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Try to decrypt with a wrong SPI
        Path wrongKeypath = RESOURCE_DIR.resolve("presharedkey");
        byte[] wrongKey = Files.readAllBytes(wrongKeypath);
        SdlsSecurityAssociation wrongSa = new SecurityAssociationAes256Gcm128(wrongKey, (short) 1,
                seqNumWindow, true);
        assertEquals(VerificationStatusCode.InvalidSPI, wrongSa.processSecurity(frame, 0, secHeaderStart, frame.length,
                authMask));
    }

    @Test
    void whenBitFlipPrimaryHeaderAuthdField_failAuth() {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Bitflip VCID of primary header
        int vcid_octet_frame_primary_header = 1;
        frame[vcid_octet_frame_primary_header] = 0b0000_1100;

        assertEquals(VerificationStatusCode.MacVerificationFailure, saRecv.processSecurity(frame, 0, secHeaderStart,
                frame.length, authMask));
    }

    @Test
    void whenBitFlipSecurityHeader_failVerifySPI() {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Bit-flip SPID of security header
        int spid_octet_sec_header = 6;
        byte oldValue = frame[spid_octet_sec_header];
        frame[spid_octet_sec_header] = (byte) (oldValue ^ 1);

        assertEquals(VerificationStatusCode.InvalidSPI, saRecv.processSecurity(frame, 0, secHeaderStart, frame.length,
                authMask));
    }

    @Test
    void whenBitFlipNonAuthData_succeed() {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Master channel frame count is explicitly excluded from authentication in standard
        // Bitflip shouldn't cause a problem
        int mc_frcnt_octet_fph = 2;
        // Need to make a copy here because decryption will succeed
        byte oldValue = frame[mc_frcnt_octet_fph];
        frame[mc_frcnt_octet_fph] = (byte) (oldValue ^ 1);
        VerificationStatusCode result = saRecv.processSecurity(frame, 0, secHeaderStart, frame.length,
                authMask);
        assertEquals(VerificationStatusCode.NoFailure, result);

        // Check the decrypted data matches the original input
        byte[] plaintext2 = Arrays.copyOfRange(frame, dataStart, dataEnd);
        assertArrayEquals(inputPlaintext, plaintext2);
    }

    @Test
    void whenWrongSecurityTag_failAuth() {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Mess with the security tag and fail
        int security_tag_first_byte = dataEnd + 1;
        byte oldValue = frame[security_tag_first_byte];
        frame[security_tag_first_byte] = (byte) (oldValue ^ 1);

        assertEquals(VerificationStatusCode.MacVerificationFailure, saRecv.processSecurity(frame, 0, secHeaderStart,
                frame.length, authMask));
    }

    @Test
    void whenCorruptedCipherText_failAuth() {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Mess with the ciphertext and fail
        int second_ciphertext_byte = dataStart + 1;
        byte oldValue = frame[second_ciphertext_byte];
        frame[second_ciphertext_byte] = (byte) (oldValue ^ 1);

        assertEquals(VerificationStatusCode.MacVerificationFailure, saRecv.processSecurity(frame, 0, secHeaderStart,
                frame.length, authMask));
    }

    @Test
    void testEncryptDecrypt() {
        // Encrypt frame
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        // Check the data is encrypted
        byte[] ciphertext = Arrays.copyOfRange(frame, dataStart, dataEnd);
        assertFalse(Arrays.equals(inputPlaintext, ciphertext));

        // Decrypt frame
        assertEquals(VerificationStatusCode.NoFailure, saRecv.processSecurity(frame, 0, secHeaderStart, frame.length,
                authMask));

        // Check the decrypted data matches the original input
        byte[] plaintext2 = Arrays.copyOfRange(frame, dataStart, dataEnd);
        assertArrayEquals(inputPlaintext, plaintext2);
    }

    @Test
    void testIvNotReused() {
        // Encrypt once, get IV
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));

        int firstSpi = ByteArrayUtils.decodeUnsignedShort(frame, secHeaderStart);
        byte[] firstIv = Arrays.copyOfRange(frame, secHeaderStart + 2,
                secHeaderStart + saRecv.getHeaderSize());

        // Encrypt again, get IV (we can use the already encrypted data, the actual content doesn't matter in this test)
        assertDoesNotThrow(() -> saSend.applySecurity(frame, 0, secHeaderStart, frame.length, authMask));
        int secondSpi = ByteArrayUtils.decodeUnsignedShort(frame, secHeaderStart);
        byte[] secondIv = Arrays.copyOfRange(frame, secHeaderStart + 2,
                secHeaderStart + saRecv.getHeaderSize());

        // Check that the SPI is the same, but the IV changes
        assertEquals(firstSpi, secondSpi);
        assertFalse(Arrays.equals(firstIv, secondIv));
    }
}