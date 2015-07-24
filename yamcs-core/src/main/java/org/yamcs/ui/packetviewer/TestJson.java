package org.yamcs.ui.packetviewer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class TestJson {
    public  static void main(String[] args) throws Exception {
        JsonFactory jsonFactory = new JsonFactory();
        ArrayList<String> columnParaNames = new ArrayList<String>();
        JsonParser jsp = jsonFactory.createParser("[\"/asdf/asdf/asdfas\", \"asdf\"]");;
        if (jsp.nextToken() != JsonToken.START_ARRAY) {
            return;
        }
        while(jsp.nextToken()==JsonToken.VALUE_STRING) {
            columnParaNames.add(jsp.getValueAsString());
        }
        
        
        System.out.println("got names: "+columnParaNames);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator jsg = jsonFactory.createGenerator(baos);
        jsg.writeStartArray();
        for(String s: columnParaNames) {
            jsg.writeString(s);
        }
        jsg.writeEndArray();
        jsg.close();
        
        System.out.println("got json"+ new String(baos.toByteArray()));
        
        
        
    }
}
