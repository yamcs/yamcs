package org.yamcs.security;


import org.yamcs.utils.ByteArrayUtils;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

public class SdlsSecurityAssociation {
    public static final String cipherName = "AES/GCM/NoPadding";
    public static final String secretKeyAlgorithm = "AES";
    public static final int GCM_TAG_LEN_BITS = 128; // baseline SDLS
    public static final int GCM_IV_LEN_BYTES = 12; // OWASP recommendation & baseline SDLS
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] authMask;
    private final short spi;
    private final SecretKey secretKey;

    public SdlsSecurityAssociation(byte[] key, short spi, byte[] primaryAuthMask) {
        this.secretKey = new SecretKeySpec(key, secretKeyAlgorithm);

        byte[] authMaskFull = new byte[primaryAuthMask.length + getHeaderSize()];
        System.arraycopy(primaryAuthMask, 0, authMaskFull, 0, primaryAuthMask.length);

        // Add a mask for the security header
        int secAuthMaskStart = primaryAuthMask.length;
        // We want to authenticate the SPI field (first 16 bits)
        authMaskFull[secAuthMaskStart] = 1;
        authMaskFull[secAuthMaskStart + 1] = 1;

        // Create final authMask for primary + sec header
        this.authMask = authMaskFull;

        // TODO: create a list of SAs at some level above links. Maps SPIs to SAs.
        //   The SPI allows multiple SAs to be used on a single link, because SPI uniquely identifies SA applicable to a given frame.
        //   Maybe as part of MasterChannelFrameHandler? And pass it as parameter to decoder interface? SPI should be set in config file
        //   Define list of SAs in config for link, then define one per channel. Like is in config now
        this.spi = spi;
    }

    public static int getOverheadBytes() {
        return getHeaderSize() + getTrailerSize();
    }

    // Security header for AES-GCM-128
    public static int getHeaderSize() {
        // 16-bit SPI + size of IV. no seq number or padding for AES-GCM
        return 2 + GCM_IV_LEN_BYTES;
    }

    public static int getTrailerSize() {
        return (GCM_TAG_LEN_BITS / 8);
    }


    /**
     * @param transferFrame The full transfer frame, including empty security header and trailer
     * @param dataStart     First byte of frame data
     * @param secTrailerEnd First byte following security trailer
     * @throws GeneralSecurityException
     */
    public void applySecurity(byte[] transferFrame, int frameStart, int dataStart, int secTrailerEnd) throws GeneralSecurityException {
        // Size of all headers
        int headersSize = dataStart - frameStart;

        // IV must never be re-used with same key for AES-GCM
        byte[] iv = new byte[GCM_IV_LEN_BYTES];
        secureRandom.nextBytes(iv);

        // Fill security header
        // first two bytes are SPI
        int secHeaderStart = dataStart - getHeaderSize();
        ByteArrayUtils.encodeUnsignedShort(spi, transferFrame, secHeaderStart);
        // the rest is IV
        System.arraycopy(iv, 0, transferFrame, secHeaderStart + 2, iv.length);

        // create data to authenticate by masking frame headers with authMask
        byte[] aad = new byte[headersSize];
        for (int i = 0; i < dataStart; ++i) {
            aad[i] = (byte) (transferFrame[frameStart + i] & authMask[i]);
        }

        int plaintextSize = secTrailerEnd - getTrailerSize() - dataStart;
        byte[] plaintext = new byte[plaintextSize];
        System.arraycopy(transferFrame, dataStart, plaintext, 0, plaintextSize);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LEN_BITS, iv);
        final Cipher cipher = Cipher.getInstance(cipherName);

        cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, parameterSpec);
        cipher.updateAAD(aad);

        // cipherText contains plaintext.length + GCM_IV_LEN_BYTES data
        // cipherText is [encrypted data | security trailer (MAC)]
        byte[] cipherText = cipher.doFinal(plaintext);
        assert cipherText.length == secTrailerEnd - dataStart;

        // copy it back into the frame, overwriting data & empty trailer with actual MAC
        System.arraycopy(cipherText, 0, transferFrame, dataStart, cipherText.length);

    }

    public enum VerificationStatusCode {
        NoFailure,
        InvalidSPI,
        MacVerificationFailure,
        NoSuchCipher,
        InvalidCipherKey,
        InvalidCipherParam,
        DecryptionFailed,

        // TODO: [CITE] No additional sequence number needed for AES-GCM
        // AntiReplaySequenceNumberFailure,
        // TODO: [CITE] No padding used in AES-GCM
        // PaddingError,
    }

    /**
     * @param transferFrame the entire transfer frame
     * @param dataStart     first byte of frame data
     * @param secTrailerEnd first byte after the security trailer
     * @return
     */
    public VerificationStatusCode processSecurity(byte[] transferFrame, int frameStart, int dataStart, int secTrailerEnd) {
        // Size of all headers
        int headersSize = dataStart - frameStart;

        // Read security header
        // first two bytes are SPI
        int secHeaderStart = dataStart - getHeaderSize();
        short receivedSpi = (short) ByteArrayUtils.decodeUnsignedShort(transferFrame, secHeaderStart);
        if (receivedSpi != spi) {
            return VerificationStatusCode.InvalidSPI;
        }

        // the rest is IV
        byte[] receivedIv = new byte[GCM_IV_LEN_BYTES];
        System.arraycopy(transferFrame, secHeaderStart + 2, receivedIv, 0, GCM_IV_LEN_BYTES);

        // create data to authenticate by masking frame headers with authMask
        byte[] aad = new byte[headersSize];
        for (int i = 0; i < dataStart; ++i) {
            aad[i] = (byte) (transferFrame[frameStart + i] & authMask[i]);
        }

        final Cipher cipher;
        try {
            cipher = Cipher.getInstance(cipherName);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            return VerificationStatusCode.NoSuchCipher;
        }

        AlgorithmParameterSpec gcmIv = new GCMParameterSpec(GCM_TAG_LEN_BITS, receivedIv, 0, GCM_IV_LEN_BYTES);
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
        } catch (InvalidKeyException e) {
            return VerificationStatusCode.InvalidCipherKey;
        } catch (InvalidAlgorithmParameterException e) {
            return VerificationStatusCode.InvalidCipherParam;
        }
        cipher.updateAAD(aad);

        byte[] plaintext = null;
        try {
            plaintext = cipher.doFinal(transferFrame, dataStart, secTrailerEnd - dataStart);
        } catch (AEADBadTagException e) {
            return VerificationStatusCode.MacVerificationFailure;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            return VerificationStatusCode.DecryptionFailed;
        }
        System.arraycopy(plaintext, 0, transferFrame, dataStart, plaintext.length);

        // Zero the sec header and sec trailer
        Arrays.fill(transferFrame, dataStart - getHeaderSize(), dataStart, (byte) 0);
        Arrays.fill(transferFrame, secTrailerEnd - getTrailerSize(), secTrailerEnd, (byte) 0);

        return VerificationStatusCode.NoFailure;
    }
}
