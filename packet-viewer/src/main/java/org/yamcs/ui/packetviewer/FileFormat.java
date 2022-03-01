package org.yamcs.ui.packetviewer;

import java.io.IOException;
import java.io.InputStream;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.PacketInputStream;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.utils.YObjectLoader;

public class FileFormat {

    private String name;

    private String packetInputStreamClassName;
    private YConfiguration packetInputStreamArgs;

    private PacketPreprocessor packetPreprocessor;

    private String rootContainer;

    public FileFormat(String name, String packetInputStreamClassName, YConfiguration packetInputStreamArgs,
            PacketPreprocessor packetPreprocessor) {
        this.name = name;
        this.packetInputStreamClassName = packetInputStreamClassName;
        this.packetInputStreamArgs = packetInputStreamArgs;
        this.packetPreprocessor = packetPreprocessor;
    }

    public String getName() {
        return name;
    }

    public String getRootContainer() {
        return rootContainer;
    }

    public void setRootContainer(String rootContainer) {
        this.rootContainer = rootContainer;
    }

    public PacketInputStream newPacketInputStream(InputStream inputStream) throws IOException {
        PacketInputStream packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName);
        packetInputStream.init(inputStream, packetInputStreamArgs);
        return packetInputStream;
    }

    public PacketPreprocessor getPacketPreprocessor() {
        return packetPreprocessor;
    }
}
