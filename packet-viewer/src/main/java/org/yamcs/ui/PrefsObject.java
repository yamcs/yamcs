package org.yamcs.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.prefs.Preferences;

public class PrefsObject {

    public static void putObject(Preferences prefs, String key, Object o) {
        byte[] raw;
        try {
            raw = object2Bytes( o );
            prefs.putByteArray(key, raw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Object getObject(Preferences prefs, String key) {
        byte[] raw = prefs.getByteArray(key, null);
        if(raw==null) return null;
        Object o=null;
        try {
            o = bytes2Object( raw );
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return o;
    }
    
    private static byte[] object2Bytes(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( o );
        return baos.toByteArray();
    }


    private static Object bytes2Object(byte[] raw) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream( raw );
        ObjectInputStream ois = new ObjectInputStream( bais );
        return ois.readObject();
    }
}
