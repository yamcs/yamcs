Script Activities
=================

Yamcs can run arbitrary scripts or programs as a background activity.

Scripts are predefined and stored under :file:`etc/scripts`.

Script files may be directly executable, or be associated to another program based on its file extension.


Configuration
-------------

Script activity options are configured in the instance configuration file :file:`etc/yamcs.{instance}.yaml`.

.. code-block:: yaml
    :caption: :file:`etc/yamcs.{instance}.yaml`

    activities:
      scriptExecution:
        searchPath: etc/scripts
        impersonateCaller: false
        fileAssociations:
          py: python3 -u


Configuration Options
---------------------

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

  .. note::
     Some Linux distributions no longer have a ``python`` command. In this case you can adapt the file association to ``python3 -u``. Alternatively, on Debian-based distributions you may want to install the ``python-is-python3`` package:

     .. code-block:: shell

        sudo apt install python-is-python3
   
     This package creates a symlink so that ``python`` invokes ``python3``.

  .. note::
      The ``-u`` flag added to the ``python`` command forces Python to run in **unbuffered mode**. This affects how Python handles output streams, allowing Yamcs to capture output in realtime instead of needing to wait until the buffer is full.

impersonateCaller (boolean)
  Scripts receive a transient API key via an environment variable. By default this API key uses the built-in ``System`` user, which provides unrestricted access.

  When this property is enabled, the script receives instead an API key of the user that started the activity.

  Default: ``false``


Activity Options
----------------

script (string)
    **Required.** Script path relative to the :file:`etc/scripts` directory.

args (string, or list of strings)
    Command line arguments.

processor (string)
    If provided this information is passed to the called script as a ``YAMCS_PROCESSOR`` environment variable.


Execution
---------

The script's ``stdout`` and ``stderr`` output streams are captured in the Yamcs activity log. The activity tracks the lifecycle of the subprocess. The exit code of this process determines whether the activity is considered successful or not. A non-zero exit code indicates failure.


Environment Variables
---------------------

Scripts are executed with the following environment variables:

``YAMCS``
    Always set to ``1``.

``YAMCS_INSTANCE``
    Set to the applicable Yamcs instance.

``YAMCS_PROCESSOR``
    Set to the applicable Yamcs processor.

``YAMCS_URL``
    URL that the script can use to reach Yamcs.

    For example: ``http://localhost:8090``

If Yamcs requires authentication, another environment variable is set:

``YAMCS_API_KEY``
    A randomly generated API key that the script may use to authenticate against Yamcs. Keys are invalidated when the script terminates.

    Clients can use an API key by setting the ``x-api-key`` HTTP header on each request. Yamcs will know how to link this back to the appropriate user, which (depending on configuration) could be the script caller, or a generic ``System`` user.


Python Scripts
--------------

Python scripts may use the `Python Yamcs Client <https://docs.yamcs.org/python-yamcs-client/>`_ to access the Yamcs API. This includes a static utility function ``YamcsClient.from_environment()`` that reads aforementioned environment variables, and authenticates when needed.

.. code-block:: python

   from yamcs.client import YamcsClient
   import sys

   # Create a client instance
   client = YamcsClient.from_environment()

   # Print all command-line arguments 
   print(sys.argv)

   # ...

The Python Yamcs Client is something that would need to be installed separate from Yamcs.


Shell Scripts
-------------

Below is an example of a shell script that generates a Yamcs event. Remember to make the file executable.

.. code-block:: shell

   #!/bin/sh
   
   echo "Creating event"
   
   MSG="$(whoami) says hi"
   JSON_STRING=$(printf '{"message": "%s"}' "$MSG")
   
   curl -XPOST $YAMCS_URL/api/archive/$YAMCS_INSTANCE/events \
        --silent -d "$JSON_STRING" --fail-with-body

For authenticated servers, you can specify an additional HTTP header on the curl command:

.. code-block:: shell

   -H "x-api-key: $YAMCS_API_KEY"


Yamcs UI
--------

In the Yamcs UI, choose :menuselection:`Procedures --> Run a script`. A list of the available scripts is displayed, and you can choose to run one **immediately**, providing any script arguments.

To execute a script **at a later time**, choose the option :guilabel:`Run later...`. You will be asked to enter the desired execution time. This will create an activity *item* in the :doc:`../timeline/index`.


Python Yamcs Client
-------------------

Run a script activity immediately:

.. code-block:: python

    from yamcs.client import YamcsClient

    client = YamcsClient("localhost:8090")
    processor = client.get_processor("simulator", "realtime")

    # Simulate LOS for 5 seconds
    # (the run_script call does not block)
    processor.run_script("simulate_los.py", "--duration 5")


Run a script activity one minute from now:

.. code-block:: python

    from datetime import datetime, timedelta, timezone

    from yamcs.client import ScriptActivity, Item, YamcsClient

    client = YamcsClient("http://localhost:8090")
    timeline = client.get_timeline_client("simulator")

    now = datetime.now(tz=timezone.utc)

    item = Item()
    item.start = now + timedelta(minutes=1)
    item.duration = timedelta(seconds=5)  # Planned duration
    item.activity = ScriptActivity(
        script="simulate_los.py",
        args="--duration 5",
        processor="realtime",
    )
    timeline.save_item(item)
