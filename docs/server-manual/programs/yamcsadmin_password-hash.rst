yamcsadmin password-hash
========================

.. program:: yamcsadmin password-hash

**NAME**

    yamcsadmin password-hash - Generate password hash for use in users.yaml


**SYNOPSIS**

    ``yamcsadmin password-hash``


**DESCRIPTION**

    Prompts to enter and confirm a password, and generates a randomly salted PBKDF2 hash of this password. This hash may be used in users.yaml instead of the actual password, and allows verifying user passwords without storing them.

    The command may be used in non-interactive mode by setting the password with the environment variable ``YAMCSADMIN_PASSWORD``
