yamcsadmin password-hash
========================

.. program:: yamcsadmin password-hash

Synopsis
--------

.. rst-class:: synopsis

    | **yamcsadmin** password-hash


Description
-----------

Prompts to enter and confirm a password, and generates a randomly salted PBKDF2 hash of this password. This hash may be used in :file:`etc/users.yaml` instead of the actual password, and allows verifying user passwords without storing them.


Environment
-----------

.. describe:: YAMCSADMIN_PASSWORD

   Provide the password through the environment, thereby avoiding prompts.
