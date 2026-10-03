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

This AuthModule implements the conventions for server-side web applications. In other words: the ``id_token`` is retrieved and decoded on Yamcs server only. Before Yamcs can obtain the ``id_token`` it expects to be given some information by the integrating application.

The source code of the Yamcs UI serves as the best reference. But generally it works like this:

#. The browser application retrieves OpenID Connect options on the ``/auth`` endpoint. This includes the ``client_id``, the ``authorizationEndpoint`` and the ``scope``. Other configuration options are reserved for server use.

#. The browser application uses the ``authorizationEndpoint`` to redirect the browser to a login or consent page of the  upstream OIDC server. The following is an example:
   
   .. code-block:: JavaScript

       window.location.href = "https://oidc.example.com" +
               "?client_id=encodeURIComponent(CLIENT_ID)" +
               "&state=encodeURIComponent(STATE)" +
               "&response_mode=query" +
               "&response_type=code" +
               "&scope=openid+email+profile" +
               "&redirect_uri=encodeURIComponent(REDIRECT_URI)";
    
   ``STATE`` can be anything, and is typically used for encoding information about the original request such that when the authentication is done, the user is redirected back to the original attempted route.

   ``REDIRECT_URI`` is the path where OIDC will redirect back the user after the login or consent is confirmed.

#. When OIDC redirects the user's browser back to REDIRECT_URI, extract the ``code`` and ``state`` from the query params.

#. Use this upstream ``code`` to make an encoded string like this:

   .. code-block:: JavaScript

       var codeForYamcs = "oidc " + JWT;

   Here, JWT represent a JSON Web Token that stringifies a payload containing at least these properties:

   .. code-block:: text

       {
         "redirect_uri": REDIRECT_URI,
         "code": UPSTREAM_CODE,
       }

#. The string value of the variable ``codeForYamcs`` can be used against the Yamcs ``/auth`` endpoint using ``grant_type=authorization_code`` for converting it to a standard Yamcs-level access token.

   In the background what happens is that Yamcs will use the upstream code and exchange it against OIDC for an ``id_token`` which tells Yamcs what the username, email and display name are for the authenticated user. The ``redirect_uri`` property is not actually used anymore, but most OIDC servers will check on this being specified and matching the original ``redirect_uri`` used for obtaining the upstream code.
