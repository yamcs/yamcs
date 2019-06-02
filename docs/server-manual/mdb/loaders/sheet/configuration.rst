Configuration
-------------

The loader is configured in ``etc/mdb.yaml`` or in the instance configuration by specifying the 'type' as ``sheet``, and providing the location of the XML file in the ``spec`` attribute.

.. code-block:: yaml

    - type: "sheet"
      spec: "BogusSAT.xls"
