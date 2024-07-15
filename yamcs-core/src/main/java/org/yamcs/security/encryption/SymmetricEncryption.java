package org.yamcs.security.encryption;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.yamcs.YConfiguration;

public interface SymmetricEncryption {
    public byte[] encrypt(byte[] plainText)
        throws NoSuchAlgorithmException,InvalidKeySpecException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException;
    public byte[] decrypt(byte[] encryptedText) throws Exception;

    /**
     * @param config
     *            - the configuration - cannot be null (but can be empty)
     */
    default void init(YConfiguration config) {
    }

    public int getTagLength();

    public int getIVLength();
}