CCSDS Frame Processing
======================

This section describes Yamcs support for parts of the following CCSDS specifications:

* TM Space Data Link Protocol `CCSDS 132.0-B-3 <https://public.ccsds.org/Pubs/132x0b3.pdf>`_
* AOS Space Data Link Protocol `CCSDS 732.0-B-4 <https://public.ccsds.org/Pubs/732x0b4.pdf>`_
* TC Space Data Link Protocol `CCSDS 232.0-B-4 <https://public.ccsds.org/Pubs/232x0b4e1c1.pdf>`_
* Unified Space Data Link Protocol `CCSDS 732.1-B-2  <https://public.ccsds.org/Pubs/732x1b3e1.pdf>`_
* TC Synchronization and Channel Coding `CCSDS 231.0-B-4 <https://public.ccsds.org/Pubs/232x0b4e1c1.pdf>`_
* TM Synchronization and Channel Coding `CCSDS 131.0-B-4 <https://public.ccsds.org/Pubs/131x0b5.pdf>`_
* Communications Operation Procedure (COP-1) `CCSDS 232.1-B-2 <https://public.ccsds.org/Pubs/232x1b2e2c1.pdf>`_
* Space Packet Protocol `CCSDS 133.0-B-2 <https://public.ccsds.org/Pubs/133x0b2e2.pdf>`_
* Encapsulation Service `CCSDS 133.1-B-3 <https://public.ccsds.org/Pubs/133x1b3e1.pdf>`_
* Space Data Link Security Protocol `CCSDS 355.0-B-2 <https://ccsds.org/Pubs/355x1b1.pdf>`__

These specifications are dealing with multiplexing and to a certain extent encoding data for transmission on a space link.

The document `Space Data Link Protocols — Summary of Concept and Rationale <https://public.ccsds.org/Pubs/130x2g3.pdf>`_ provides a comprehensive summary of the different protocols and it is recommended to read it before attempting to configure Yamcs to use these protocols.


Telemetry Frame Processing
--------------------------

The CCSDS specifies how to transport data into three types of frames:

* AOS
* TM
* USLP

Yamcs supports to a certain extent all three of them. The main support is around the "packet service" - that is describing how the telemetry packets are extracted from the frames. The implementation is however generic enough (hopefully) such that it is possible to add additional functionality for processing non-packet data (e.g. sending video to external application).

The packets are inserted into frames which are sent as part of Virtual Channels (VC). The VCs can have different priority on-board, for example one VC can be used to transport low volume HK data, while another one to transport high volume science data.

Note that The USLP and TC frames support a second level of multiplexing called Multiplexer Access Point (MAP) which allows multiplexing data inside a VC. The MAP service is only supported for TC, not for USLP.

Currently the built-in way to receive frame data inside Yamcs is by using the UdpTmFrameLink data link. The yamcs-sle project provides an implementation of the Space Link Extension (SLE) which allows receiving frame data from SLE-enabled Ground Stations (such as those from NASA Deep Space Network or :abbr:`ESA (European Space Agency)` :abbr:`ESTRACK (European Space Tracking)`). The options described below are valid for both link types.

An example of a UDP TM frame link specification is below:

.. code-block:: yaml

    - name: UDP_FRAME_IN
      class: org.yamcs.tctm.ccsds.UdpTmFrameLink
      args:
        port: 10017
        rawFrameDecoder:
            codec: RS
            interleavingDepth: 5
            errorCorrectionCapability: 16
            derandomize: false
        frameType: "AOS"
        spacecraftId: 0xAB
        frameLength: 512
        frameHeaderErrorControlPresent: true
        insertZoneLength: 0
        errorDetection: CRC16
        clcwStream: clcw
        goodFrameStream: good_frames
        badFrameStream: bad_frames
        encryption:
          - spi: 1
            class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
            args:
              keyFile: etc/sdls-presharedkey-256bit
              seqNumWindow: 1
          - spi: 2
            class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
            args:
              keyFile: etc/sdls-presharedkey-256bit
              seqNumWindow: 1
              verifySeqNum: false
        virtualChannels:
          - vcId: 0
            encryptionSpis: [1]
            ocfPresent: true
            service: "PACKET"
            maxPacketLength: 2048
            packetPreprocessorClassName: org.yamcs.tctm.IssPacketPreprocessor
            packetPreprocessorArgs:
              [...]
            stream: "tm_realtime"
          - vcId: 1
            encryptionSpis: [1, 2]
            ocfPresent: true
            service: "PACKET"
            maxPacketLength: 2048
            stripEncapsulationHeader: true
            packetPreprocessorClassName: org.yamcs.tctm.GenericPacketPreprocessor
            packetPreprocessorArgs:
              [...]
            stream: "tm2_realtime"
          - vcId: 2
            encryptionSpis: [2]
            ocfPresent: true
            service: "PACKET"
            maxPacketLength: 2048
            packetPreprocessorClassName: org.yamcs.tctm.IssPacketPreprocessor
            stream: "tm_dump"

The following general options are supported:


rawFrameDecoder (map) supported since Yamcs 5.5.7
   Decodes raw frame data using an error correction scheme and/or randomization. For the moment only the Reed-Solomon codec is supported. If this is not set, the frames are considered already decoded. See below for the options to the Reed-Solomon codec.

frameDecapsulation (map)
    Optional provider which removes mission-specific headers after raw-frame decoding and before CCSDS transfer-frame decoding. The map contains a required ``class`` and optional ``args``. See `SRS4 Outer Frame Processing`_ for the built-in SRS4 provider.

frameType (string)
    **Required.** One of ``AOS``, ``TM`` or ``USLP``. The first 2 bits for AOS/TM and 4 bits for USLP represent the version number and have to have the value 0, 1 or 12 respectively. If a frame is received that has a different version, it is discarded (with a warning log message).

derandomize (boolean)
    If true, derandomize the frames with the derandomizer as per CCSDS 131.0-B-4. Default: false

spacecraftId (integer)
    **Required.** The expected spacecraft identifier. The spacecraftId is encoded in the frame header. If a frame with a different identifier is received, it is discarded (with a warning log message).

frameLength (integer)
    The expected frame length. This parameter is mandatory for AOS and TM frames and optional for USLP frames which can have variable length. If a frame is received that does not have this length, it is discarded (with a warning log message).
    For USLP frames, if this parameter is specified, the following two are ignored; Yamcs will use maxFrameLength = minFrameLength = frameLength.

maxFrameLength (integer)
    Used for USLP with variable frame length to specify the maximum length of the frame. This parameter is ignored if the frameLength parameter is also specified.

minFrameLength (integer)
    Used for USLP with variable frame length to specify the minimum length of the frame. This parameter is ignored if the frameLength parameter is also specified.

frameHeaderErrorControlPresent (boolean)
    Used only for AOS frames to specify the presence/absence of the 2 bytes Frame Header Error Control. This can be used to detect and correct errors in parts of the AOS frame headers using a  Reed-Solomon (10,6) code.

insertZoneLength (integer)
    The AOS and USLP frames can optionally use an Insert Service to transfer fixed-length data synchronized with the release of the frames. The insert data follows immediately the frame primary header. If the Insert Service is used, this parameter specifies the length of the insert data. If not used, please set it to 0 (default). For TM frames this parameter is ignored.
    Currently Yamcs ignores any data in the insert zone.

errorDetection (string)
    One of ``NONE``, ``CRC16`` or ``CRC32``. Specifies the error detection scheme used. TM and AOS frames support either NONE or CRC16 while USLP supports NONE, CRC16 or CRC32. If present, the last 2 respectively 4 bytes of the frame will contain an error control field. If the CRC does not match the computation, the frame will be discarded (with a warning message).

clcwStream (string)
    Can be used to specify the name of the stream where the Command Link Control Words (CLCW) will be sent. The CLCW is the mechanism used by COP-1 to acknowledge uplinked frames. For TM and USLP frames, there is an OCF flag part of the frame header indicating the presence or not of the CLCW. For AOS frames it has to be configured with the ``ocfPresent`` flag below.
    If present, the CLCW is also extracted from idle frames (i.e. frames that are inserted when no data needs to be transmitted in order to keep the constant bitrate required for downlink).

goodFrameStream (string)
    If specified, the good frames will be sent on a stream with that name. The stream will be created if it does not exist.

badFrameStream (string)
    If specified, the bad frames will be sent on a stream with that name. Bad frames are considered as those that fail decoding for various reasons: length in the header does not match the size of the data received, frame version does not match, bad CRC, bad spacecraft id, bad vcid.

encryption (list of map)
    If specified, channels on the link can use one of the configured SPIs to encrypt frames.

virtualChannels (map)
    **Required.** Used to specify the Virtual Channel specific configuration.

For each item in the ``encryption`` list, the following parameters can be used:

spi (integer)
    **Required.** Specifies the Security Parameter Index (SPI) that uniquely identifies this encryption key for the link.

class (string)
    **Required.** Specifies the SDLS Security Association implementation to use. Yamcs has AES-256-GCM-128 authenticated encryption built in, which can be selected with ``org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory``. Custom implementations can be loaded as plugins.

args (map)
    Contains configuration options that will be passed to the ``create`` method of the class specified in ``class``. These are different for each implementation.

authMask (string)
    Specify a custom authentication bit mask to apply from the beginning of the frame, as a hex string. The mask is applied as a bitwise AND of the frame and the mask.
    Exactly the number of bytes equal to the length of ``authMask`` are masked from the frame, and the result is used as additional authenticated data (AAD) in an authenticated encryption operation.
    If ``authMask`` is not specified, a default is used according to the SDLS standard (CCSDS 355.0-B-2).
    This option is only useful if you have a static header size.

For each Virtual Channel in the ``virtualChannels`` map, the following parameters can be used:

vcId (integer)
    **Required.** The configured Virtual Channel identifier.

encryptionSpis (list of integer)
    If specified, instructs the virtual channel to use SDLS for frame encryption and authentication, using the specified Security Parameter Indices. The ``encryptionSpis`` must be a subset of the ``spi`` values in the ``encryption`` list for the link. Integers must fit in a two-byte unsigned short (i.e., maximum value 65535).

ocfPresent: (boolean)
    Used for AOS frames to indicate that the Virtual Channel uses the  Operational Control Field (OCF) Service to transport the CLCW containing acknowledgments for the uplinked TC frames. For TM and USLP frames, there is a flag in each frame that indicates the presence or absence of OCF.

service:
    **Required.** This specifies the type of data that is part of the Virtual Channel. One of ``PACKET``, ``IDLE`` or ``VCA``

    PACKET:
       This is used if the data contains packets - it requires the presence of the first header pointer to indicate where in the frame the packet starts. Both CCSDS space packets and CCSDS encapsulation packets are supported (even multiplexed on the same virtual channel). The type of packet is detected based on the first 3 bits of data: 000=CCSDS space packet, 111=encapsulation packets.
       Idle CCSDS space packets (having APID = 0x7FF) and idle encapsulation packets (having first byte = 0x1C) are discarded.
    IDLE:
       Supported for AOS and USLP to indicate that the Virtual Channel contains only idle frames . Normally, the AOS and USLP use the Virtual Channel 63 to transmit idle frames and you do not need to define this virtual channel (in conclusion ``IDLE`` is not very useful). The TM frames have a different mechanism to signal idle frames (first header pointer is 0x7FE).
    VCA:
       VCA stands for Virtual Channel Access - it is  a mechanism for the user to plug a custom handler for the virtual channel data. The ``vcaHandlerClassName`` property has to be defined if this option is specified (see  below).

maxPacketLength:
    **Required if service=PACKET.**  Specifies the maximum size of a packet (header included). Valid for both CCSDS Space Packets and CCSDS encapsulation packets. If the header of a packet indicates a packet size larger than this value, a warning event is raised and the packet is dropped including all the data until a new frame containing a packet start.

packetPreprocessorClassName and packetPreprocessorArgs
    **Required if service=PACKET.** Specifies the packet preprocessor and its configuration that will be used for the packets extracted from this Virtual Channel. See :doc:`packet-preprocessor/index` for details.

vcaHandlerClassName:
    **Required if the service = VCA** Specifies the name of the class which handles data for this virtual channel. The class has to implement :javadoc:`~org.yamcs.tctm.ccsds.VcDownlinkHandler` interface. Optionally it can implement :javadoc:`~org.yamcs.tctm.Link` interface to appear as a data link (e.g. in yamcs-web). An example implementation of such class can be found in the ccsds-frames example project.

*Raw Frame Decoder*

The options which can be selected under the ``rawFrameDecoder`` key are the following:

codec (string)
   **Required.** Specifies the error correction codec to use. Valid values are ``NONE`` and ``RS``. None means the data will not be error corrected (can be still useful if only de-randomization is required).
   RS means the Reed-Solomon codec is used and the errorCorrectionCapability and interleavingDepth below can be used to configure the codec.

interleavingDepth (int)
   The interleaving depth specifies the number of RS decoders running in "parallel" for one frame. Each interleavingDepth'th byte in the frame will be passed to a different decoder. Note however that as of Yamcs 5.5.7, the data is process sequentially not in parallel. Default: 5

errorCorrectionCapability (int)
   This is either 8 or 16 determining the RS(255, 239) respectively RS(255,223) codec to be used. Default: 16

derandomize (boolean)
    If true, the data will be passed through a derandomizer after being decoded. Default: false


Telecommand Frame Processing
----------------------------

Yamcs supports packing telecommand packets into TC Transfer Frames and in addition encapsulating the frames into Communications Link Transmission Unit (CLTU).

Currently the built-in way to send telecommand frames from  Yamcs is by using the UdpTcFrameLink data link. The yamcs-sle project provides an implementation of the Space Link Extension (SLE) which allows sending CLTUs to SLE-enabled Ground Stations. The options described below are valid for both link types.

An example of a UDP TC frame link specification is below:

.. code-block:: yaml

    - name: UDP_FRAME_OUT
      class: org.yamcs.tctm.ccsds.UdpTcFrameLink
      host: localhost
      port: 10018
      spacecraftId: 0xAB
      maxFrameLength: 1024
      cltuEncoding: BCH
      priorityScheme: FIFO
      randomizeCltu: false
      encryption:
        - spi: 1
          class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
          args:
            keyFile: etc/sdls-presharedkey-256bit
            seqNumWindow: 1
        - spi: 2
          class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
          args:
            keyFile: etc/sdls-presharedkey-256bit
            seqNumWindow: 1
        - spi: 3
          class: org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory
          args:
            keyFile: etc/sdls-presharedkey-256bit
            seqNumWindow: 1
      virtualChannels:
          - vcId: 0
            encryptionSpi: 1
            service: "PACKET"
            mapId: 1
            priority: 1
            commandPostprocessorClassName: org.yamcs.tctm.IssCommandPostprocessor
            commandPostprocessorArgs:
              [...]
            stream: "tc_sim"
            useCop1: true
            clcwStream: "clcw"
            initialClcwWait: 3600
            cop1T1: 3
            cop1TxLimit: 3
            slidingWindowWidth: 15
            bdAbsolutePriority: false


The following general options are supported:

spacecraftId (integer)
    **Required.** The spacecraftId is encoded in the TC Transfer Frame primary header.

maxFrameLength (integer)
    **Required.** The maximum length of the frames sent over this link. The Virtual Channel can also specify an option for this but the VC specific maximum frame length has to be smaller or equal than this. Note that since Yamcs does not support segmentation (i.e. splitting a TC packet over multiple frames), this value limits effectively the size of the TC packet that can be sent.

priorityScheme (string)
    One of ``FIFO``, ``ABSOLUTE`` or ``POLLING_VECTOR``. This configures the priority of the different Virtual Channels. The different schemes are described below.

cltuEncoding (string)
    One of ``BCH``, ``LDPC64``, ``LDPC256``, or ``CUSTOM``. If this parameter is present, the TC transfer frames will be encoded into CLTUs and this parameter configures the code to be used. If this parameter is not present, the frames will not be encapsulated into CLTUs and the following related parameters are ignored. If the value is ``CUSTOM``, the CLTU generator class must be specified as indicated below.

cltuStartSequence (string)
    This parameter can optionally set the  CLTU start sequence in hexadecimal if different than the CCSDS specs.

cltuTailSequence (string)
    This parameter can optionally set the CLTU tail sequence in hexadecimal if different than the CCSDS specs.

randomizeCltu (boolean)
    Used if cltuEncoding is BCH or CUSTOM to enable/disable the randomization. For LDPC encoding, randomization is always on.
    Note that as per issue 4 of CCSDS 231.0 (TC Synchronization and Channel Coding), the randomization is done before the encoding when BCH is enabled whereas if LDPC encoding is enabled, the randomization is done after the encoding. This has been changed in Yamcs version 5.5.4 - in versions 5.5.3 and earlier the randomization was always applied before the encoding (as per issue 3 of the CCSDS standard). If CUSTOM CLTU encoding is used, the custom encoder is responsible for the randomization - it can use this option or its own separate option for configuration.

encryption (list of map)
    If specified, channels on the link can use one of the configured SPIs to encrypt frames.

skipRandomizationForVcs (list of integers) added in Yamcs 5.5.6
    If randomizeCltu is true, this option can define a list of virtual channels for which randomization is not performed. This is not as per CCSDS standard which specifies that the randomization is enabled/disabled at the physical channel level.

cltuGeneratorClassName (string)
    **Required if cltuEncoding is CUSTOM.** Specifies the name of the class which constructs the CLTU from the frame, if a custom format is required.

cltuGeneratorArgs
    Optional if cltuEncoding is CUSTOM, ignored otherwise. Arguments to pass to the constructor for the CLTU generator class.

virtualChannels (map)
    **Required.** Used to specify the Virtual Channel specific configuration.

errorDetection (string)
    One of ``NONE`` or ``CRC16``. Specifies the error detection scheme used. If present, the last 2 bytes of the frame will contain an error control field.
    Default: ``CRC16``

frameMaxRate (double)
    maximum number of command frames to send per second. This option is specific to the UDP TC link.

frameEncapsulation (map)
    Optional provider which adds mission-specific headers after CCSDS/SDLS frame construction and before optional CLTU encoding. The map contains a required ``class`` and optional ``args``. See `SRS4 Outer Frame Processing`_ for the built-in SRS4 provider.


For each item in the ``encryption`` list, the following parameters can be used:

spi (integer)
    **Required.** Specifies the Security Parameter Index (SPI) that uniquely identifies this encryption key for the link.

class (string)
    **Required.** Specifies the SDLS Security Association implementation to use. Yamcs has AES-256-GCM-128 authenticated encryption built in, which can be selected with ``org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory``. Custom implementations can be loaded as plugins.

args (map)
    Contains configuration options that will be passed to the ``create`` method of the class specified in ``class``. These are different for each implementation.

authMask (string)
    Specify a custom authentication bit mask to apply from the beginning of the frame, as a hex string. The mask is applied as a bitwise AND of the frame and the mask.
    Exactly the number of bytes equal to the length of ``authMask`` are masked from the frame, and the result is used as additional authenticated data (AAD) in an authenticated encryption operation.
    If ``authMask`` is not specified, a default is used according to the SDLS standard (CCSDS 355.0-B-2).
    This option is only useful if you have a static header size.

For each Virtual Channel in the ``virtualChannels`` map, the following parameters can be used:

vcId (integer)
    **Required.** The Virtual Channel identifier to be used in the frames. You can define multiple entries in the map with the same vcId, if the data is coming from different streams.

encryptionSpi (integer)
    If specified, instructs the virtual channel to encrypt and authenticate frames, using the specified Security Parameter Index. The ``encryptionSpi`` must be one of the ``spi`` values in the ``encryption`` list for the link. Integer must fit in a two-byte unsigned short (i.e., maximum value 65535).

service (string)
    Currently the only supported option is ``PACKET`` which is also the default.

commandPostprocessorClassName (string) and commandPostprocessorArgs (string)
   **Required if service=PACKET.** Specifies the command postprocessor and its configuration. See :doc:`command-postprocessor/index` for details.

stream (string)
     **Required.** The stream on which the commands are received.

multiplePacketsPerFrame (boolean)
    If set to true (default), Yamcs sends multiple command packets in one frame if possible (i.e. if the accumulated size fits within the maximum frame size and the commands are available when a frame has to be sent).

useCop1 (boolean)
    If set to true, the COP-1 protocol is used for acknowledgment of TC frames.

clcwStream (string)
    If COP-1 is enabled, this parameter configures the stream where the Command Link Control Words (CLCW) is read from.

initialClcwWait (integer)
    If COP-1 is enabled, this specifies how many seconds to wait for the first CLCW.

cop1T1 (integer)
    If COP-1 is enabled, this specifies the value in seconds for the timeout associated to command acknowledgments. If the command frame is not acknowledged within that time, it will be retransmitted. The default value is 3 seconds.

cop1TxLimit (integer)
    If COP-1 is enabled, this specifies the number of retransmissions for each un-acknowledged frame before suspending operations.

slidingWindowWidth (integer)
    If COP-1 is enabled, this specifies the default value for the FOP_SLIDING_WINDOW_WIDTH (K). Default: ``10``

bdAbsolutePriority (false)
    If COP-1 is enabled, this specifies that the BD frames have absolute priority over normal AD frames. This means that if there are a number of AD frames ready to be uplinked and a TC with ``cop1Bypass`` flag is received (see below for an explanation of this flag), it will pass in front of the queue so ti will be the first frame uplinked (once the multiplexer decides to uplink frames from this Virtual Channel). This flag only applies when the COP-1 state is active, if the COP-1 synchronization has not taken place, the BD frames are uplinked anyway (because all AD frames are waiting).

tcQueueSize (integer)
    This is used if COP-1 is not enabled, to determine the size of the command queue. Note that this is number of commands (not frames!). If the queue is full, the new commands will be rejected. Commands are taken from the queue by the multiplexer, according to the priority scheme defined below. Default: ``10``.

errorDetection (string)
    One of ``NONE`` or ``CRC16``. Specifies the error detection scheme used for the virtual channel, overriding the setting at link level. This is not according to the CCSDS standard which specifies the frame error detection shall be configured at physical channel level.
    If not specified (default), the setting at the link level will be used.

mapId (integer)
    If specified and positive, use the MAP service. Supported for TC frames only (not for USLP). Each frame will contain an extra byte after the primary header. The first two bits of the byte are set to 1 (i.e. unsegmented) and the last 6 bits are the map id. The default id is the one specified in this configuration. It can be overridden in the MDB or via command attributes. The map id has to be between ``0`` and ``15``.
    Default: ``-1`` (MAP service not used)


Priority Schemes
****************

The multiplexing of command frames from the different Virtual Channels is done according to the defined priority scheme. The multiplexer is triggered by the availability of the uplink - when a command frame is to be uplinked it has to decide from which Virtual Channel it will release it.

``FIFO`` means that the first frame received across all virtual channels will be the first one sent.

``ABSOLUTE`` means that the frames will be sent according to the priority set on each Virtual Channel (set by the ``priority`` parameter). This means that as long as a high priority VC has commands to be sent, the lower priority VC will not release any command.


``POLLING_VECTOR`` means that a polling vector will be built and each Virtual Channel will have the number of entries in the vector according to its priority. The multiplexing algorithm will cycle through the vector releasing the first command available.
For example if there are two VCs VC1 with priority 2 and VC2 with priority 4, the polling vector will look like: [VC1, VC1, VC2, VC2, VC2, VC2]. This means that if both VCs have a high number of frames to be sent, the multiplexer will send 2 frames from VC1 followed by 4 from VC2 and then again. If however VC2 has only one frame to be sent, it will lose its other three slots for that cycle and the multiplexer will go back to sending two frames from VC1.


COP-1 Support
*************


COP-1 is the protocol specified in  `CCSDS 232.1-B-2 <https://public.ccsds.org/Pubs/232x1b2e2c1.pdf>`_ for ensuring complete and correct transmission of TC frames. The protocol is using a sliding window principle based on the frame counter assigned by Yamcs to each uplinked frame.

The mechanism through which the on-board system reports the reception of commands is called Command Link Control Word (CLCW). This is a 4 byte word which is sent regularly by the on-board system to ground and contains the value of the latest received command counter and a few status bits. In Yamcs, we expect the CLCW to be made available on a stream (configured with the ``clcwStream`` parameter). The TM frame decoding can place the content of the OCF onto this stream. If the CLCW is sent as part of a regular TM packet, a StreamSQL statement like the following can be used:

.. code-block:: sql

   create stream clcw (clcw int)
   insert into clcw select extract_int(packet, 12) as clcw from tm_realtime where extract_short(packet, 0) = 2080

The first statement creates the stream, and the second inserts 4 bytes extracted from offset 12 from all telemetry packets having the first 2 bytes equal with 2080.

If the ``initialClcwWait`` parameter is positive, at the link startup, Yamcs waits for that number of seconds for a CLCW to be received; once it is received, Yamcs will set the value of the ground counter (called ``vS`` in the spec) to the on-board counter value (called ``nR`` in the spec) received in the CLCW. That will ensure that the next command frame sent by Yamcs will contain the counter value expected by the on-board system.

If the ``initialClcwWait`` parameter is not positive (the value will be ignored) or if no CLCW has been received within the specified time, the synchronization has to be initiated manually via the user interface. This can be done either waiting again for a new CLCW, setting manually a value for ``vS`` (this requires the operator to know somehow what value the on-board system is expecting) or sending a command to the on-board system to force the on-board counter to the same value like the ground.

If the ground and on-board systems are not synchronized and a command is received, there are two possible outcomes:

* if the initialization process has been started (manually or at the link startup with the ``initialClcwWait`` parameter), the command will be put in a wait queue to be sent once the Synchronization took place.
* if the initialization process has not been started or has failed, the command will be rejected straight away with the NACK on the Sent acknowledgment.


.. rubric:: AD, BD and BC frames

The CCSDS Standard distinguishes between three types of TC frames (the type is encoded in some bits in the frame primary header):

* AD frames contain normal telecommands and they are subjected to COP-1 transmission verification.
* BD frames contain normal telecommands but they are not subjected to COP-1 transmission verification.
* BC frames contain control commands generated by the ground COP-1 state machine and they are used to control the on-board state machine.

To send BD frames with Yamcs, you can use an attribute on the command called ``cop1Bypass``. If the link finds this attribute set to true, it will send the command in a BD frame, bypassing the COP-1 verification. The BC frames are sent only by the COP-1 state machine and it is not possible to send them from the user.

The user interface allows also to deactivate the COP-1 and the user can opt for sending all the commands as AD frames or BD frames regardless of the cop1Bypass attribute.


SDLS Support
************


SDLS is the protocol specified in `CCSDS 355.0-B-2 <https://ccsds.org/Pubs/355x1b1.pdf>`__ for securing CCSDS frames.

Yamcs supports loading custom SDLS Security Association implementations as `plugins <https://docs.yamcs.org/yamcs-maven-plugin/examples/plugin/>`__. You need one class to implement the ``SdlsSecurityAssociation`` interface (which does the actual security functions), and then another class to implement the ``SdlsSecurityAssociationFactory`` interface (which instructs how to create an instance of your Security Association implementation).

There is a built-in authenticated encryption implementation, AES-256-GCM-128, with ``class`` name ``org.yamcs.security.sdls.SecurityAssociationAes256Gcm128Factory``.

The managed parameters for this built-in implementation are:

* Security Parameter Index (SPI): configurable by user in the Yamcs instance config file
* Security Association Service Type: Authenticated Encryption is supported; not user-configurable.
* Security Association Context: GVCID is supported. A Security Association is associated with a virtual channel, so changing the MAP ID will not affect the SPI used for SDLS.
* Transmitted length of Initialization Vector: set to 12 octets (OWASP recommendation, baseline SDLS); not user-configurable.
* Transmitted length of Sequence Number: set to 4 octets; not user-configurable.
* Transmitted length of Pad Length: padding is not used; not user-configurable.
* Transmitted length of MAC: set to 16 octets (baseline SDLS); not user-configurable.
* Authentication algorithm: GCM is supported; not user-configurable.
* Authentication mask: set as dictated by the standard; not user-configurable
* Sequence number window: configurable by user in the Yamcs instance config file
* Encryption algorithm: AES-GCM is supported; not user-configurable.
* Authentication key: length is 256 bits, key is configured by user in the Yamcs configuration
* Encryption key: the same key as in authentication is used, because AES-GCM provides authenticated encryption. Length is 256 bits, key is configured by user in the Yamcs configuration.
* Sequence number: set to 0 when a Security Association is created. Not user-configurable, but can be reset via the API.
* Encryption initialization vector: initialized with a random value on every encryption; not user-configurable.


SRS4 Outer Frame Processing
---------------------------

The SRS4 providers add or remove the SRS4 radio header and either a CSP v1 or IPv4/UDP bus header. All SRS4-specific configuration is kept below the ``srs4`` provider argument. These layers are not part of CCSDS framing and are disabled when no provider is configured.

The following TC example enables both buses. The boolean command option ``useCan`` is then registered system-wide. A missing or false value selects Ethernet; true selects CAN. Commands with different selections are placed in different transfer frames.

.. code-block:: yaml

    frameEncapsulation:
      class: org.yamcs.tctm.ccsds.srs4.Srs4TcFrameEncapsulator
      args:
        srs4:
          radio:
            enabled: true
            spacecraftId: 0x1234
          csp:
            enabled: true
            sourceAddress: 1
            sourcePort: 10
            priority: 0
            hmac: false
            xtea: false
            rdp: false
            crc: false
          ipv4Udp:
            enabled: true
            sourceAddress: 10.0.0.1
            sourcePort: 1000
            ttl: 64
            calculateUdpChecksum: false
          controlFrameFlow: ETHERNET
          virtualChannels:
            - vcId: 0
              csp:
                destinationAddress: 2
                destinationPort: 20
              ipv4Udp:
                destinationAddress: 10.0.0.2
                destinationPort: 2000

For TM, the spacecraft source is selected by VC and the ground destination is fixed:

.. code-block:: yaml

    frameDecapsulation:
      class: org.yamcs.tctm.ccsds.srs4.Srs4TmFrameDecapsulator
      args:
        srs4:
          radio:
            enabled: true
            spacecraftId: 0x1234
          csp:
            enabled: true
            destinationAddress: 1
            destinationPort: 10
          ipv4Udp:
            enabled: true
            destinationAddress: 10.0.0.1
            destinationPort: 1000
            ttl: 64
          virtualChannels:
            - vcId: 0
              csp:
                sourceAddress: 2
                sourcePort: 20
              ipv4Udp:
                sourceAddress: 10.0.0.2
                sourcePort: 2000

Provider activation and flow selection
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``frameEncapsulation.class``
    For TC, set this to ``org.yamcs.tctm.ccsds.srs4.Srs4TcFrameEncapsulator``. The provider receives an already constructed CCSDS transfer frame and adds the configured SRS4 layers before optional CLTU encoding.

``frameDecapsulation.class``
    For TM, set this to ``org.yamcs.tctm.ccsds.srs4.Srs4TmFrameDecapsulator``. After optional raw-frame decoding, the provider removes and validates the SRS4 layers before CCSDS transfer-frame decoding.

``args.srs4``
    Contains all SRS4-specific configuration. Keeping these fields below this map prevents the SRS4 wire format from becoming part of the general CCSDS link configuration.

``radio.enabled``
    Enables the four-byte SRS4 radio header. If the ``radio`` map is present, this defaults to true. The radio layer is mandatory whenever either bus layer is enabled.

``csp.enabled``
    Enables the four-byte CSP v1 header used by the CAN contingency flow. If the ``csp`` map is present, this defaults to true.

``ipv4Udp.enabled``
    Enables the 28-byte IPv4/UDP header used by the Ethernet nominal flow. If the ``ipv4Udp`` map is present, this defaults to true.

The permitted combinations are:

* radio and CSP: the link always uses CAN;
* radio and IPv4/UDP: the link always uses Ethernet;
* radio, CSP and IPv4/UDP: TC flow is selected at runtime and TM flow is selected from the received radio type bit;
* no SRS4 provider: existing Yamcs framing behavior is unchanged.

A bus layer without the radio layer, a radio layer without a bus layer, or an SRS4 provider without either bus layer is rejected during initialization.

When both TC bus layers are enabled, Yamcs registers the system-wide boolean command option ``useCan``:

* missing or false selects the Ethernet nominal flow;
* true selects the CAN contingency flow;
* commands with different values are not aggregated into the same CCSDS transfer frame;
* the option is not registered by a single-bus SRS4 TC configuration.

Radio fields and wire format
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``radio.spacecraftId``
    **Required.** Unsigned 16-bit identifier written into the SRS4 radio header. This identifier is independent of the CCSDS ``spacecraftId`` configured on the frame link. TM frames whose radio identifier does not match are discarded.

The radio header is four bytes in network byte order:

* bits 15 to 12: reserved and required to be zero;
* bit 11: type, where 0 identifies CSP/CAN and 1 identifies IPv4/UDP/Ethernet;
* bits 10 to 0: content length;
* the following two bytes: ``radio.spacecraftId``.

The 11-bit length excludes the two-byte type/length word. It includes the two-byte radio spacecraft ID, the selected bus header, and the complete CCSDS transfer frame including SDLS overhead and the CCSDS frame error-control field. Therefore the maximum value is 2047. Yamcs rejects a configuration whose maximum CCSDS frame length plus SRS4 overhead cannot fit in this field.

On TM, Yamcs verifies the reserved bits, declared length, configured spacecraft ID and type before selecting a bus-header decoder.

CSP fields
~~~~~~~~~~

The CSP layer uses the existing Yamcs CSP v1 bit layout. All CSP integers are encoded in network byte order.

``csp.sourceAddress``
    **Required for TC.** Fixed ground source address in the range 0 to 31.

``csp.sourcePort``
    **Required for TC.** Fixed ground source port in the range 0 to 63.

``csp.destinationAddress``
    **Required for TM.** Fixed ground destination address in the range 0 to 31.

``csp.destinationPort``
    **Required for TM.** Fixed ground destination port in the range 0 to 63.

``csp.priority``
    CSP priority in the range 0 to 3. Default: ``0``. TM requires the received priority to equal this configured value.

``csp.hmac``
    Sets or validates the CSP HMAC flag. Default: ``false``.

``csp.xtea``
    Sets or validates the CSP XTEA flag. Default: ``false``.

``csp.rdp``
    Sets or validates the CSP RDP flag. Default: ``false``.

``csp.crc``
    Sets or validates the CSP CRC flag. Default: ``false``.

The four CSP flags affect only the bits in the CSP header. The SRS4 provider does not perform CSP authentication, encryption or RDP processing, and does not append or remove a CSP CRC trailer. Any such processing must be implemented outside this provider.

IPv4/UDP fields
~~~~~~~~~~~~~~~

``ipv4Udp.sourceAddress``
    **Required for TC.** Fixed ground IPv4 source address. Specify a dotted-decimal IPv4 literal; host names and IPv6 addresses are not accepted.

``ipv4Udp.sourcePort``
    **Required for TC.** Fixed UDP source port in the range 0 to 65535.

``ipv4Udp.destinationAddress``
    **Required for TM.** Fixed ground IPv4 destination address.

``ipv4Udp.destinationPort``
    **Required for TM.** Fixed ground UDP destination port in the range 0 to 65535.

``ipv4Udp.ttl``
    IPv4 time-to-live in the range 1 to 255. Default: ``64``. TC writes this value and TM requires the received value to match.

``ipv4Udp.calculateUdpChecksum``
    TC-only option controlling UDP checksum generation. Default: ``false``. If false, Yamcs writes zero, which indicates that no UDP checksum is supplied for IPv4. If true, Yamcs calculates the checksum using the IPv4 pseudo-header. TM always accepts zero and verifies every nonzero UDP checksum, independently of this setting.

Yamcs constructs a fixed 20-byte IPv4 header followed by an eight-byte UDP header. It sets IPv4 version 4, IHL 5, DSCP/ECN zero, identification zero, no fragmentation and protocol UDP. IP total length, UDP length and the IPv4 header checksum are calculated automatically. IPv4 options and fragmented packets are not supported.

Virtual-channel endpoint fields
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``virtualChannels``
    **Required.** SRS4 routing entries. Every VC configured on the parent CCSDS link must have exactly one SRS4 route containing an endpoint for each enabled bus layer. Duplicate VC entries are rejected.

``virtualChannels[].vcId``
    CCSDS virtual-channel identifier to which the nested endpoints apply.

For TC, the fixed endpoints are the ground sources and each VC supplies spacecraft destinations:

``virtualChannels[].csp.destinationAddress`` / ``destinationPort``
    CSP destination for this VC.

``virtualChannels[].ipv4Udp.destinationAddress`` / ``destinationPort``
    IPv4/UDP destination for this VC.

For TM, the fixed endpoints are the ground destinations and each VC supplies spacecraft sources:

``virtualChannels[].csp.sourceAddress`` / ``sourcePort``
    Expected CSP source for this VC.

``virtualChannels[].ipv4Udp.sourceAddress`` / ``sourcePort``
    Expected IPv4/UDP source for this VC.

TM source endpoint pairs must be unique within each bus layer. Yamcs first resolves the received source endpoint to an expected VC, then compares it with the VC decoded from the inner CCSDS frame. A mismatch causes the frame to be discarded.

``controlFrameFlow`` and COP-1
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``controlFrameFlow`` selects the SRS4 bus used for a TC frame that has no originating ``PreparedCommand`` from which Yamcs could read ``useCan``. Valid values are ``ETHERNET`` and ``CAN``. Default: ``ETHERNET``.

The principal use is internally generated COP-1 BC control frames, including Unlock and Set V(R). These frames are created by the COP-1 state machine rather than submitted by an operator, so they do not carry command options.

``controlFrameFlow`` does not override ``useCan`` on normal telecommands:

* COP-1 AD frames use the ``useCan`` value of their contained command;
* COP-1 BD frames, including commands sent with ``cop1Bypass``, also use their command's ``useCan`` value;
* retransmitted AD frames retain the commands and therefore retain their originally selected flow;
* COP-1 BC frames and any other command-control frame without a command use ``controlFrameFlow``.

This field is meaningful only when both CSP and IPv4/UDP are enabled. With a single enabled bus, all frames—including control frames—use that bus. Configure ``controlFrameFlow: CAN`` only when CSP is enabled. If COP-1 is disabled and the link never creates command-control frames, this field has no operational effect.

TM validation and processing order
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

After validating the radio header, TM performs strict validation of the selected bus header. CSP destination, source route, priority, flags and reserved bits must match configuration. IPv4 version, IHL, DSCP/ECN, fragmentation, TTL, protocol, lengths and checksum are validated, followed by UDP lengths, checksum and endpoints.

TC processing order is:

#. Construct the CCSDS transfer frame, including optional SDLS and the CCSDS frame error-control field.
#. Add the selected CSP or IPv4/UDP header.
#. Add the SRS4 radio header.
#. Apply optional CLTU encoding to the complete result.

TM processing reverses the uplink layering:

#. Apply the configured raw-frame decoder, including Reed-Solomon decoding and/or derandomization.
#. Remove and validate the SRS4 radio header.
#. Remove and validate the selected CSP or IPv4/UDP header.
#. Pass the resulting CCSDS frame to the configured TM/AOS/USLP transfer-frame decoder.

Any channel coding not supported by ``rawFrameDecoder`` must still be removed before the frame reaches this link.
