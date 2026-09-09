Composable CCSDS Frame Links
============================

``ComposedTcFrameLink`` and ``ComposedTmFrameLink`` separate CCSDS frame processing from outer framing, physical-channel coding, and transport. The components are internal to one user-visible link; virtual-channel handlers remain its Yamcs sublinks.

The processing order is fixed. TC applies CCSDS framing and SDLS, outer framing, channel coding, and transport in that order. TM applies those operations in reverse. An omitted optional component is disabled.

Named components and modes
--------------------------

Component definitions are reusable. A mode supplies one complete set of component references for each composition:

.. code-block:: yaml

    dataLinkComposition:
      selectedMode: "${env.MCS_DATALINK_MODE:nominal}"

      components:
        tc-protocol:
          class: org.yamcs.tctm.ccsds.CcsdsTcFrameProtocol
          args:
            spacecraftId: 1
            maxFrameLength: 1024
            errorDetection: CRC16
            virtualChannels:
              - vcId: 0
                service: PACKET
                commandPostprocessorClassName: org.yamcs.pus.PusCommandPostprocessor
                stream: tc_realtime

        tc-sdls:
          class: org.yamcs.tctm.ccsds.CcsdsTcSdlsConfiguration
          args:
            encryption:
              - spi: 1
                class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
                args:
                  keyFile: etc/sdls-presharedkey-256bit
            virtualChannels:
              - vcId: 0
                encryptionSpi: 1

        tc-srs4:
          class: org.yamcs.tctm.ccsds.srs4.Srs4TcFrameEncapsulator
          args:
            srs4:
              radio:
                spacecraftId: 0xABCD
              ipv4Udp:
                sourceAddress: 192.0.2.1
                sourcePort: 10000
              virtualChannels:
                - vcId: 0
                  ipv4Udp:
                    destinationAddress: 192.0.2.2
                    destinationPort: 11000

        tc-cltu:
          class: org.yamcs.tctm.ccsds.CcsdsCltuEncoder
          args:
            cltuEncoding: BCH

        tc-radio-udp:
          class: org.yamcs.tctm.UdpTcFrameTransport
          args:
            host: radio.example
            port: 10058

        tc-checkout-udp:
          class: org.yamcs.tctm.UdpTcFrameTransport
          args:
            host: checkout.example
            port: 10058

        tm-protocol:
          class: org.yamcs.tctm.ccsds.CcsdsTmFrameProtocol
          args:
            frameType: TM
            spacecraftId: 1
            # Five interleaved RS(255, 223) blocks decode to 1115 bytes. The
            # IPv4/UDP SRS4 envelope occupies 32 bytes, leaving 1083 CCSDS bytes.
            frameLength: 1083
            errorDetection: CRC16
            goodFrameStream: good_frame_stream
            badFrameStream: bad_frame_stream
            virtualChannels:
              - vcId: 0
                service: PACKET
                ocfPresent: false
                maxPacketLength: 2048
                packetPreprocessorClassName: org.yamcs.pus.PusPacketPreprocessor
                packetPreprocessorArgs:
                  pktTimeOffset: 13
                  timePktTimeOffset: 7
                  timeEncoding:
                    type: CUC
                    epoch: NONE
                stream: tm_realtime

        tm-sdls:
          class: org.yamcs.tctm.ccsds.CcsdsTmSdlsConfiguration
          args:
            encryption:
              - spi: 1
                class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
                args:
                  keyFile: etc/sdls-presharedkey-256bit
            virtualChannels:
              - vcId: 0
                encryptionSpis: [1]

        tm-srs4:
          class: org.yamcs.tctm.ccsds.srs4.Srs4TmFrameDecapsulator
          args:
            srs4:
              radio:
                spacecraftId: 0xABCD
              # TM fixed fields describe the ground-side destination.
              ipv4Udp:
                destinationAddress: 192.0.2.1
                destinationPort: 10000
              virtualChannels:
                # A source endpoint may identify several candidate VCIDs. The
                # inner CCSDS frame determines which candidate was received.
                - vcIds: [0]
                  ipv4Udp:
                    - sourceAddress: 192.0.2.2
                      sourcePort: 11000

        tm-rs:
          class: org.yamcs.tctm.ccsds.Ccsds131TmChannelDecoder
          args:
            codec: RS
            errorCorrectionCapability: 16
            interleavingDepth: 5
            derandomize: true

        tm-radio-udp:
          class: org.yamcs.tctm.UdpTmFrameTransport
          args:
            port: 10057

        tm-checkout-udp:
          class: org.yamcs.tctm.UdpTmFrameTransport
          args:
            port: 11057

      modes:
        nominal:
          spacecraft-tc:
            protocol: tc-protocol
            protocolSecurity: tc-sdls
            outerFrame: tc-srs4
            channelCoding: tc-cltu
            transport: tc-radio-udp
          spacecraft-tm:
            protocol: tm-protocol
            protocolSecurity: tm-sdls
            outerFrame: tm-srs4
            channelCoding: tm-rs
            transport: tm-radio-udp

        checkout-direct:
          spacecraft-tc:
            protocol: tc-protocol
            transport: tc-checkout-udp
          spacecraft-tm:
            protocol: tm-protocol
            transport: tm-checkout-udp

    dataLinks:
      - name: TC_OUT
        class: org.yamcs.tctm.ccsds.ComposedTcFrameLink
        composition: spacecraft-tc

      - name: TM_IN
        class: org.yamcs.tctm.ccsds.ComposedTmFrameLink
        composition: spacecraft-tm

The ``dataLinkComposition`` section is optional. When present, ``selectedMode``, ``components``, and ``modes`` are all required. A link that names a ``composition`` requires this global section. Every composition requires ``protocol`` and ``transport``. ``protocolSecurity``, ``outerFrame``, and ``channelCoding`` are optional. Mode entries are complete compositions and are not merged with each other. An unknown mode, composition, slot, or component reference prevents instance startup.

In ``nominal`` mode the TM transport receives a channel-coded SRS4 envelope. Reed-Solomon decoding and derandomization produce the outer frame, SRS4 removes its radio and IPv4/UDP headers, and the CCSDS protocol validates and processes the resulting frame. In ``checkout-direct`` mode the checkout endpoint must send one plain 1083-byte CCSDS TM frame per UDP datagram because channel coding, SRS4, and SDLS are all omitted.

Inline components
-----------------

For a composition used by only one link, component definitions may instead be placed directly on that link. This form does not require ``dataLinkComposition``:

.. code-block:: yaml

    dataLinks:
      - name: TM_IN
        class: org.yamcs.tctm.ccsds.ComposedTmFrameLink
        components:
          protocol:
            class: org.yamcs.tctm.ccsds.CcsdsTmFrameProtocol
            args:
              frameType: TM
              spacecraftId: 1
              frameLength: 1083
              errorDetection: CRC16
              virtualChannels:
                - vcId: 0
                  service: PACKET
                  stream: tm_realtime
          transport:
            class: org.yamcs.tctm.UdpTmFrameTransport
            args:
              port: 11057

A composed link uses either a named ``composition`` or inline ``components``, never both. Legacy links also require no ``dataLinkComposition`` section.

Checkout selection
------------------

Yamcs expands ``${env.MCS_DATALINK_MODE}`` from its process environment. Yamcs does not read a :file:`.env` file itself. A launcher, Docker Compose, or systemd unit must export the value before starting Yamcs. Changing modes requires an instance restart.

Disabling SDLS means omitting ``protocolSecurity``. This omits both security associations and VC SPI selections without deleting persisted SDLS sequence state. Disabling SRS4 or channel coding similarly means omitting their slots.

TM validation ownership
-----------------------

A fixed channel decoder output must lie between the configured minimum and maximum CCSDS frame lengths after adding maximum outer-frame overhead to both bounds.
SRS4 then validates and removes only its own radio and bus headers. The resulting inner length is validated by the CCSDS TM, AOS, or USLP decoder.
SRS4 source routing may return several candidate VCIDs; the VCID decoded from the inner CCSDS frame must belong to that set.
