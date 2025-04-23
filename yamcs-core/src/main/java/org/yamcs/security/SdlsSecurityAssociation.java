package org.yamcs.security;

import org.yamcs.utils.ByteArrayUtils;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * A Security Association for SDLS encryption/decryption (CCSDS 355.0-B-2). This class is hard-coded to use AES-256-GCM
 * as its underlying cipher suite.
 */
public class SdlsSecurityAssociation {
    /**
     * The cipher and mode that we use for encryption
     */
    public static final String cipherName = "AES/GCM/NoPadding"; // padding not used for GCM
    public static final String secretKeyAlgorithm = "AES";
    /**
     * Authentication tag size
     */
    public static final int GCM_TAG_LEN_BITS = 128; // baseline SDLS
    /**
     * Length of initialization vector for GCM
     */
    public static final int GCM_IV_LEN_BYTES = 12; // OWASP recommendation & baseline SDLS
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Security parameter index: identifier shared between sender and receiver, specifies which security association is
     * used
     */
    private final short spi;
    /**
     * Secret key used for encryption and decryption
     */
    private final SecretKey secretKey;

    /**
     * @param key the 256-bit key used for encryption/decryption
     * @param spi the security parameter index, shared between sender and receiver.
     */
    public SdlsSecurityAssociation(byte[] key, short spi) {
        this.secretKey = new SecretKeySpec(key, secretKeyAlgorithm);

        this.spi = spi;
    }

    /**
     * @return Total overhead of SDLS (header and trailer)
     */
    public static int getOverheadBytes() {
        return getHeaderSize() + getTrailerSize();
    }

    /**
     * @return Size of security header in bytes
     */
    public static int getHeaderSize() {
        // 16-bit SPI + size of IV.
        // no sequence number for AES-GCM - already includes an increasing counter
        // no padding for AES-GCM - it works almost like a stream cipher
        return 2 + GCM_IV_LEN_BYTES;
    }

    /**
     * @return Size of security trailer in bytes
     */
    public static int getTrailerSize() {
        // Just the length of the security tag, as bytes
        return (GCM_TAG_LEN_BITS / 8);
    }

    /**
     * Complete the authentication mask to include the security header
     *
     * @param partialAuthMask The authentication mask as provided by the user (excludes security header).
     * @return The authentication mask, extended to include the security header.
     */
    byte[] completeAuthMask(byte[] partialAuthMask) {
        // Create a new authentication mask that will include the security header
        byte[] authMaskFull = new byte[partialAuthMask.length + getHeaderSize()];
        System.arraycopy(partialAuthMask, 0, authMaskFull, 0, partialAuthMask.length);

        // Add a mask for the security header
        int secAuthMaskStart = partialAuthMask.length;
        // We want to authenticate the SPI field (first 16 bits)
        authMaskFull[secAuthMaskStart] = 1;
        authMaskFull[secAuthMaskStart + 1] = 1;

        // Set final authMask for primary + sec header
        return authMaskFull;
    }

    /**
     * Encrypt the provided trasferFrame and authenticate data.
     *
     * @param transferFrame   The full transfer frame, including empty security header and trailer
     * @param frameStart      The first byte of the frae
     * @param dataStart       First byte of frame data
     * @param secTrailerEnd   First byte following the security trailer
     * @param partialAuthMask Mask to authenticate header data (does not include the security header, this is
     *                        automatically authenticated by the SDLS implementation)
     * @throws GeneralSecurityException
     */
    public void applySecurity(byte[] transferFrame, int frameStart, int dataStart, int secTrailerEnd,
                              byte[] partialAuthMask) throws GeneralSecurityException {
        // Size of all headers
        int headersSize = dataStart - frameStart;

        // IV must never be re-used with same key for AES-GCM, so we generate a random
        // one for every encryption.
        byte[] iv = new byte[GCM_IV_LEN_BYTES];
        secureRandom.nextBytes(iv);

        // Fill security header
        // first two bytes are SPI
        int secHeaderStart = dataStart - getHeaderSize();
        ByteArrayUtils.encodeUnsignedShort(spi, transferFrame, secHeaderStart);
        // the rest is IV
        System.arraycopy(iv, 0, transferFrame, secHeaderStart + 2, iv.length);

        // create data to authenticate by masking frame headers with authMask
        byte[] authMask = completeAuthMask(partialAuthMask);
        byte[] aad = new byte[authMask.length];
        for (int i = 0; i < authMask.length; ++i) {
            aad[i] = (byte) (transferFrame[frameStart + i] & authMask[i]);
        }

        // Copy plaintext to encrypt over to a new array
        int plaintextSize = secTrailerEnd - getTrailerSize() - dataStart;
        byte[] plaintext = new byte[plaintextSize];
        System.arraycopy(transferFrame, dataStart, plaintext, 0, plaintextSize);

        // Create the encryption cipher
        final Cipher cipher = Cipher.getInstance(cipherName);

        // Tell the cipher to use our secret key and parameters
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LEN_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, parameterSpec);

        // Add extra authenticated data
        cipher.updateAAD(aad);

        // Encrypt and authenticate data
        byte[] cipherText = cipher.doFinal(plaintext);

        // cipherText nowc ontains plaintext.length + GCM_IV_LEN_BYTES data
        // cipherText is [encrypted data | security trailer (MAC)]
        assert cipherText.length == secTrailerEnd - dataStart;

        // copy the result back into the frame, overwriting data & empty trailer with
        // actual MAC
        System.arraycopy(cipherText, 0, transferFrame, dataStart, cipherText.length);
    }

    /**
     * The various results of a decryption operation.
     */
    public enum VerificationStatusCode {
        /**
         * Verification and decryption was successful.
         */
        NoFailure,
        /**
         * The received SPI is not known for the channel.
         */
        InvalidSPI,
        /**
         * The authentication tag could not be verified.
         */
        MacVerificationFailure,
        /**
         * The requested cipher is not available.
         */
        NoSuchCipher,
        /**
         * The provided key cannot be used with the configured cipher.
         */
        InvalidCipherKey,
        /**
         * The provided parameters cannot be used with the configured cipher.
         */
        InvalidCipherParam,
        /**
         * Data could not be decrypted.
         */
        DecryptionFailed,

        // This code is not used; AES-GCM does not use an additional sequence number,
        // because
        // the cipher mode already includes an increasing counter (see CCSDS 355.0-B-2).
        // AntiReplaySequenceNumberFailure,
        // This code is not used; AES-GCM does not require padding (see CCSDS
        // 355.0-B-2).
        // PaddingError,
    }

    /**
     * Verify and decrypt a transferFrame.
     *
     * @param transferFrame   the entire transfer frame
     * @param frameStart      the index of the first byte of the transfer frame
     * @param dataStart       index of the first byte of frame data
     * @param secTrailerEnd   index of the first byte after the security trailer
     * @param partialAuthMask Mask to authenticate header data (does not include the security header, this is
     *                        automatically authenticated by the SDLS implementation)
     * @return a code indicating the verification/decryption status
     */
    public VerificationStatusCode processSecurity(byte[] transferFrame, int frameStart, int dataStart, int secTrailerEnd,
                                                  byte[] partialAuthMask) {
        // Size of all headers
        int headersSize = dataStart - frameStart;

        // Read security header
        // first two bytes are SPI
        int secHeaderStart = dataStart - getHeaderSize();
        short receivedSpi = (short) ByteArrayUtils.decodeUnsignedShort(transferFrame, secHeaderStart);

        // Check that the received SPI is the SPI for this SA
        if (receivedSpi != spi) {
            return VerificationStatusCode.InvalidSPI;
        }

        // the rest of the header is IV
        byte[] receivedIv = new byte[GCM_IV_LEN_BYTES];
        System.arraycopy(transferFrame, secHeaderStart + 2, receivedIv, 0, GCM_IV_LEN_BYTES);

        // create data to authenticate by masking frame headers with authMask
        byte[] authMask = completeAuthMask(partialAuthMask);
        byte[] aad = new byte[authMask.length];
        for (int i = 0; i < authMask.length; ++i) {
            aad[i] = (byte) (transferFrame[frameStart + i] & authMask[i]);
        }

        // Initialize the cipher
        final Cipher cipher;
        try {
            cipher = Cipher.getInstance(cipherName);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            return VerificationStatusCode.NoSuchCipher;
        }

        // Configure the cipher with our parameters, the received IV, and our key
        AlgorithmParameterSpec gcmIv = new GCMParameterSpec(GCM_TAG_LEN_BITS, receivedIv, 0, GCM_IV_LEN_BYTES);
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
        } catch (InvalidKeyException e) {
            return VerificationStatusCode.InvalidCipherKey;
        } catch (InvalidAlgorithmParameterException e) {
            return VerificationStatusCode.InvalidCipherParam;
        }

        // Add authenticated data
        cipher.updateAAD(aad);

        // And try to verify & decrypt
        byte[] plaintext = null;
        try {
            plaintext = cipher.doFinal(transferFrame, dataStart, secTrailerEnd - dataStart);
        } catch (AEADBadTagException e) {
            return VerificationStatusCode.MacVerificationFailure;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            return VerificationStatusCode.DecryptionFailed;
        }

        // Copy the decrypted plaintext back into the frame
        System.arraycopy(plaintext, 0, transferFrame, dataStart, plaintext.length);

        // Zero the sec header and sec trailer
        Arrays.fill(transferFrame, dataStart - getHeaderSize(), dataStart, (byte) 0);
        Arrays.fill(transferFrame, secTrailerEnd - getTrailerSize(), secTrailerEnd, (byte) 0);

        return VerificationStatusCode.NoFailure;
    }
}
