package org.yamcs.security.encryption.aes;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import org.yamcs.YConfiguration;
import org.yamcs.security.encryption.SymmetricEncryption;
import org.yamcs.utils.StringConverter;


public class GCM implements SymmetricEncryption {
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String FACTORY_INSTANCE = "PBKDF2WithHmacSHA512";
    private static final int ITERATIONS = 65535;

    private static final int TAG_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 32;

    String key;
    boolean useSalt;
    byte[] aad;

    @Override
    public void init(YConfiguration config) {
        this.key = config.getString("key");
        this.useSalt = config.getBoolean("useSalt");
        if (config.containsKey("associatedData"))
            this.aad = config.getBinary("associatedData");
    }

    public byte[] encrypt(byte[] plainMessage)
            throws NoSuchAlgorithmException,InvalidKeySpecException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        byte[] salt = useSalt? getRandomNonce(SALT_LENGTH): new byte[0];
        byte[] iv = getRandomNonce(IV_LENGTH);

        SecretKey secretKey = getSecretKey(key, salt);
        Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, secretKey, iv);
        if (aad != null)
            cipher.updateAAD(aad);

        byte[] encryptedMessage = cipher.doFinal(plainMessage);

        // Add IV to the beginning
        return ByteBuffer.allocate(salt.length + iv.length + encryptedMessage.length)
                .put(salt)
                .put(iv)
                .put(encryptedMessage)
                .array();
    }

    public byte[] getRandomNonce(int length) {
        byte[] nonce = new byte[length];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private Cipher initCipher(int mode, SecretKey secretKey, byte[] iv) 
            throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(mode, secretKey, new GCMParameterSpec(TAG_LENGTH * 8, iv));
        return cipher;
    }

    public SecretKey getSecretKey(String key, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (salt.length != 0) {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(FACTORY_INSTANCE);
            KeySpec spec = new PBEKeySpec(key.toCharArray(), salt, ITERATIONS, KEY_LENGTH * 8);
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        }

        return new SecretKeySpec(StringConverter.hexStringToArray(key), "AES");
    }

    @Override
    public byte[] decrypt(byte[] cipherContent) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException  {
        ByteBuffer byteBuffer = ByteBuffer.wrap(cipherContent);

        byte[] salt = new byte[0];
        if (useSalt) {
            salt = new byte[SALT_LENGTH];
            byteBuffer.get(salt);
        }

        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        byte[] content = new byte[byteBuffer.remaining()];
        byteBuffer.get(content);

        SecretKey secretKey = getSecretKey(key, salt);
        Cipher cipher = initCipher(Cipher.DECRYPT_MODE, secretKey, iv);
        if (aad != null)
            cipher.updateAAD(aad);

        return cipher.doFinal(content);
    }

    @Override
    public int getTagLength() {
        return TAG_LENGTH;
    }

    @Override
    public int getIVLength() {
        return IV_LENGTH;
    }
}
