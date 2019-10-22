#!/bin/bash

mvn -q -f packet-viewer/pom.xml exec:exec \
    -Dexec.executable="java" \
    -Dexec.args="-classpath etc:%classpath org.yamcs.ui.packetviewer.PacketViewer"

