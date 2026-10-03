OpenID Connect AuthModule
=========================

This AuthModule supports federated identity by redirecting web application users to the authorization (or consent) page of an OpenID Connect server. This allows for remote management of users and could be used to perform cross-domain Single Sign On with multiple other browser applications.

This AuthModule is used for authentication only. It does not directly support importing roles. But you could do so by extending this module.

If the token endpoint of the OpenID server provides a refresh token, then Yamcs will refresh the access token whenever it has expired.

If the token endpoint of the OpenID server does not provide a refresh token, Yamcs will only interact once with the OpenID server (for the initial auth), and afterwards no longer.


Class Name
----------

:javadoc:`org.yamcs.security.OpenIDAuthModule`


Configuration Options
---------------------

issuer (string)
    The Issuer Identifier of the OpenID server, for example ``https://keycloak.example.com/realms/myrealm``. If set, Yamcs retrieves the endpoints of the OpenID server from ``<issuer>/.well-known/openid-configuration`` (OpenID Connect Discovery). The ``issuer`` in that document must exactly match this value.

    This URL must be accessible by Yamcs itself. If the OpenID server cannot be reached during startup, Yamcs logs a warning and retries when needed.

authorizationEndpoint (string)
    The URL of the OpenID server page where to redirect users for authorization and/or consent.

    This URL must be accessible by clients.

    **Required** unless ``issuer`` is set. If set together with ``issuer``, this value overrides the discovered endpoint. This is useful when Yamcs and clients reach the OpenID server under a different hostname.

tokenEndpoint (string)
    The URL of the OpenID server page where OAuth2 tokens can be retrieved.

    This URL must be accessible by Yamcs itself.

    **Required** unless ``issuer`` is set. If set together with ``issuer``, this value overrides the discovered endpoint.

endSessionEndpoint (string)
    The URL of the OpenID server page where to redirect users when they sign out of Yamcs. This corresponds to the ``end_session_endpoint`` of OpenID Connect RP-Initiated Logout. If set together with ``issuer``, this value overrides the discovered endpoint.

    This URL must be accessible by clients.

clientId (string)
    **Required.** An identifier that identifies your Yamcs server installation as a client against the Open ID server. This should be set up using the configuration tools of the Open ID server.

clientSecret (string)
    **Required.** The secret matching with the ``clientId``.

scope (string)
    Space-separated scope to be used in authorization request. Default: ``openid email profile``

attributes (map)
    Configure how claims are mapped to Yamcs attributes. If unset, Yamcs uses defaults that work out of the box against some common OpenID Connect providers.

verifyTls (boolean)
    If false, disable TLS and hostname verification when Yamcs uses the token endpoint, or retrieves the discovery document. Default: true.


Attributes sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

name (string or string[])
    The claim that matches with the account name. This is used internally by Yamcs to map the user to a single identity. If multiples are defined, they are tried in order. Default: ``[preferred_username, nickname, email]``.

email (string or string[])
    The claim that matches with the email. If multiples are defined, they are tried in order. Default: ``email``.

displayName (string or string[])
    The claim that matches with the display name. If multiples are defined, they are tried in order. Default: ``name``.


Examples
--------

AuthModules are configured in the file :file:`etc/security.yaml`.

With discovery, endpoints are retrieved from the OpenID server:

.. code-block:: yaml

    authModules:
      - class: org.yamcs.security.OpenIDAuthModule
        args:
          issuer: https://keycloak.example.com/realms/myrealm
          clientId: yamcs
          clientSecret: changeme

Without discovery, endpoints are configured explicitly:

.. code-block:: yaml

    authModules:
      - class: org.yamcs.security.OpenIDAuthModule
        args:
          authorizationEndpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/auth
          tokenEndpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/token
          endSessionEndpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/logout
          clientId: yamcs
          clientSecret: changeme


RP-Initiated Logout
-------------------

If an end session endpoint is configured with ``endSessionEndpoint``, or discovered through ``issuer``, a user that signs out of the Yamcs web interface is redirected to the OpenID server so that the session is ended there as well. Yamcs includes the ``id_token_hint``, ``client_id`` and ``post_logout_redirect_uri`` parameters, which allows the OpenID server to end the session without asking the user for confirmation.

The ``post_logout_redirect_uri`` is the root URL of the Yamcs web interface (for example ``http://localhost:8090/``). This URL must be registered as a valid post logout redirect URI at the OpenID server.

If no end session endpoint is available, signing out of Yamcs does not end the session at the OpenID server.

For sessions established through this AuthModule, this redirect takes precedence over the ``logoutRedirectUrl`` option of the Yamcs web interface.


Back-channel Logout
-------------------

This AuthModule adds an endpoint ``/openid/backchannel-logout`` to Yamcs that may be called by the OpenID server when a user is to be logged out. This is called back-channel because the communication is directly from the Open ID server to Yamcs, rather than via the user agent. If not used, a logout on the Open ID server is only detected when the next token refresh is attempted.


Note to third-party developers
------------------------------

This AuthModule follows the conventions for server-side web applications: the ``id_token`` is retrieved and decoded by Yamcs only, and never reaches the browser. A browser application that wants users to log in through the OpenID server must obtain an authorization code, and then let Yamcs exchange that code for a Yamcs access token.

The source code of the Yamcs web interface serves as the reference implementation. In short:

#. Retrieve the OpenID Connect options from the ``/auth`` endpoint. The response contains an ``openid`` object with the properties ``clientId``, ``authorizationEndpoint`` and ``scope``. If the ``openid`` object is missing, the OpenID server is currently not available to Yamcs (for example because discovery has not yet succeeded).

#. Redirect the browser to the ``authorizationEndpoint``:

   .. code-block:: JavaScript

       const { clientId, authorizationEndpoint, scope } = authInfo.openid;
       window.location.href = authorizationEndpoint +
               "?client_id=" + encodeURIComponent(clientId) +
               "&state=" + encodeURIComponent(state) +
               "&response_mode=query" +
               "&response_type=code" +
               "&scope=" + encodeURIComponent(scope) +
               "&redirect_uri=" + encodeURIComponent(redirectUri);

   ``state`` can be anything. It is typically used to remember the originally requested page, so that the user can be sent back there when the login has completed.

   ``redirectUri`` is the URL where the OpenID server sends the browser back to after the user has logged in. It must be registered as a valid redirect URI at the OpenID server.

#. When the browser arrives at ``redirectUri``, read the ``code`` and ``state`` query parameters.

#. Wrap the ``code`` in an unsigned JSON Web Token, together with the ``redirectUri``, and prefix it with ``oidc``:

   .. code-block:: JavaScript

       const header = base64url(JSON.stringify({ alg: "none" }));
       const payload = base64url(JSON.stringify({ code, redirect_uri: redirectUri }));
       const codeForYamcs = "oidc " + header + "." + payload + ".";

   Here ``base64url`` encodes a string using the URL-safe Base64 alphabet, without padding. The token does not need to be signed: Yamcs does not trust its content, but uses the code to retrieve the ``id_token`` directly from the OpenID server.

#. Exchange this value for a Yamcs access token by sending a form-encoded ``POST`` request to ``/auth/token`` with ``grant_type=authorization_code`` and ``code=<codeForYamcs>``. The JSON response contains an ``access_token``, its lifetime in seconds (``expires_in``) and a ``refresh_token``.

   Yamcs sends the code and the ``redirect_uri`` to the token endpoint of the OpenID server. The OpenID server requires this ``redirect_uri`` to be identical to the one that was used in step 2. Yamcs also uses it for later token refreshes, and to determine where the user returns after logging out of the OpenID server: the parent path of the ``redirect_uri``.

#. Use the access token with an ``Authorization: Bearer <access_token>`` header. Before it expires, send a ``POST`` request to ``/auth/token`` with ``grant_type=refresh_token`` and ``refresh_token=<refresh_token>`` to obtain a new access token. Each refresh token can be used only once: the response contains a new one.

#. To log out, send a form-encoded ``POST`` request to ``/auth/logout`` with ``refresh_token=<refresh_token>``. This ends the Yamcs session. If the response contains a ``redirectUrl``, redirect the browser there to also end the session at the OpenID server (see `RP-Initiated Logout`_).
