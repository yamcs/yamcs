package org.yamcs.security.sdls;

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
import org.yamcs.memento.MementoDb;
import org.yamcs.utils.ByteArrayUtils;

/**
 * A Security Association for SDLS encryption/decryption (CCSDS 355.0-B-2).
 * <p>
 * This class is hard-coded to use AES-256-GCM as its underlying cipher suite.
 * It uses authenticated encryption for all frame types: TC, TM, AOS and USLP.
 * <p>
 * The IV is hard-coded to 12 bytes as per the SDLS standard, and consists of a wrapping around
 * sequence number.
 * <p>
 * <p>
 * According to the CCSDS 355.0-B-2 (SDLS) standard, the transfer frame structure is:
 * <ul>
 * <li>Frame primary header</li>
 * <li>Segment header (for TC) / Secondary header (for TM) /insert zone (for AOS and USLP)</li>
 * <li>Security Header - this class uses a header composed of 2 bytes SPI and 12 bytes IV; IV is a sequence number</li>
 * <li>Frame Data</li>
 * <li>Security Trailer - this class uses 128 bits security trailer representing the authentication tag</li>
 * <li>Frame Trailer composed of OCF and checksum (both optional)</li>
 * </ul>
 * <p>
 * According to the standard, the encryption shall be performed over the Frame Data only
 * <p>
 * Authentication (MAC) shall be computed over:
 * <ul>
 * <li>Frame primary header</li>
 * <li>Segment header (for TC) / Secondary header (for TM)</li>
 * <li>Security Header</li>
 * <li>Frame Data</li>
 * </ul>
 * The MAC shall not be computed over the frame trailer (OCF and checksum) and the insert zone (AOS and USLP). The
 * insert zone shall be masked out.
 * Authentication masks are calculated in {@link StandardAuthMask}.
 * <p>
 * This class uses a {@link Cipher} instance which requires two calls: first to add the so-called "Additional
 * Authenticated Data (AAD)" {@link Cipher#updateAAD(byte[])} and then to add the data to be authenticated and encrypted
 * {@link Cipher#doFinal(byte[])}
 * <p>
 * For authenticated encryption, the AAD data is composed of the frame data from the beginning of the frame until the
 * start of the frame data, masked as per the standard.
 * <p>
 * The data to be encrypted is the frame data following the security header up to and not including the security
 * trailer.
 * <p>
 * The frame data will be encrypted and the authentication tag will be written into the security trailer, following the
 * frame data.
 *
 */
public class SecurityAssociationAes256Gcm128 implements SdlsSecurityAssociation {
    /**
     * The cipher and mode that we use for encryption
     */
    private static final String cipherName = "AES/GCM/NoPadding"; // padding not used for GCM
    private static final String secretKeyAlgorithm = "AES";
    /**
     * Authentication tag size
     */
    private static final int GCM_TAG_LEN_BITS = 128; // baseline SDLS
    /**
     * Length of initialization vector for GCM
     */
    private static final int GCM_IV_LEN_BYTES = 12; // OWASP recommendation & baseline SDLS

    private static final Logger log = LoggerFactory.getLogger(SecurityAssociationAes256Gcm128.class);
    /**
     * Security parameter index: identifier shared between sender and receiver, specifies which security association is
     * used
     */
    private final short spi;
    /**
     * Anti-replay sequence number window. Specifies the range of sequence number around the current number that will be
     * accepted.
     */
    private final int seqNumWindow;
    /**
     * Whether to verify the received anti-replay sequence number
     */
    private final boolean verifySeqNum;
    /**
     * Name of the Yamcs instance, used when persisting the sequence number
     */
    private final String instanceName;
    /**
     * Name of the link, used when persisting the sequence number
     */
    private final String linkName;
    /**
     * Anti-replay sequence number
     */
    private IvSeqNum seqNum;
    /**
     * Secret key used for encryption and decryption
     */
    private SecretKey secretKey;
    /**
     * If true, skips verifying the sequence number for the next frame in `processSecurity`
     */
    private boolean skipVerifyingNextSeqNum = false;

    /**
     * @param key                the 256-bit key used for encryption/decryption
     * @param spi                the security parameter index, shared between sender and receiver.
     * @param seqNumWindow       a positive integer; only frames whose sequence number differs by this integer at
     *                           maximum will be accepted.
     * @param verifySeqNum       whether to verify the received anti-replay sequence number based on the seqNumWindow.
     * @param initialSeqNumBytes if no value is found in the Mememto DB for the initial sequence number, then use this
     *                           one. Can be null.
     */
    public SecurityAssociationAes256Gcm128(String instanceName, String linkName, byte[] key, short spi,
                                           byte[] initialSeqNumBytes, int seqNumWindow, boolean verifySeqNum) {
        this.instanceName = instanceName;
        this.linkName = linkName;
        this.spi = spi;
        this.secretKey = new SecretKeySpec(key, secretKeyAlgorithm);

        // If we have information to retrieve a persisted sequence number, do so
        if (instanceName != null && linkName != null) {
            this.seqNum = loadSeqNum();
        }
        // if we did not find a sequence number used the initial one if set
        if (this.seqNum == null) {
            if (initialSeqNumBytes != null) {
                this.seqNum = IvSeqNum.fromBytes(initialSeqNumBytes, GCM_IV_LEN_BYTES);
            } else {// if not set, just start from 0 but on the downlink do not verify the first frame
                this.seqNum = new IvSeqNum(GCM_IV_LEN_BYTES);
                skipVerifyingNextSeqNum = true;
            }
        }
        this.seqNumWindow = Math.abs(seqNumWindow); // just to ensure it's not negative
        this.verifySeqNum = verifySeqNum;
    }

    // Constructor that skips persistence (e.g. for tests)
    public SecurityAssociationAes256Gcm128(byte[] key, short spi, int seqNumWindow,
                                           boolean verifySeqNum) {
        this(null, null, key, spi, null, seqNumWindow, verifySeqNum);
    }

    /**
     * @return Size of security header in bytes
     */
    @Override
    public int getHeaderSize() {
        // 16-bit SPI + size of IV
        // no padding for AES-GCM - it works almost like a stream cipher
        return 2 + GCM_IV_LEN_BYTES;
    }

    /**
     * @return Size of security trailer in bytes
     */
    @Override
    public int getTrailerSize() {
        // Just the length of the security tag, as bytes
        return (GCM_TAG_LEN_BITS / 8);
    }

    @Override
    public int getKeyLenBits() {
        return 256;
    }

    @Override
    public String getAlgorithm() {
        return "AES-256-GCM-128";
    }

    @Override
    public byte[] securityHdrAuthMask() {
        byte[] authMask = new byte[2 + GCM_IV_LEN_BYTES];
        // Only authenticate SPI
        authMask[0] = (byte) 0xff;
        authMask[1] = (byte) 0xff;
        return authMask;
    }

    /**
     * Compute Additional Authenticated Data (AAD) for GCM encryption/decryption.
     * <p>
     * Applies the mask from the beginning of the frame, for the full length of `authMask`. The `authMask` should not
     * include the data, because it is automatically included in GCM (source: McGrew and Viega, "The Galois/Counter Mode
     * of Operation (GCM)").
     *
     * @param buffer     the frame buffer
     * @param frameStart the start of the frame in the buffer
     * @param authMask   bitmask to apply from `frameStart`. This will be applied in full, so it should be at maximum
     *                   the size of the headers, because data is authenticated automatically. Each byte of the
     *                   authMask is combined with each byte of data using a bitwise AND.
     * @return the AAD bytes
     */
    private static byte[] computeAad(byte[] buffer, int frameStart, byte[] authMask) {
        // Create AAD buffer for auth mask
        byte[] aad = new byte[authMask.length];

        // Apply auth mask to frame
        for (int i = 0; i < authMask.length; ++i) {
            aad[i] = (byte) (buffer[frameStart + i] & authMask[i]);
        }

        return aad;
    }

    /**
     * @return Get the sequence number (IV) as bytes
     */
    byte[] getSeqNumIvBytes() {
        return seqNum.toBytes(GCM_IV_LEN_BYTES);
    }

    /**
     * Load a persisted sequence number, defaulting to zero.
     *
     * @return the sequence number for the next frame to send, or null if not found
     */
    IvSeqNum loadSeqNum() {
        MementoDb mementoDb = MementoDb.getInstance(instanceName);
        return mementoDb.getObject(SdlsMemento.MEMENTO_KEY, SdlsMemento.class)
                .map(memento -> memento.getSeqNum(linkName, spi)).orElse(null);
    }

    /**
     * Save the current sequence number to the database
     */
    void persistSeqNum() {
        MementoDb mementoDb = MementoDb.getInstance(instanceName);
        SdlsMemento memento = mementoDb.getObject(SdlsMemento.MEMENTO_KEY, SdlsMemento.class)
                .orElse(new SdlsMemento());
        memento.saveSeqNum(linkName, spi, seqNum);
        mementoDb.putObject(SdlsMemento.MEMENTO_KEY, memento);
    }

    /**
     * Encrypt the provided trasferFrame and authenticate data.
     * <p>
     * The partialAuthMask has to cover from the beginning of the frame until the start of the security header. If it is
     * larger, the last bytes will not be used.
     *
     * @param buffer         The full transfer frame, including empty security header and trailer
     * @param frameStart     The first byte of the frame in the buffer
     * @param secHeaderStart The offset of the security header
     * @param secTrailerEnd  First byte following the security trailer
     * @throws GeneralSecurityException if encryption fails
     */
    @Override
    public void applySecurity(byte[] buffer, int frameStart, int secHeaderStart, int secTrailerEnd,
                              byte[] authMask) throws GeneralSecurityException {
        // IV must never be re-used with same key for AES-GCM, so we generate a random
        // one for every encryption.
        byte[] iv = getSeqNumIvBytes();

        assert iv.length == GCM_IV_LEN_BYTES : "IV legth should be " + GCM_IV_LEN_BYTES + " but is " + iv.length;

        // Fill security header
        // first two bytes are SPI
        ByteArrayUtils.encodeUnsignedShort(spi, buffer, secHeaderStart);
        // the next are IV
        System.arraycopy(iv, 0, buffer, secHeaderStart + 2, iv.length);
        // and increment the sequence number
        seqNum.increment();

        // Save the next seq num to be sent
        if (instanceName != null && linkName != null)
            persistSeqNum();

        // create data to authenticate by masking frame headers with authMask
        byte[] aad = computeAad(buffer, frameStart, authMask);
        int dataStart = secHeaderStart + getHeaderSize();

        // Create the encryption cipher
        final Cipher cipher = Cipher.getInstance(cipherName);

        // Tell the cipher to use our secret key and parameters
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LEN_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, parameterSpec);

        // Add extra authenticated data
        cipher.updateAAD(aad);

        // Encrypt in-place
        int plaintextSize = secTrailerEnd - getTrailerSize() - dataStart;

        cipher.doFinal(buffer, dataStart, plaintextSize, buffer, dataStart);
    }

    /**
     * Verify and decrypt a transferFrame.
     *
     * <p>
     * This function knows the size of the security header and trailer and those sizes are used to find the data start
     * and data end.
     * <p>
     * For MAC, this function authenticates data in `buffer` from `frameStart`, after applying `authMask` in a
     * bitwise-AND. The length of the authenticated data is equal to the length of the `authMask`.
     *
     * @param buffer         the buffer containing the transfer frame
     * @param frameStart     the index of the first byte of the transfer frame in the buffer
     * @param secHeaderStart index of the first byte of security header
     * @param secTrailerEnd  index of the first byte after the security trailer
     * @param authMask       Mask to authenticate header data (does not include the security header, this is
     *                       automatically authenticated by the SDLS implementation)
     * @return a code indicating the verification/decryption status
     *
     */
    public VerificationStatusCode processSecurity(byte[] buffer, int frameStart, int secHeaderStart,
                                                                                  int secTrailerEnd, byte[] authMask) {
        // Read security header
        // first two bytes are SPI
        short receivedSpi = (short) ByteArrayUtils.decodeUnsignedShort(buffer, secHeaderStart);

        // Check that the received SPI is the SPI for this SA
        if (receivedSpi != spi) {
            log.warn("Expected SPI {}, received SPI {}", spi, receivedSpi);
            return VerificationStatusCode.InvalidSPI;
        }

        // Next bytes of the header are IV
        byte[] receivedIv = new byte[GCM_IV_LEN_BYTES];
        System.arraycopy(buffer, secHeaderStart + 2, receivedIv, 0, GCM_IV_LEN_BYTES);

        byte[] aad = computeAad(buffer, frameStart, authMask);

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

        int dataStart = secHeaderStart + getHeaderSize();
        // And try to verify & decrypt
        byte[] plaintext;
        try {
            plaintext = cipher.doFinal(buffer, dataStart, secTrailerEnd - dataStart);
        } catch (AEADBadTagException e) {
            return VerificationStatusCode.MacVerificationFailure;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            return VerificationStatusCode.DecryptionFailed;
        }

        // Copy the decrypted plaintext back into the frame
        System.arraycopy(plaintext, 0, buffer, dataStart, plaintext.length);

        // Check the sequence number
        IvSeqNum receivedSeqNum = IvSeqNum.fromBytes(receivedIv, GCM_IV_LEN_BYTES);

        if (skipVerifyingNextSeqNum) {
            skipVerifyingNextSeqNum = false;
        } else if (verifySeqNum && !seqNum.verifyInWindow(receivedSeqNum, seqNumWindow)) {
            return VerificationStatusCode.AntiReplaySequenceNumberFailure;
        }
        seqNum = receivedSeqNum;
        // Save the last received seq num
        if (instanceName != null && linkName != null) {
            persistSeqNum();
        }

        // Zero the sec header and sec trailer
        Arrays.fill(buffer, dataStart - getHeaderSize(), dataStart, (byte) 0);
        Arrays.fill(buffer, secTrailerEnd - getTrailerSize(), secTrailerEnd, (byte) 0);

        log.debug("Processed security SPI {}, seq num {}", receivedSpi, seqNum);
        return VerificationStatusCode.NoFailure;
    }

    /**
     * Do not verify the sequence number for the next received frame
     */
    @Override
    public void skipVerifyingNextSeqNum() {
        skipVerifyingNextSeqNum = true;
    }

    /* Methods used by HTTP API */

    /**
     * Update the secret key
     *
     * @param secretKey a 256-bit key to be used by AES-GCM
     */
    @Override
    public void setSecretKey(byte[] secretKey) {
        this.secretKey = new SecretKeySpec(secretKey, secretKeyAlgorithm);
    }

    /**
     * Get the current sequence number
     *
     * @return the current sequence number in big-endian order
     */
    @Override
    public byte[] getSeqNum() {
        return getSeqNumIvBytes();
    }

    /**
     * Reset the anti-replay sequence number
     *
     * @param newSeqNum the bytes of the new sequence number, in big-endian order
     */
    @Override
    public void setSeqNum(byte[] newSeqNum) {
        this.seqNum = IvSeqNum.fromBytes(newSeqNum, GCM_IV_LEN_BYTES);
    }
}