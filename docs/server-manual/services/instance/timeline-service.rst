Timeline Service
================

This services enables Timeline and Activity-related functionalities.

The Yamcs Timeline provides a visual, chronological overview of mission events. It can also be used to schedule activities for future execution.


Class Name
----------

:javadoc:`org.yamcs.timeline.TimelineService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.timeline.TimelineService
        args:
          activities:
            scriptExecution:
              searchPath: etc/scripts
              impersonateCaller: false
              fileAssociations:
                py: python3 -u


Configuration Options
---------------------

scheduling (map)
  Placeholder for future scheduling-related options. Nothing currently.

activities (map)
  Optional configuration for each of the supported activity executors.

  The built-in types are ``commandExecution``, ``stackExecution`` and ``scriptExecution``. These are further described in the sub-configuration sections below.


Command execution sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Placeholder for future command executor options. Nothing currently.


Stack execution sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Placeholder for future stack executor options. Nothing currently.


Script execution sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

searchPath (string or string[])
  Directory where to locate scripts or executables.

  Default: :file:`etc/scripts`

fileAssociations (map)
  Extend or override the default file associations. Each entry maps a file extension (case-insensitive) to a program that should be used to execute this file.

  The default file associations are:

  .. code-block:: yaml

      fileAssociations:
        java: java
        js: node
        mjs: node
        pl: perl
        py: python -u
        rb: ruby

  Any file that does not have an association, is executed directly.

impersonateCaller (boolean)
  Scripts receive a transient API key via an environment variable. By default this API key uses the built-in ``System`` user, which provides unrestricted access.

  When this property is enabled, the script receives instead an API key of the user that started the activity.

  Default: ``false``
