Configuration
=============

Web options are configured in the file :file:`etc/yamcs.yaml`.

.. code-block:: yaml

   yamcs-web:
     tag: Example Mission
     logo: etc/logo.png
     siteLinks:
       - label: Wiki
         url: https://example.com/wiki
         external: true

Some options can also be configured at instance-level in the file :file:`etc/yamcs.{instance}.yaml`.

.. code-block:: yaml

   yamcs-web:
     displayBucket: customBucket
     stackBucket: customBucket


.. contents:: Contents
   :local:
   :backlinks: none


Global Configuration Options
----------------------------

.. options:: ../../yamcs-web/src/main/resources/org/yamcs/web/WebPlugin.yaml


Instance Configuration Options
------------------------------

.. options:: ../../yamcs-web/src/main/resources/org/yamcs/web/WebPlugin.yaml
   :scope: instance
