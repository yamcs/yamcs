Commanding
==========

Yamcs supports XTCE concepts for commanding. Commands have constraints (preconditions) and verifiers (postconditions). The constraints are checked before issuing an command and the verifiers are run after the command has been issued to verify the different stages of execution.

In addition to the constraints/verifiers, Yamcs also implements the concept of command queue. This allows an operator to inspect commands of other users before being issued. It also allows to block completely commands from users during certain intervals (this effect can also be obtained with a constraint).

The commands and arguments are formatted to binary packets based on the XTCE definition.


Command Significance
--------------------

Yamcs uses the XTCE concept of command significance. Each command's significance can have one of this values none (default), watch, warning, distress, critical or severe.

In addition to the significance, the command has a message explaining why the command has the given significance.

Currently, Yamcs Server does not check or impose anything based on the significance of the command. In the future, the privileges may be used to restrict users that can send commands of high significance. However, currently the information (significance + reason) is only given to an external application (Yamcs Studio) to present it to the user in a suitable manner.

The command significance can be defined in the Excel Spreadsheet in the CommandOptions tab:

.. image:: _images/significance.png
    :alt: Significance
    :align: center


Command Queues
--------------

When a command is issued, it must first pass by a queue. Privileges are checked before the command is put into the queue, so if the user does not have the privilege for the given command, the command is rejected before even reaching the queue.

The available queues are defined in the file :file:`etc/command-queue.yaml`.

.. code-block:: yaml

    supervised:
      state: blocked
      minLevel: critical

    default:

If this file is absent, a default queue is created, equivalent to this configuration:

.. code-block:: yaml

    default:

Queues can be in one of three states: ``enabled``, ``blocked`` or ``disabled``. When the state is not specified in the :file:`etc/command-queue.yaml` configuration file, the latest state will be remembered across server restarts, defaulting to ``enabled``. If there is a configured state, that will always be applied as the initial state of that queue.

Each queue has optional conditions. Issued commands are offered to the first queue (in definition order) whose conditions match the command.

The conditions are:

* | **minLevel** (one of watch, warning, distress, critical or severe)
  | Match only commands that are at least as significant as ``minLevel``.

* | **users** (list of usernames)
  | Match only commands that are issued by one of the specified users.

* | **groups** (list of group names)
  | Match only commands that are issued by one of the specified groups.

* | **tcPatterns** (list of command name patterns)
  | Match only commands whose qualified name matches any of the specified patterns.

The conditions ``users`` and ``groups`` are evaluated together: it suffices if the issuer matches with one of these two conditions. All other conditions must all apply, before a command can be matched to the queue.

At runtime, a queue can perform different actions on matched commands:

* | **ACCEPT** (state: ``enabled``)
  | Matched commands are immediately released.

* | **HOLD** (state: ``blocked``)
  | Matched commands are accepted into the queue but need to be manually released.

* | **REJECT** (state: ``disabled``)
  | Matched commands are immediately rejected.

The queue action can be changed dynamically by users with the ``ControlCommandQueue`` privilege.


Transmission Constraints
------------------------

When the is set to be released from the queue (either manually by an operator or automatically because the queue was in the Enabled state), the transmission constraints are verified.

The command constraints are conditions set on parameters that have to be met in order for the command to be released. There can be multiple constraints for each command and each constraint can specify a timeout which means that the command will fail if the constraint is not met within the timeout. If the timeout is 0, the condition will be checked immediately.

The transmission constraints can be defined in the Excel Spreadsheet in the CommandOptions tab.

.. image:: _images/constraints.png
    :alt: Constraints
    :align: center

Currently it is only possible to specify the transmission constraints based on parameter verification. This corresponds to  Comparison and ComparisonList in XTCE. In the future it will be possible to specify transmission constraints based on algorithms. That will allow for example to check for specific values of arguments (i.e. allow a command to be sent if ``cmdArgX > 3``).
