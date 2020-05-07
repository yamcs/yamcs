CCSDS Frame Processing
======================

This section describes Yamcs support for parts of the following CCSDS specifications:

 * TM Space Data Link Protocol `CCSDS 132.0-B-2 <https://public.ccsds.org/Pubs/132x0b2.pdf>`_
 * AOS AOS Space Data Link Protocol `CCSDS 732.0-B-3 <https://public.ccsds.org/Pubs/132x0b2.pdf>`_
 * TC Space Data Link Protocol `CCSDS 232.0-B-3 <https://public.ccsds.org/Pubs/232x0b3.pdf>`_
 * Unified Space Data Link Protocol `CCSDS 732.1-B-1  <https://public.ccsds.org/Pubs/732x1b1.pdf>`_
 * TC Synchronization and Channel Coding `CCSDS 231.0-B-3 <https://public.ccsds.org/Pubs/231x0b3.pdf>`_
 * Communications Operation Procedure (COP-1) `CCSDS 232.1-B-2 <https://public.ccsds.org/Pubs/232x1b2e2c1.pdf>`_
 * Space Packet Protocol `CCSDS 133.0-B-1 <https://public.ccsds.org/Pubs/133x0b1c2.pdf>`_
 * Encapsulation Service `CCSDS 133.1-B-2 <https://public.ccsds.org/Pubs/133x1b2c2.pdf>`_

These specifications are dealing with multiplexing and to a certain extent encoding data for transmission on a space link.

The document `Space Data Link Protocols — Summary of Concept and Rationale <https://public.ccsds.org/Pubs/130x2g3.pdf>`_ provides a comprehensive summary of the different protocols and it is recommended to read it before attempting to configure Yamcs to use these protocols.


Telemetry Frame Processing
--------------------------

The CCSDS specifies how to transport data into three types of frames:
 * AOS
 * TM
 * USLP

In Yamcs we support to a certain extent all three of them. The main support is around the "packet service" - that is describing how the telemetry packets are extracted from the frames. The implementation is however generic enough (hopefully) such that it is possible to add additional functionality for processing non-packet data (e.g. sending video to external application).

The packets are inserted into frames which are sent as part of Virtual Channels (VC). The VCs can have different priority on-board, for example one VC can be used to transport low volume HK data, while another one to transport high volume science data.

Note that The USLP frames (as well as the TC frames used for commanding) support a second level of multiplexing called Multiplexer Access Point (MAP) which allows multiplexing data inside a VC. The MAP service is not supported by Yamcs.

Currently the built-in way to receive frame dat inside Yamcs is by using the UdpTmFrameLink data link. The yamcs-sle project provides an implementation of the Space Link Extension (SLE) which allows receiving frame data from Ground Stations supporting this protocol. The options described below are valid for both link types.

An example of a UDP TM frame link specification is below:

.. code-block:: yaml

    - name: UDP_FRAME_IN
        class: org.yamcs.tctm.ccsds.UdpTmFrameLink
        args:
            port: 10017
            frameType: "AOS"
            spacecraftId: 0xAB
            frameLength: 512
            frameHeaderErrorControlPresent: true
            insertZoneLength: 0
            errorDetection: CRC16
            clcwStream: clcw
            virtualChannels:
                - vcId: 0
                  ocfPresent: true
                  service: "PACKET"
                  maxPacketLength: 2048
                  packetPreprocessorClassName: org.yamcs.tctm.IssPacketPreprocessor
                  packetPreprocessorArgs:
                       ....
                  stream: "tm_realtime"
                - vcId: 1
                  ocfPresent: true
                  service: "PACKET"
                  maxPacketLength: 2048
                  stripEncapsulationHeader: true
                  packetPreprocessorClassName: org.yamcs.tctm.GenericPacketPreprocessor
                  packetPreprocessorArgs:
                       ....        
                  stream: "tm2_realtime"
                - vcId: 2
                  ocfPresent: true
                  service: "PACKET" 
                  maxPacketLength: 2048
                  packetPreprocessorClassName: org.yamcs.tctm.IssPacketPreprocessor
                  stream: "tm_dump"

The following general options are supported:

frameType (string)
    **Required.** One of ``AOS``, ``TM`` or ``USLP``. The first 2 bits for AOS/TM and 4 bits for USLP represent the version number and have to have the value 0, 1 or 12 respectively. If a frame is received that has a different version, it is discarded (with a warning log message). 

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
    The AOS and USLP frames can optionally use an Insert Service to trasnfer fixed-length data synchronized with the release of the frames. The insert data follows immediately the frame primary header. If the Insert Service is used, this parameter specifies the length of the insert data. If not used, please set it to 0 (default). For TM frames this parameter is ignored.
    Currently Yamcs ignores any data in the insert zone. 

errorDetection (string)
    One of ``NONE``, ``CRC16`` or ``CRC32``. Specifies the error detection scheme used. TM and AOS frames support either NONE or CRC16 while USLP supports NONE, CRC16 or CRC32. If present, the last 2 resepectively 4 bytes of the frame will contain an error control field. If the CRC does not match the computation, the frame will be discarded (with a warning message).

clcwStream (string)
    Can be used to specify the name of the stream where the Command Link Control Words (CLCW) will be sent. The CLCW is the mechanism used by COP-1 to acknolwedge uplinked frames. For TM and USLP frames, there is an OCF flag part of the frame header indicating the presence or not of the CLCW. For AOS frames it has to be configured with the ``ocfPresent`` flag below.
    If present, the CLCW is also extracted from idle frames (i.e. frames that are inserted when no data needs to be transmitted in order to keep the constant bitrate required for downlink).
    
virtualChannels (map)
    **Required.** Used to specify the Virtual Channel specific configuration. 

For each Virtual Channel in the ``virtualChannels`` map, the following parameters can be used:

vcId (integer)
    **Required.** The configured Virtual Channel identifier.

ocfPresent: (boolean)
    Used for AOS frames to indicate that the Virtual Channel uses the  Operational Control Field (OCF) Service to transport the CLCW containing acknowledgemnts for the uplinked TC frames. For TM and USLP frames, there is a flag in each frame that indicates the presence or absence of OCF.
   

service:
    **Required.** This specifies the type of data that is part of the Virtual Channel. The only supported option for TM frames is ``PACKET``. AOS and USLP also support ``IDLE`` to indicate that the Virtual Channel contains only idle frames . Normally, the AOS and USLP use the Virtual Channel 63 to transmit idle frames and you do not need to define this virtual channel (in conclusion ``IDLE`` is not very useful). The TM frames have a different mechanism to signal idle frames (first header pointer is 0x7FE).
    For the PACKET service type, both CCSDS space packets and CCSDS encapsulation packets are supported (even multiplexed on the same virtual channel). The type of packet is detected based on the first 3 bits of data: 000=CCSDS space packet, 111=encapsulation packets. 
    Idle CCSDS space packets (having APID = 0x7FF) and idle encapsulation packets (having first byte = 0x1C) are discarded.
    If you need support for other types of services (e.g. bitstream), please contact Space Applications Services.
    
maxPacketLength:
    **Required if service=PACKET.**  Specifies the maximum size of a packet (header included). Valid for both CCSDS Space Packets and CCSDS encapsulation packets. If the header of a packet indicates a packet size larger than this value, a warning event is raised and the packet is droped including all the data until a new frame containing a packet start. 

packetPreprocessorClassName and packetPreprocessorArgs
    **Required if service=PACKET.** Specfies the packet pre-processor and its configuration that will be used for the packets extracted from this Virtual Channel. See :doc:`packet-preprocessor` for details.
  

Telecommand Frame Processing
----------------------------

Yamcs supports packing telecommand packets into TC Transfer Frames and in addition encapsulating the frames into Communications Link Transmission Unit (CLTU).

Currently the built-in way to send telecommand frames from  Yamcs is by using the UdpTcFrameLink data link. The yamcs-sle project provides an implementation of the Space Link Extension (SLE) which allows sending CLTUs to Ground Stations supporting this protocol. The options described below are valid for both link types.

An example of a UDP TC frame link specification is below:

.. code-block:: yaml

    - name: UDP_FRAME_OUT
        class: org.yamcs.tctm.ccsds.UdpTcFrameLink
        args:
            host: localhost
            port: 10018
            spacecraftId: 0xAB
            maxFrameLength: 1024
            cltuEncoding: BCH
            priorityScheme: FIFO
            randomizeCltu: false
            virtualChannels:
                - vcId: 0
                  service: "PACKET" 
                  priority: 1
                  commandPostprocessorClassName: org.yamcs.tctm.IssCommandPostprocessor
                  commandPostprocessorArgs:
                        ...
                  stream: "tc_sim" 
                  useCop1: true 
                  clcwStream: "clcw" 
                  initialClcwWait: 3600
                  cop1T1: 3
                  cop1TxLimit: 3
                  bdAbsolutePriority: false
           
           
The following general options are supported:

spacecraftId (integer)
    **Required.** The spacecraftId is encoded in the TC Transfer Frame primary header.
    
maxFrameLength (integer)
    **Required.** The maximum length of the frames sent over this link. The Virtual Channel can also specify an option for this but the VC specifc maximum frame length has to be smaller or equal than this. Note that since Yamcs does not support segmentation (i.e. spliting a packet over multiple frames), this value limits effectively the size of the TC packet that can be sent.

priorityScheme (string)
    One of ``FIFO``, ``ABSOLUTE`` or ``POLLING_VECTOR``. This configures the priority of the different Virtual Channels. See below an explanation on how the different schemes work.
    
cltuEncoding (string)
    One of ``BCH``, ``LDPC64`` or ``LDPC256``. If this parameter is present, the TC transfer frames will be encoded into CLTUs and this parameter configures the code to be used. If this parameter is not present, the frames will not be encapsulated into CLTUs and the following related parameters are ignored.

cltuStartSequence (string)
    This parameter can optionally set the  CLTU start sequence in hexadecimal if different than the CCSDS specs.

cltuTailSequence (string)
    This parameter can optionally set the CLTU tail sequence in hexadecimal if different than the CCSDS specs.
    
randomizeCltu (boolean)
    Used if cltuEncoding is BCH to enable/disable the randomization. For LDPC encoding, randomization is always on. 
 
virtualChannels (map)
    **Required.** Used to specify the Virtual Channel specific configuration. 

For each Virtual Channel in the ``virtualChannels`` map, the following parameters can be used:

vcId (integer)
    **Required.** The Virtual Channel identifier to be used in the frames. You can define multiple entries in the map with the same vcId, if the data is coming from different streams.

service (string)
    Currently the only supported option is ``PACKET`` which is also the default.

commandPostprocessorClassName (string) and commandPostprocessorArgs (string)
   **Required if service=PACKET.** Specfies the command post-processor and its configuration. See :doc:`command-post-processor` for details.
   
stream (string)
     **Required.** The stream on which the commands are received.
     
multiplePacketsPerFrame (boolean)
    If set to true (default), Yamcs will send multiple command packets in one frame if possible (i.e. if the accumulated size fits within the maximum frame size and the commands are available when a frame has to be sent).

useCop1 (boolean)
    If set to true, the COP1 protocol will be used for acknowledgemnts of the TC frames.

clcwStream (string)
    If COP1 is enabled, this parameter configures the stream where the Command Link Control Words (CLCW) is read from.

initialClcwWait (integer)
    If COP1 is enabled, this specifies how many seconds to wait for the first CLCW.

cop1T1 (integer)
    If COP1 is enabled, this specifies the value in seconds for the timeout associated to command acknowledgemnts. If the command frame is not acknowledged within that time, it will be retransmitted. The default value is 3 seconds.

cop1TxLimit (integer)
    If COP1 is enabled, this specifies the number of retransmissions for each un-acknolwedged frame before suspending operations.
           
bdAbsolutePriority (false)
    If COP1 is enabled, this specifies that the BD frames have absolute priority over normal AD frames. This means that if there are a number of AD frames ready to be uplinked and a TC with ``cop1Bypass`` flag is received (see below for an explanation of this flag), it will pass in front of the queue so ti will be the first frame uplinked (once the multiplexer decides to uplink frames from this Virtual Channel). This flag only applies when the COP1 state is active, if the COP1 synchnoziation has not taken place, the BD frames are uplinked anyway (because all AD frames are waiting). 
    
           
Priority Schemes
****************

The multiplexing of command frames from the different Virtual Channels is done according to the defined priority scheme. The multiplexer is triggered by the availability of the uplink - when a command frame is to be uplinked it has to decide from which Virtual Channel it will release it. 

``FIFO`` means that the first frame received across all virtual channels will be the first one sent.

``ABSOLUTE`` means that the frames will be sent according to the priority set on each Virtual Channel (according to the ``priority`` parameter). This means that as long as a high priority VC has commands to be sent, the lower priority VC will not release any command.


``POLLING_VECTOR`` means that a polling vector will be built and each Virtual Channel will have the number of entries in the vector according to its priority. The multiplexing algorithm will cycle throgugh the vector releasing the first command available. 
For example if there are two VCs VC1 with priority 2 and VC2 with priority 4, the polling vector will look like: [VC1, VC1, VC2, VC2, VC2, VC2]. This means that if both VCs have a high number of frames to be sent, the multiplexer will send 2 frames from VC1 followed by 4 from VC2 and then again. If however VC2 has only one frame to be sent, it will lose its other three slots for that cycle and the multiplexer will go back to sending two frames from VC1.


COP1 Support
************


COP1 is the protocol specified in  `CCSDS 232.1-B-2 <https://public.ccsds.org/Pubs/232x1b2e2c1.pdf>`_ for ensuring complete and correct transmission of TC frames. The protocol is using a sliding window principle based on the frame counter assigned by Yamcs to each uplinked frame.

The mechanism through which the on-board system reports the reception of commands is called Command Link Control Word (CLCW). This is a 4 byte word which is sent regularely by the on-board system to ground and contains the value of the latest received command counter and a few status bits. In Yamcs, we expect the CLCW to be made available on a stream (configured with the ``clcwStream`` parameter). The TM frame decoding can place the content of the OCF onto this stream. If the CLCW is sent as part of a regular TM packet, a StreamSQL statement like the following can be used:

.. code-block::sql
   create stream clcw (clcw int)
   insert into clcw select extract_int(packet, 12) as clcw from tm_realtime where extract_short(packet, 0) = 2080

The first statement creates the stream, and the second inserts 4 bytes extracted from offset 12 from all telemetry packets having the first 2 bytes equal with 2080. 

If the ``initialClcwWait`` parameter is positive, at the link startup, Yamcs waits for that number of seconds for a CLCW to be received; once it is received, Yamcs will set the value of the ground counter (called ``vS`` in the spec) to the on-board counter value (called ``nR`` in the spec) received in the CLCW. That will ensure that the next command frame sent by Yamcs will contain the counter value expected by the on-board system.

If the ``initialClcwWait`` parameter is not positive (the value will be ignored) or if no CLCW has been received within the specified time, the synchnoziation has to be initiated manually via the user interface. This can be done either waiting again for a new CLCW, setting manually a value for ``vS`` (this requires the operator to know somehow what value the on-board system is expecting) or sending a command to the on-board system to force the on-board counter to the same value like the ground.

If the ground and on-board systems are not synchronized and a command is received, there are two possible outcomes:

   * if the initialization process has been started (manually or at the link startup with the ``initialClcwWait`` parameter), the command will be put in a wait queue to be sent once the Synchronization took place.
   * if the initialization process has not been started or has failed, the command will be rejected straing away with the NACK on the Sent acknowledgemnt.
 
**AD, BD and BC frames**

The CCSDS Standard distinguishes between three types of TC frames (the type is encoded in some bits in the frame primary header):

    * AD frames contain normal telecommands and they are subjected to COP1 transmission verification.
    * BD frames contain normal telecommands but they are not subjected to COP1 transmission verification.
    * BC frames contain control commands generated by the ground COP1 state machine and they are used to control the on-board state machine.

To send BD frames with Yamcs, you can use an attribute on the command called ``cop1Bypass``. If the link finds this attribute set to true, it will send the command in a BD frame, bypassing the COP1 verification. The BC frames are sent only by the COP1 state machine and it is not possible to send them from the user.

The user interface allows also to deactivate the COP1 and the user can opt for sending all the commands as AD frames or BD frames regardless of the cop1Bypass attribute.



