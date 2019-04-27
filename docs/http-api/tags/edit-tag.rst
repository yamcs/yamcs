Edit Tag
========

Edit a tag::

    PATCH /api/archive/:instance/tags/:start/:id


.. rubric:: Parameters

name (string)
    The name of the tag.

description (string)
    The description of the tag.

start (string)
    The start time of the tag. Must be a date string in ISO 8601 format. Set to empty to indicate unbounded.

stop (string)
    The stop time of the tag. Must be a date string in ISO 8601 format. Set to empty to indicate unbounded.

color (string)
    The color of the tag. Must be an RGB hex color, e.g. ``#ff0000``.

The same parameters can also be specified in the request body. In case both query string parameters and body parameters are specified, they are merged with priority being given to query string parameters.

.. rubric:: Example

Change the color, and the description:

.. code-block:: json

    {
      "color" : "#00ff00",
      "description": "an example description"
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message EditTagRequest {
      optional string name = 1;
      optional string start = 2;
      optional string stop = 3;
      optional string description = 4;
      optional string color = 5;
    }
