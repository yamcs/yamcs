Activity Triggers
=================

A trigger starts an activity automatically when a monitoring condition occurs. The condition is either:

* an **alarm notification**: a parameter or event alarm is triggered, increases in severity, returns to normal, etc.
* a **parameter condition**: a boolean expression over parameters becomes true (or false).

The started activity can be of any type: a command, a stack, a script, a manual activity, or a type added by a plugin. Triggered activities appear on the Activities page like any other activity, with a reference to the trigger that started them.

Triggers are only evaluated on a realtime processor (by default ``realtime``). Replays never start activities.

.. note::
    Triggers require the :doc:`Trigger Service <../services/instance/trigger-service>` to be configured for the instance.


Conditions
----------

.. rubric:: Alarm condition

subjects (string or list of strings)
    Glob patterns matched against the alarm subject. For parameter alarms, this is the qualified parameter name. For event alarms, this is ``source/type``. ``*`` matches any sequence of characters, ``?`` matches a single character. If not specified, any alarm of the given type matches.

type (string)
    One of ``PARAMETER`` or ``EVENT``. Default: ``PARAMETER``

notifications (list of strings)
    The alarm notifications that make the trigger fire. Possible values: ``TRIGGERED``, ``SEVERITY_INCREASED``, ``VALUE_UPDATED``, ``ACKNOWLEDGED``, ``RTN``, ``CLEARED``, ``RESET``, ``SHELVED``, ``UNSHELVED``.

    Default: ``[TRIGGERED, SEVERITY_INCREASED]``

minSeverity (string)
    Minimum alarm severity: ``WATCH``, ``WARNING``, ``DISTRESS``, ``CRITICAL`` or ``SEVERE``. The severity of an alarm is the highest severity it reached. Default: ``WATCH``

includeShelved (boolean)
    Whether shelved alarms can make the trigger fire. Default: ``false``

Alarms that are already active when the server starts, or when the trigger is enabled, do not make the trigger fire.


.. rubric:: Parameter condition

expression (string)
    **Required.** Boolean expression over parameters. See :ref:`trigger-expressions`.

edge (string)
    When to fire: ``RISING`` (expression becomes true), ``FALLING`` (expression becomes false) or ``BOTH``. Default: ``RISING``

holdTime (duration)
    The expression must keep its new state for this long before the trigger fires. Use this to ignore short glitches. Default: ``0``

invalidAsFalse (boolean)
    Whether a parameter value that is not acquired (for example expired or invalid) makes the expression false. If ``false``, such values leave the state unchanged. Default: ``true``

The first evaluation after the trigger starts only establishes the initial state, and never fires.


.. _trigger-expressions:

Expressions
-----------

Expressions compare parameter values to literals or to other parameters:

.. code-block:: text

    /YSS/SIMULATOR/BatteryVoltage2 < 10 && /YSS/SIMULATOR/Mode == "SAFE"

* Logical operators: ``AND`` or ``&&``, ``OR`` or ``||``, ``NOT`` or ``!``, and parentheses. ``AND`` has higher precedence than ``OR``.
* Comparison operators: ``==`` (or ``=``, ``EQ``), ``!=`` (``NE``), ``<`` (``LT``), ``<=`` (``LE``), ``>`` (``GT``), ``>=`` (``GE``).
* The left side is a qualified parameter name. Add a member path for aggregate or array parameters (``/a/b.member[2]``), and the suffix ``.raw`` to compare the raw value instead of the engineering value.
* The right side is a value (quoted with ``"`` if it contains spaces or special characters), ``true``/``false``, or another parameter prefixed with ``$`` (``/a/b > $/a/c``).

Expressions in the spreadsheet syntax are also accepted, for example ``&(/a/b < 10; /a/c == "SAFE")``.


Policy
------

cooldown (duration)
    Minimum time between two firings. Firings during the cooldown are recorded but do not start an activity. Default: ``60s``

concurrency (string)
    What to do when the trigger fires while its previous activity is still running (or still waiting for confirmation):

    * ``SKIP``: do not start a new activity
    * ``ALLOW``: start a new activity anyway
    * ``CANCEL_PREVIOUS``: cancel the running activity, then start a new one

    Default: ``SKIP``

requireConfirmation (boolean)
    Wait for an operator to confirm before starting the activity. Pending confirmations can be confirmed or rejected through the API. The activity then runs on behalf of the confirming user. Default: ``false``

confirmationTimeout (duration)
    Time after which an unconfirmed firing expires. Default: ``5m``

maxFiringsPerHour (integer)
    Maximum number of activities started (or pending) in any hour. ``0`` means no limit. Default: ``0``

Durations are specified as a number of seconds, or as a string with a unit: ``500ms``, ``30s``, ``10m``, ``1h``, ``1d``. In the HTTP API, durations are always in milliseconds.


Activity
--------

type (string)
    **Required.** Activity type, for example ``COMMAND``, ``STACK``, ``SCRIPT`` or ``MANUAL``.

args (map)
    Activity arguments, as for starting the activity directly. See :doc:`commands`, :doc:`stacks` and :doc:`scripts`.

label (string)
    Activity label. Default: the trigger name.

String arguments may contain placeholders that are replaced when the trigger fires:

.. list-table::
    :widths: 35 65

    * - ``${trigger.name}``, ``${trigger.id}``
      - The trigger
    * - ``${processor}``
      - The monitored processor
    * - ``${value}``
      - Triggering value. For parameter alarms, the current value of the parameter. For event alarms, the event message. For parameter conditions, the value of the first parameter in the expression.
    * - ``${alarm.subject}``, ``${alarm.severity}``, ``${alarm.notification}``, ``${alarm.type}``, ``${alarm.seqNum}``
      - Alarm details (alarm conditions only)
    * - ``${event.source}``, ``${event.type}``, ``${event.message}``, ``${event.severity}``
      - Event details (event alarm conditions only)
    * - ``${condition.expression}``, ``${condition.edge}``
      - Expression details (parameter conditions only)
    * - ``${param:/qualified/name}``
      - Engineering value of a parameter. For parameters referenced by the condition, this is the value at the time of firing. For other parameters, it is the latest known value.

If a string consists of a single placeholder, the value keeps its type (for example a number for a command argument). Use ``$${`` for a literal ``${``. A placeholder that cannot be resolved makes the firing fail.


Safety
------

* Each trigger can be enabled or disabled individually.
* All triggers can be stopped at once with ``POST /api/activities/{instance}/triggers:disableAll``. This setting is remembered across restarts.
* Each trigger runs its activity on behalf of a user: the ``runAs`` user if set, otherwise the user who created or last updated the trigger. Triggers from the configuration without ``runAs`` use the built-in ``System`` user. Only superusers can set ``runAs`` to another user. If the user no longer exists, is inactive or lacks the ``ControlActivities`` privilege, the firing fails.
* Each firing is recorded, including those that were suppressed. Started, pending, failed and expired firings also generate an event with source ``ActivityTrigger``.


Configuration
-------------

Triggers can be created through the HTTP API, or declared in the service configuration. Triggers from the configuration cannot be modified or deleted through the API, but they can be enabled and disabled until the next restart.

.. code-block:: yaml
    :caption: :file:`etc/yamcs.{instance}.yaml`

    services:
      - class: org.yamcs.activities.triggers.TriggerService
        args:
          triggers:
            - name: battery-critical-safe-mode
              condition:
                alarm:
                  subjects: /YSS/SIMULATOR/BatteryVoltage*
                  minSeverity: CRITICAL
              policy:
                cooldown: 10m
                requireConfirmation: true
                confirmationTimeout: 2m
              activity:
                type: STACK
                args:
                  bucket: stacks
                  stack: safe-mode.ycs

            - name: low-voltage-diagnostics
              condition:
                parameter:
                  expression: /YSS/SIMULATOR/BatteryVoltage2 < 10 && /YSS/SIMULATOR/Mode == "NOMINAL"
                  holdTime: 5s
              activity:
                type: SCRIPT
                args:
                  script: collect_eps.py
                  args: ["--reason", "${trigger.name}", "--value", "${value}"]

Each trigger accepts these options, in addition to ``condition`` (with either ``alarm`` or ``parameter``), ``policy`` and ``activity``:

name (string)
    **Required.** Unique name of the trigger.

description (string)
    Optional description.

enabled (boolean)
    Whether the trigger is enabled when the server starts. Default: ``true``

processor (string)
    Processor to monitor. Default: ``realtime``

runAs (string)
    User on whose behalf the activity is started. Default: the built-in ``System`` user.
