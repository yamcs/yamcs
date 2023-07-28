yamcsadmin users
================

.. program:: yamcsadmin users

Synopsis
--------

.. rst-class:: synopsis

    | **yamcsadmin users** add-role <*USERNAME*> --role <*ROLE*>
    | **yamcsadmin users** check-password <*USERNAME*>
    | **yamcsadmin users** create [--email <*EMAIL*>] [--display-name <*NAME*>]
                [--inactive] [--superuser] [--no-password] <*USERNAME*>
    | **yamcsadmin users** delete <*USERNAME*>
    | **yamcsadmin users** describe <*USERNAME*>
    | **yamcsadmin users** list
    | **yamcsadmin users** remove-identity <*USERNAME*> --identity <*IDENTITY*>
    | **yamcsadmin users** remove-role <*USERNAME*> --role <*ROLE*>
    | **yamcsadmin users** reset-password <*USERNAME*>
    | **yamcsadmin users** update [--active true | false]
                [--display-name <*NAME*>] [--email <*EMAIL*>]
                [--superuser true | false] <*USERNAME*>


Description
-----------

User operations.


Commands
--------

.. describe:: add-role <USERNAME> --role <ROLE>

    Add a role to a user.

.. describe:: check-password <USERNAME>

    Check a user's password. This command prompts to enter the user's current password. The command will print if the provided password is correct or not.

    The command may be used in non-interactive mode by setting the password with the environment variable ``YAMCSADMIN_PASSWORD``.

.. describe:: create [--email <EMAIL>] [--display-name <NAME>] [--inactive] [--superuser] [--no-password] <USERNAME>

    Create a new Yamcs user. This prompts for a password.

    The command may be used in non-interactive mode by setting the password with the environment variable ``YAMCSADMIN_PASSWORD``, or using the option ``--no-password``.

.. describe:: delete <USERNAME>

    Delete a user.

.. describe:: describe <USERNAME>

    Describe user details.

.. describe:: list

    List users.

.. describe:: remove-identity <USERNAME> --identity <IDENTITY>

    Remove an identity from a user.

.. describe:: remove-role <USERNAME> --role <ROLE>

    Remove a role from a user.

.. describe:: reset-password <USERNAME>

    Reset a user's password.

.. describe:: update [--active true | false] [--display-name <NAME>] [--email <EMAIL>] [--superuser true | false] <USERNAME>

    Update user details. Prompts to enter and confirm a new user password.

    The command may be used in non-interactive mode by setting the password with the environment variable ``YAMCSADMIN_PASSWORD``.


Options
-------

.. option:: --role <ROLE>

    With ``add-role``, specify the role to be added.

    With ``remove-role``, specify the role to be removed.

.. option:: --display-name <NAME>

    With ``create`` and ``update``, specify the displayed name of the user.

.. option:: --email <EMAIL>

    With ``create`` and ``update``, specify the user email.

.. option:: --inactive

    With ``create``, prevent Yamcs from activating the account.

.. option:: --active true | false

    With ``update``, activate or inactivate the user account.

.. option:: --superuser

    With ``create`` and ``update``, grant this user superuser privileges.

.. option:: --no-password

    With ``create``, indicate that this user should not have a password. This will also bypass the password prompt.


Environment
-----------

.. describe:: YAMCSADMIN_PASSWORD

   Commands that prompt for a password, can alternatively be run in non-interactive mode by specifying this environment variable.
