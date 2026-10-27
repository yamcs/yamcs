import { APP_BASE_HREF } from '@angular/common';
import { inject, OnDestroy, Service } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import {
  AuthInfo,
  HttpHandler,
  OpenIDConnectInfo,
  TokenResponse,
  UserInfo,
} from '../client';
import { User } from '../User';
import * as utils from '../utils';
import { ConfigService } from './config.service';
import { Synchronizer } from './synchronizer.service';
import { YamcsService } from './yamcs.service';

export interface Claims {
  iss: string;
  sub: string;
  iat: number;
  exp: number;
}

@Service()
export class AuthService implements OnDestroy {
  private yamcsService = inject(YamcsService);
  private configService = inject(ConfigService);
  private router = inject(Router);
  private baseHref = inject(APP_BASE_HREF);
  private synchronizer = inject(Synchronizer);

  private authInfo: AuthInfo;
  public user$ = new BehaviorSubject<User | null>(null);

  private syncSubscription: Subscription;
  private nextRefresh: Date | null;

  // Set when the session was ended by an explicit sign-out, or by the
  // server. The resulting navigation is then handled explicitly, rather
  // than by automatic redirects to the login page.
  private sessionEnded = false;

  // Optional logout page where to redirect the browser after logging out of Yamcs
  // If unset, defaults to a local login page.
  private logoutRedirectUrl?: string;

  constructor() {
    this.authInfo = this.configService.getAuthInfo();
    this.logoutRedirectUrl = this.configService.getConfig().logoutRedirectUrl;

    this.yamcsService.sessionEnded$.subscribe((ended) => {
      if (ended && !this.authInfo.spnego && !this.sessionEnded) {
        this.onSessionEnded();
      }
    });

    /*
     * Attempts to prevent 401 exceptions by checking if locally available
     * tokens should (still) work. If not, then the user is navigated
     * to the login page.
     */
    this.yamcsService.yamcsClient.setHttpInterceptor(
      async (next: HttpHandler, url: string, init?: RequestInit) => {
        try {
          // Verify or fetch a token when necessary
          await this.loginAutomatically();
        } catch (err: any) {
          throw this.handleRequestFailure(url, err);
        }

        // Kept outside the try blocks: an error while preparing the request
        // is a client bug, and must not be reported as the server being down.
        init = this.modifyRequest(init);

        let response: Response;
        try {
          response = await next.handle(url, init);
          if (response.status === 401) {
            // Server must have refused our access token. Attempt to refresh.
            this.clearCookie('access_token');
            await this.loginAutomatically();
          }
        } catch (err: any) {
          throw this.handleRequestFailure(url, err);
        }

        if (response.status === 401) {
          init = this.modifyRequest(init);
          response = await next.handle(url, init);
        }

        return response;
      },
    );

    const accessToken = this.getCookie('access_token');
    if (accessToken) {
      // User visited the page with a prior established access token. We don't
      // know its exact expiration, but start the refresh cycle shortly after
      // page load.
      this.nextRefresh = new Date();
      this.nextRefresh.setTime(this.nextRefresh.getTime() + 10000);
    }

    // Proactively extends a login session when it's close to being expired.
    this.syncSubscription = this.synchronizer.syncSlow(() => {
      if (this.nextRefresh) {
        const now = new Date().getTime();
        if (now >= this.nextRefresh.getTime()) {
          this.nextRefresh = null;
          this.loginAutomatically(true /* refresh */);
        }
      }
    });
  }

  /**
   * Navigates away after a failed request or login attempt, and returns
   * the error for rethrowing.
   */
  private handleRequestFailure(url: string, err: any) {
    // Log the cause, because the navigation below hides it from the user
    console.error(`Request to ${url} failed`, err);
    if (err.name === 'TypeError') {
      // TypeError is how Fetch API reports network or CORS failure
      this.router.navigate(['/down'], { skipLocationChange: true });
    } else {
      this.logout(true);
    }
    return err;
  }

  /**
   * Adds an authorization header to an HTTP request.
   */
  private modifyRequest(init?: RequestInit): RequestInit | undefined {
    const accessToken = this.getCookie('access_token');
    if (!accessToken) {
      return init;
    }

    const headers = new Headers(init?.headers);
    headers.set('Authorization', `Bearer ${accessToken}`);
    return { ...init, headers };
  }

  getUser() {
    return this.user$.value;
  }

  /**
   * Aims to establish a login session without needing to ask
   * the user for credentials. This will re-use locally available
   * tokens in order to limit server calls.
   *
   * The promise will be rejected when the automatic login failed.
   */
  public async loginAutomatically(refresh = false): Promise<any> {
    if (
      !this.authInfo.requireAuthentication ||
      this.configService.getDisableLoginForm()
    ) {
      if (!this.user$.value) {
        // Written such that it bypasses our interceptor
        const response = await fetch(`${this.baseHref}api/user`);
        this.user$.next(new User((await response.json()) as UserInfo));
      }
      return;
    }

    // Use already available tokens when we can
    const accessToken = this.getCookie('access_token');
    const refreshToken = this.getCookie('refresh_token');
    if (accessToken && !refresh) {
      if (!this.user$.value) {
        // Written such that it bypasses our interceptor
        const headers = new Headers();
        headers.append('Authorization', `Bearer ${accessToken}`);
        const response = await fetch(`${this.baseHref}api/user`, { headers });
        if (response.status === 200) {
          const user = new User((await response.json()) as UserInfo);
          this.user$.next(user);
        } else if (response.status === 401) {
          if (refreshToken) {
            this.clearCookie('access_token');
            try {
              return await this.loginWithRefreshToken(refreshToken);
            } catch {
              console.log('Server refused our refresh token');
            }
          }
          this.logout(false);
          return await this.loginAutomatically();
        } else {
          return Promise.reject(
            'Unexpected response when retrieving user info',
          );
        }
      }

      return this.extractClaims(this.getCookie('access_token')!);
    } else if (refreshToken) {
      try {
        return await this.loginWithRefreshToken(refreshToken);
      } catch {
        console.log('Server refused our refresh token');
        this.logout(false);
      }
    }

    // If server supports spnego, attempt browser negotiation.
    // This is done before any other auth attempts, because it does not
    // require user intervention when successful.
    if (this.authInfo.spnego) {
      try {
        return await this.loginWithSpnego();
      } catch {
        // Ignore
      }
    }

    this.logout(true);
    throw new Error('Could not login automatically');
  }

  /**
   * Logs in via user-provided username/password credentials. This would have
   * to come from our login page.
   */
  public login(username: string, password: string) {
    return this.yamcsService.yamcsClient
      .fetchAccessTokenWithPassword(username, password)
      .then((loginInfo) => {
        this.updateLoginState(loginInfo);
        this.user$.next(new User(loginInfo.user));
        return this.extractClaims(loginInfo.access_token);
      });
  }

  public loginWithAuthorizationCode(authorizationCode: string) {
    return this.yamcsService.yamcsClient
      .fetchAccessTokenWithAuthorizationCode(authorizationCode)
      .then((loginInfo) => {
        this.updateLoginState(loginInfo);

        this.user$.next(new User(loginInfo.user));
        return this.extractClaims(loginInfo.access_token);
      });
  }

  private async loginWithSpnego() {
    const response = await fetch('/auth/spnego', {
      credentials: 'include',
    });
    if (response.status === 200) {
      const authorizationCode = (await response.text()).trim();
      return await this.loginWithAuthorizationCode(authorizationCode);
    } else {
      throw new Error('SPNEGO authentication failed');
    }
  }

  private loginWithRefreshToken(refreshToken: string) {
    // Store in cookie so that the token survives browser refreshes
    // and so it is added to the header of a websocket request.
    return this.yamcsService.yamcsClient
      .fetchAccessTokenWithRefreshToken(refreshToken)
      .then((loginInfo) => {
        this.updateLoginState(loginInfo);
        this.user$.next(new User(loginInfo.user));
        return this.extractClaims(loginInfo.access_token);
      });
  }

  /*
   * Store in cookie so that the token survives browser refreshes and so it
   * is added to the header of a websocket request.
   */
  private updateLoginState(tokenResponse: TokenResponse) {
    const expireMillis = tokenResponse.expires_in * 1000;
    const cookieExpiration = new Date();
    cookieExpiration.setTime(cookieExpiration.getTime() + expireMillis);
    let cookie = `access_token=${encodeURIComponent(tokenResponse.access_token)}`;
    cookie += `; expires=${cookieExpiration.toUTCString()}`;
    cookie += `; path=${this.getCookiePath()}`;
    const cookieConfig = this.configService.getConfig().cookie;
    cookie += `; SameSite=${cookieConfig.sameSite}`;
    if (cookieConfig.secure) {
      cookie += '; Secure';
    }
    document.cookie = cookie;

    // Store refresh token in a Session Cookie (bound to browser, not tab)
    if (tokenResponse.refresh_token) {
      cookie = `refresh_token=${encodeURIComponent(tokenResponse.refresh_token)}`;
      cookie += `; path=${this.getCookiePath()}`;
      cookie += `; SameSite=${cookieConfig.sameSite}`;
      if (cookieConfig.secure) {
        cookie += '; Secure';
      }
      document.cookie = cookie;
    }

    // Schedule a refresh before the new access token expires.
    // Do this even when there is no refresh token
    // (SPNEGO is an alternative way of refreshing)
    this.nextRefresh = new Date(cookieExpiration.getTime() - 20000);
  }

  /**
   * Clear all data from a previous login session
   */
  logout(navigateToLoginPage: boolean) {
    this.nextRefresh = null;
    this.clearCookie('access_token');
    this.clearCookie('refresh_token');

    this.yamcsService.clearContext(); // TODO needed here?
    this.user$.next(null);

    // TODO should be closed from server side
    // (once it better supports OIDC-like sessions)
    this.yamcsService.yamcsClient.closeWebSocketClient();

    // Ending the session triggers session-end messages and 401 responses.
    // These must not compete with the navigation done by signOut, or
    // navigate away from the signed-out page.
    if (navigateToLoginPage && !this.sessionEnded) {
      this.redirectToLogin();
    }
  }

  /**
   * Explicit sign-out by the user. Ends the server-side session, and,
   * if applicable, the session at the upstream identity provider.
   */
  async signOut() {
    this.sessionEnded = true;
    let redirectUrl: string | undefined;
    const refreshToken = this.getCookie('refresh_token');
    if (refreshToken) {
      try {
        const response =
          await this.yamcsService.yamcsClient.logout(refreshToken);
        redirectUrl = response.redirectUrl;
      } catch (err) {
        console.warn('Failed to end server-side session', err);
      }
    }

    this.logout(false);

    if (redirectUrl) {
      window.location.href = redirectUrl;
    } else if (this.logoutRedirectUrl) {
      window.location.href = this.logoutRedirectUrl;
    } else {
      this.redirectToLogin('/');
    }
  }

  /**
   * Handles a session that was ended by the server, for example following
   * a logout at the OpenID server.
   *
   * Rather than redirecting to the login page, the user is asked to sign in
   * again. An immediate redirect may reach the OpenID server before it has
   * fully ended its own session, which would log the user back in.
   */
  private onSessionEnded() {
    const next = this.router.url;
    this.sessionEnded = true;
    this.logout(false);
    this.router.navigate(['/signed-out'], { state: { next } });
  }

  /**
   * Navigates the browser to where the user can log in.
   *
   * When an OpenID server is configured, this is always the OpenID server.
   * Else this is the login form, or the logoutRedirectUrl if the login form
   * is disabled.
   *
   * @param next client-side URL to return to after a successful login.
   * Defaults to the current URL.
   */
  redirectToLogin(next = this.router.url) {
    const openid = this.authInfo.openid;
    if (openid) {
      // Let Yamcs redirect to the OpenID server. This works even when the
      // authorization endpoint was not known when this page was loaded.
      window.location.href = this.buildRedirector(
        {
          ...openid,
          authorizationEndpoint: `${location.protocol}//${location.host}${this.baseHref}openid/authorize`,
        },
        this.buildServerSideOpenIDRedirectURI(),
        next,
      );
    } else if (!this.configService.getConfig().disableLoginForm) {
      window.location.href = this.buildRedirector(
        {
          clientId: 'yamcs-web',
          authorizationEndpoint: `${location.protocol}//${location.host}${this.baseHref}auth/authorize`,
          scope: 'openid',
        },
        this.buildOpenIDRedirectURI(),
        next,
      );
    } else if (this.logoutRedirectUrl) {
      window.location.href = this.logoutRedirectUrl;
    }
  }

  public buildOpenIDRedirectURI() {
    return `${location.protocol}//${location.host}${this.baseHref}cb`;
  }

  public buildServerSideOpenIDRedirectURI() {
    return `${location.protocol}//${location.host}${this.baseHref}oidc-browser-callback`;
  }

  private buildRedirector(
    openid: Required<OpenIDConnectInfo>,
    redirectURI: string,
    next: string,
  ) {
    // The client-side URL gets passed as state. When the login completes,
    // we get it back on the callback route, together with a code that we
    // can exchange for a Yamcs access token.
    const state = utils.toBase64URL(next);

    let url = openid.authorizationEndpoint;
    url += `?client_id=${encodeURIComponent(openid.clientId)}`;
    url += `&state=${state}`;
    url += '&response_mode=query';
    url += '&response_type=code';
    url += `&scope=${encodeURIComponent(openid.scope)}`;
    url += `&redirect_uri=${encodeURIComponent(redirectURI)}`;

    return url;
  }

  private extractClaims(jwt: string): Claims {
    const base64Url = jwt.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(window.atob(base64));
  }

  private getCookie(name: string) {
    const value = '; ' + document.cookie;
    const parts = value.split('; ' + name + '=');
    if (parts.length === 2) {
      return parts.pop()!.split(';').shift() || undefined;
    }
    return undefined;
  }

  private clearCookie(name: string) {
    const path = this.getCookiePath();
    if (path) {
      this.clearCookieForPath(name, path);

      // Remove also from root path, to avoid refresh loop when removing a previously
      // used context root.
      if (path !== '/') {
        this.clearCookieForPath(name, '/');
      }
    }
  }

  private clearCookieForPath(name: string, path: string) {
    let cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT`;
    cookie += `; path=${path}`;
    const cookieConfig = this.configService.getConfig().cookie;
    cookie += `; SameSite=${cookieConfig.sameSite}`;
    if (cookieConfig.secure) {
      cookie += '; Secure';
    }
    document.cookie = cookie;
  }

  private getCookiePath() {
    if (this.baseHref === '/') {
      return '/';
    } else if (this.baseHref.endsWith('/')) {
      return this.baseHref.substring(0, this.baseHref.length - 1);
    }
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
  }
}
