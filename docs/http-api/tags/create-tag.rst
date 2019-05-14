Create Tag
==========

Create a tag for the given archive instance::

    POST /api/archive/:instance/tags


.. rubric:: Parameters

name (string)
    **Required.** The name of the tag.

description (string)
    The description of the tag.

start (string)
    The start time of the tag. Default is unbounded.

stop (string)
    The stop time of the tag. Default is unbounded.

color (string)
    The color of the tag. Must be an RGB hex color, e.g. ``#ff0000``


.. rubric:: Example

Create a red tag covering January 1st 2015 onwards:

.. code-block:: json

    {
      "name" : "My archive annotation",
      "start" : "2015-01-01T00:00:00.000Z",
      "color" : "#ff0000"
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message CreateTagRequest {
      optional string name = 1;
      optional string start = 2;
      optional string stop = 3;
      optional string description = 4;
      optional string color = 5;
    }
