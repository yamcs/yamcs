#!/bin/bash

mvn -q -f packet-viewer/pom.xml exec:exec \
    -Dexec.executable="java" \
    -Dexec.args="-classpath %classpath org.yamcs.ui.packetviewer.PacketViewer"

