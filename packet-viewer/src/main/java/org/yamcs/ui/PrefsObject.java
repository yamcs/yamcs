package org.yamcs.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.prefs.Preferences;

public class PrefsObject {

    public static void putObject(Preferences prefs, String key, Object o) {
        byte[] raw;
        raw = object2Bytes(o);
        prefs.putByteArray(key, raw);
    }

    public static Object getObject(Preferences prefs, String key) {
        byte[] raw = prefs.getByteArray(key, null);
        if (raw == null) {
            return null;
        }
        Object o = null;
        try {
            o = bytes2Object(raw);
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to decode data for " + key + ": " + e.getMessage());
        }
        return o;
    }

    private static byte[] object2Bytes(Object o) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(o);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object bytes2Object(byte[] raw) throws ClassNotFoundException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(raw);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
