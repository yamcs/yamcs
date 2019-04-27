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


Yamcs will look for a template file in
<YAMCS_ETC>/instance-templates/template1/template.yaml with content
like this:

.. code-block:: yaml

    ...
        - class: org.yamcs.simulation.simulator.SimulatorCommander
        args:
        telnet:
            port: {{ telnetPort }}
        tctm:
            tmPort: {{ tmPort }}
            tcPort: {{ tcPort }}
            losPort: {{ losPort }}
            tm2Port: {{ tm2Port }}
    dataLinks:
    - name: tm_realtime
        enabledAtStartup: false
        class: org.yamcs.tctm.TcpTmDataLink
        args:
        stream: tm_realtime
        host: localhost
        port: {{ tmPort }}
    ...


After the request is executed successfully, a file will be created: <YAMCS_DATA>/instance_def/yamcs.simulator2.yaml where the template arguments are replaced with the instantiated values:

.. code-block:: yaml

    ...
        - class: org.yamcs.simulation.simulator.SimulatorCommander
            args:
            telnet:
                port: 30004
            tctm:
                tmPort: 30000
                tcPort: 30001
                losPort: 30002
                tm2Port: 30003
    dataLinks:
    - name: tm_realtime
        enabledAtStartup: false
        class: org.yamcs.tctm.TcpTmDataLink
        args:
        stream: tm_realtime
        host: localhost
        port: 30000
    ...
