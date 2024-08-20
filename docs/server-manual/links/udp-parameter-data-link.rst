UDP Parameter Data Link
=======================

Listens on a UDP port for datagrams containing Protobuf encoded messages. One datagram is equivalent to a message of type :javadoc:`~org.yamcs.protobuf.Pvalue.ParameterData`.

By enabling the ``json`` option, this link can also be switched to accepting the JSON equivalent of a Protobuf ``ParameterData`` message.

If more flexibility is needed, this link class can be extended in Java to override the ``decodeDatagram(byte[] data, int offset, int length)`` method. Then you can use custom logic to convert the incoming datagram to a message of type ``ParameterData``.


Class Name
----------

:javadoc:`org.yamcs.tctm.UdpParameterDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where data is emitted

port (integer)
    **Required.** The UDP port to listen on

recordingGroup (string)
    Name of the group used for incoming updates. Groups are identifiable in the Archive Browser.

    The recording group can also be specified as a property in ``ParameterData``, overriding this configuration setting.

    Default: ``DEFAULT``

json (boolean)
    If ``true``, decode the incoming message from JSON instead of Protobuf.

    Default: ``false``


JSON Example
------------

Add ``UdpParameterDataLink`` to the list of data links:

.. code-block:: yaml
   :caption: :file:`etc/yamcs.{instance}.yaml`

   dataLinks:
     - name: pp-in
       class: org.yamcs.tctm.UdpParameterDataLink
       stream: pp_realtime
       port: 11016
       json: true

Then a Python script like the following updates two parameters at the same time with a single datagram:

.. code-block:: python

   import json
   import socket
   from datetime import datetime, timezone

   gentime = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

   data = json.dumps(
       {
           "parameter": [
               {
                   "id": {"name": "/myproject/Battery1_Temp"},
                   "generationTime": gentime,
                   "engValue": {
                       "type": "FLOAT",
                       "floatValue": 123,
                   },
               },
               {
                   "id": {"name": "/myproject/ElapsedSeconds"},
                   "generationTime": gentime,
                   "engValue": {
                       "type": "UINT32",
                       "uint32Value": 123,
                   },
               },
           ]
       }
   ).encode()

   with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
       s.sendto(data, ("localhost", 11016))
