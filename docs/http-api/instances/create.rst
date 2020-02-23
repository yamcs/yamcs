Create Instance
===============

Create an Instance::

    POST /api/instances


.. rubric:: Parameters

name (string)
    **Required.** The name of the instance.

template (string)
    **Required.** The name of the template for this instance.

templateArgs (map)
    Arguments for substitution in the template definition. Each entry is keyed by the argument name. The value must be a string.

labels (map)
    Labels assigned to this instance. Each entry is keyed by the tag name of the label. The value represent the label value for that tag.


.. rubric:: Example
.. code-block:: json

    {
      "name": "simulator2",
      "template": "template1",
      "templateArgs": {
        "tmPort": "30000",
        "tcPort": "30001",
        "losPort": "30002",
        "tm2Port": "30003",
        "telnetPort": "30004"
      },
      "labels": {
        "label1": "value1",
        "label2": "value2"
      }
    }
