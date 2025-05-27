package org.yamcs.security;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.ByteArrayUtils;

/**
 * A Security Association for SDLS encryption/decryption (CCSDS 355.0-B-2).
 * <p>
 * Deviates from the baseline in the following ways:
 * - This class is hard-coded to use AES-256-GCM as its underlying cipher suite.
 * - It uses authenticated encryption for all frame types.
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

    /**
     * Security parameter index: identifier shared between sender and receiver, specifies which security association is
     * used
     */
    public final short spi;

    /**
     * Anti-replay sequence number
     */
    private BigInteger seqNum;

    /**
     * Whether to verify the received anti-replay sequence number
     */
    private final boolean verifySeqNum;

    /**
     * Anti-replay sequence number window. Specifies the range of sequence number around the current number that will be
     * accepted.
     */
    final int seqNumWindow;

    /**
     * Secret key used for encryption and decryption
     */
    private SecretKey secretKey;

    private static final Logger log = LoggerFactory.getLogger(SdlsSecurityAssociation.class);

    /**
     * @param key          the 256-bit key used for encryption/decryption
     * @param spi          the security parameter index, shared between sender and receiver.
     * @param seqNumWindow a positive integer; only frames whose sequence number differs by this integer at maximum will
     *                     be accepted.
     * @param verifySeqNum whether to verify the received anti-replay sequence number based on the seqNumWindow.
     */
    public SdlsSecurityAssociation(byte[] key, short spi, int seqNumWindow, boolean verifySeqNum) {
        this.secretKey = new SecretKeySpec(key, secretKeyAlgorithm);
        this.seqNum = BigInteger.valueOf(0);
        this.seqNumWindow = Math.abs(seqNumWindow); // just to ensure it's not negative
        this.spi = spi;
        this.verifySeqNum = verifySeqNum;
    }

    byte[] getSeqNumIvBytes() {
        byte[] arr = new byte[GCM_IV_LEN_BYTES];
        byte[] bigintBytes = seqNum.toByteArray();
        int srcPos = Math.max(bigintBytes.length - GCM_IV_LEN_BYTES, 0);
        int dstPos = Math.max(GCM_IV_LEN_BYTES - bigintBytes.length, 0);
        int length = bigintBytes.length - srcPos;
        System.arraycopy(bigintBytes, srcPos, arr, dstPos, length);
        return arr;
    }

    BigInteger seqNumMax() {
        int maxBits = GCM_IV_LEN_BYTES * 8;
        return BigInteger.valueOf(2).pow(maxBits).subtract(BigInteger.ONE);
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
        // 16-bit SPI + size of IV
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
        authMaskFull[secAuthMaskStart] = (byte) 0xff;
        authMaskFull[secAuthMaskStart + 1] = (byte) 0xff;

        // Set final authMask for primary + sec header
        return authMaskFull;
    }

    void incSeqNum() {
        // Increment
        seqNum = seqNum.add(BigInteger.ONE);

        // Restrict to maximum value, wrap around
        if (seqNum.compareTo(seqNumMax()) > 0) {
            seqNum = BigInteger.ZERO;
        }
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
     * @throws GeneralSecurityException if encryption fails
     */
    public void applySecurity(byte[] transferFrame, int frameStart, int dataStart, int secTrailerEnd,
                              byte[] partialAuthMask) throws GeneralSecurityException {
        // IV must never be re-used with same key for AES-GCM, so we generate a random
        // one for every encryption.
        byte[] iv = getSeqNumIvBytes();
        assert iv.length == GCM_IV_LEN_BYTES: "IV legth should be " + GCM_IV_LEN_BYTES + " but is " + iv.length;

        // Fill security header
        // first two bytes are SPI
        int secHeaderStart = dataStart - getHeaderSize();
        ByteArrayUtils.encodeUnsignedShort(spi, transferFrame, secHeaderStart);
        // the next are IV
        System.arraycopy(iv, 0, transferFrame, secHeaderStart + 2, iv.length);
        // and increment the sequence number
        incSeqNum();

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

        // cipherText now contains plaintext.length + GCM_IV_LEN_BYTES data
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

        /**
         * The sequence number was outside the acceptable window.
         */
        AntiReplaySequenceNumberFailure,
        // This code is not used; AES-GCM does not require padding (see CCSDS
        // 355.0-B-2).
        // PaddingError,
    }

    /**
     * Validate a received sequence number, accounting for rollover.
     *
     * @param receivedSeqNum the sequence number to validate
     * @return whether or not the sequence number is valid, accounting for rollover
     */
    public boolean seqNumValid(BigInteger receivedSeqNum) {
        if (!verifySeqNum) {
            return true;
        }
        BigInteger maxSeqNumInWindow = seqNum.add(BigInteger.valueOf(seqNumWindow));
        if (maxSeqNumInWindow.compareTo(seqNumMax()) <= 0) {
            // Wrap-around not possible yet
            // Verify normally: fail if receive lower than current, or greater than max
            if (receivedSeqNum.compareTo(seqNum) < 0 || receivedSeqNum.compareTo(maxSeqNumInWindow) > 0) {
                log.warn("Received sequence number {} outside of range [{}..{}]", receivedSeqNum, seqNum,
                        maxSeqNumInWindow);
                return false;
            }
        } else {
            // Wrap-around possible
            // Either sequence number can be between the current number and the maximum representable number, inclusive
            boolean seqNumInHighRange =
                    receivedSeqNum.compareTo(seqNum) >= 0 && receivedSeqNum.compareTo(seqNumMax()) <= 0;

            // Or from zero to whatever is left over in the window
            // e.g. max is 255, current is 253. we receive 254, window is 10. 253+10=263, 263-255=8.
            //      then we can accept [253..255] and [0..6].
            BigInteger remainingWindow =
                    seqNum.add(BigInteger.valueOf(seqNumWindow)).subtract(seqNumMax()).subtract(BigInteger.ONE);
            boolean seqNumInLowRange =
                    receivedSeqNum.compareTo(BigInteger.ZERO) >= 0 && receivedSeqNum.compareTo(remainingWindow) < 0;
            if (!seqNumInHighRange && !seqNumInLowRange) {
                log.warn("Received sequence number {} outside of range [{}..{}, 0..{}]", receivedSeqNum,
                        seqNum,
                        Integer.MAX_VALUE, remainingWindow);
                return false;
            }

        }
        return true;
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
    public VerificationStatusCode processSecurity(byte[] transferFrame, int frameStart, int dataStart,
                                                  int secTrailerEnd,
                                                  byte[] partialAuthMask) {
        // Read security header
        // first two bytes are SPI
        int secHeaderStart = dataStart - getHeaderSize();
        short receivedSpi = (short) ByteArrayUtils.decodeUnsignedShort(transferFrame, secHeaderStart);

        // Check that the received SPI is the SPI for this SA
        if (receivedSpi != spi) {
            log.warn("Expected SPI {}, received SPI {}", spi, receivedSpi);
            return VerificationStatusCode.InvalidSPI;
        }

        // Next bytes of the header are IV
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
        byte[] plaintext;
        try {
            plaintext = cipher.doFinal(transferFrame, dataStart, secTrailerEnd - dataStart);
        } catch (AEADBadTagException e) {
            return VerificationStatusCode.MacVerificationFailure;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            return VerificationStatusCode.DecryptionFailed;
        }

        // Copy the decrypted plaintext back into the frame
        System.arraycopy(plaintext, 0, transferFrame, dataStart, plaintext.length);

        // Check the sequence number
        BigInteger receivedSeqNum = new BigInteger(1, receivedIv);

        if (!seqNumValid(receivedSeqNum)) {
            return VerificationStatusCode.AntiReplaySequenceNumberFailure;
        }
        seqNum = receivedSeqNum;

        // Zero the sec header and sec trailer
        Arrays.fill(transferFrame, dataStart - getHeaderSize(), dataStart, (byte) 0);
        Arrays.fill(transferFrame, secTrailerEnd - getTrailerSize(), secTrailerEnd, (byte) 0);

        log.debug("Processed security SPI {}, seq num {}", receivedSpi, seqNum);
        return VerificationStatusCode.NoFailure;
    }

    /* Methods used by HTTP API */

    /**
     * Update the secret key
     *
     * @param secretKey a 256-bit key to be used by AES-GCM
     */
    public void setSecretKey(byte[] secretKey) {
        this.secretKey = new SecretKeySpec(secretKey, secretKeyAlgorithm);
    }

    /**
     * Reset the anti-replay sequence number
     */
    public void resetSeqNum() {
        this.seqNum = BigInteger.ZERO;
    }

    /**
     * Get the current sequence number
     *
     * @return the current sequence number
     */
    public byte[] getSeqNum() {
        return getSeqNumIvBytes();
    }

}