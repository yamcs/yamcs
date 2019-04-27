Create Processor
================

Create a processor for the given Yamcs instance::

    POST /api/processors/:instance


.. rubric:: Parameters

name (string)
    **Required.** The name of the processor. Must be unique for the Yamcs instance.

type (string)
    **Required.** The type of the processor. The available values depend on how Yamcs Server is configured. Most Yamcs deployments support at least a type ``Archive`` which allows for the creation of processors replaying archived data.

persistent (bool)
    Keep the processor when terminated. Default: ``no``.

clientId (array of integers)
    The client IDs that should be connected to this processor.

config (string)
    Configuration options specific to the processor type. Note that this should be a string representation of a valid JSON structure.


.. rubric:: Replay Config

When creating a processor of type `Archive`, the ``config`` JSON supports these parameters:

utcStart (string)
    **Required.** The time at which the replay should start. Must be a date string in ISO 8601 format.

utcStop (string)
    The time at which the replay should stop. Must be a date string in ISO 8601 format. If unspecified, the replay will keep going as long as there is remaining data.

loop (bool)
    Whether the processing should restart at the end of the replay. Default: ``no``.

speed (string)
    The speed of the processor. One of:

    * ``afap``
    * a speed factor relative to the original speed. Example: ``2x``
    * a fixed delay value in milliseconds. Example: ``2000``

    Default: ``1x``

paraname (array of strings)
    Name patterns of parameters that are included in the replay. Patterns are matched on the qualified names and support wildcard expansion. If the pattern matches the name of a space system, all parameters directly within that system are included.

ppgroup (array of strings)
    | Exact names of the groups of processed parameters to include in the replay.
    | **Partial wildcard matching is not currently supported.**

packetname (array of strings)
    | Exact qualified names of the packets to include in the replay. Specify ``*`` to include all packets.
    | **Partial wildcard matching is not currently supported.**

cmdhist (bool)
    Whether or not to replay Command History. Default: ``no``.


.. rubric:: Example

Start a replay at January 1st 2015 at 4.5x the original speed, and add client 12 to the replay:

.. code-block:: json

    {
      "name" : "An example processor",
      "type": "Archive",
      "clientId" : [ 12 ],
      "config": "{\"utcStart\":\"2015-01-01T00:00:00.000Z\",\"speed\":\"4.5x\"}",
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message CreateProcessorRequest {
      optional string name = 1;
      repeated int32 clientId = 6;
      optional bool persistent = 11;
      optional string type = 12;
      optional string config = 13;
    }
