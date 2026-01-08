package org.yamcs.security.sdls;

import java.security.GeneralSecurityException;

/**
 * A Security Association (SA) for applying SDLS operations to frames.
 */
public interface SdlsSecurityAssociation {
    /**
     * @return the number of bytes of overhead used by the SA (frequently, the sum of the size of the security header
     * and the size of the security trailer).
     */
    default int getOverheadBytes() {
        return getHeaderSize() + getTrailerSize();
    }

    /**
     * @return the number of bytes in the security header
     */
    int getHeaderSize();

    /**
     * @return the number of bytes in the security trailer
     */
    int getTrailerSize();

    /**
     * @return the expected length of the key in bits
     */
    int getKeyLenBits();

    /**
     * @return the name of the algorithm used in the Security Association
     */
    String getAlgorithm();

    /**
     * @param buffer the buffer containing the frame, with zero-initialized empty space prepared for the security
     *               header and trailer
     *               according to `getHeaderSize` and `getTrailerSize`
     * @param frameStart the start of the frame as an offset into `buffer`
     * @param secHeaderStart the position of the first byte of the security header as an offset into `buffer`
     * @param secTrailerEnd the position of the first byte after the security trailer as an offset into `buffer`
     * @param authMask the authentication mask to use, covering the whole header and including the mask returned from
     *                `securityHdrAuthMask()`
     * @throws GeneralSecurityException if the security operation fails
     */
    void applySecurity(byte[] buffer, int frameStart, int secHeaderStart, int secTrailerEnd,
                       byte[] authMask) throws GeneralSecurityException;

    /**
     * @param buffer the buffer containing the frame
     * @param frameStart the start of the frame as an offset into `buffer`
     * @param secHeaderStart the position of the first byte of the security header as an offset into `buffer`
     * @param secTrailerEnd the position of the first byte after the security trailer as an offset into `buffer`
     * @param authMask the authentication mask to use, covering the whole header and including the mask returned from
     *                `securityHdrAuthMask()`
     * @return a status code indicating the result of the security operation
     */
    VerificationStatusCode processSecurity(byte[] buffer, int frameStart, int secHeaderStart,
                                           int secTrailerEnd, byte[] authMask);

    /**
     * Configure the Security Association to not verify the sequence number for the next received frame.
     */
    void skipVerifyingNextSeqNum();

    /**
     * Update the secret key used in the SA.
     *
     * @param secretKey the new secret key
     */
    void setSecretKey(byte[] secretKey);

    /**
     * Get the current sequence number.
     *
     * @return the sequence number as bytes.
     */
    byte[] getSeqNum();

    /**
     * Update the sequence number.
     *
     * @param newSeqNum the new sequence number as bytes.
     */
    void setSeqNum(byte[] newSeqNum);

    /**
     * @return the authentication mask to use on the security header
     */
    byte[] securityHdrAuthMask();

    /**
     * The various results of a `processSecurity` operation.
     */
    enum VerificationStatusCode {
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
        /**
         * Padding was incorrect
         */
         PaddingError,
    }

}