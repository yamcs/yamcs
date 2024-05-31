import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthInfo, ConfigService, HttpHandler, OpenIDConnectInfo, Synchronizer, TokenResponse, User, UserInfo, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

export interface Claims {
  iss: string;
  sub: string;
  iat: number;
  exp: number;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService implements OnDestroy {

  private authInfo: AuthInfo;
  public user$ = new BehaviorSubject<User | null>(null);

  private syncSubscription: Subscription;
  private nextRefresh: Date | null;

  // Optional logout page where to redirect the browser after logging out of Yamcs
  // If unset, defaults to a local login page.
  private logoutRedirectUrl?: string;

  constructor(
    private yamcsService: YamcsService,
    private configService: ConfigService,
    private router: Router,
    @Inject(APP_BASE_HREF) private baseHref: string,
    synchronizer: Synchronizer,
  ) {
    this.authInfo = configService.getAuthInfo();
    this.logoutRedirectUrl = configService.getConfig().logoutRedirectUrl;

    yamcsService.sessionEnded$.subscribe(ended => {
      if (ended && !this.authInfo.spnego) {
        this.logout(true);
      }
    });

    /*
     * Attempts to prevent 401 exceptions by checking if locally available
     * tokens should (still) work. If not, then the user is navigated
     * to the login page.
     */
    yamcsService.yamcsClient.setHttpInterceptor(async (next: HttpHandler, url: string, init?: RequestInit) => {

      let response;
      try {
        // Verify or fetch a token when necessary
        await this.loginAutomatically();

        init = this.modifyRequest(init);
        response = await next.handle(url, init);
        if (response.status === 401) {
          // Server must have refused our access token. Attempt to refresh.
          this.clearCookie('access_token');
          await this.loginAutomatically();
        }
      } catch (err: any) {
        if (err.name === 'TypeError') { // TypeError is how Fetch API reports network or CORS failure
          this.router.navigate(['/down'], { skipLocationChange: true });
        } else {
          this.logout(true);
        }
        throw err;
      }

      if (response.status === 401) {
        init = this.modifyRequest(init);
        response = await next.handle(url, init);
      }

      return response;
    });

    const accessToken = this.getCookie('access_token');
    if (accessToken) {
      // User visited the page with a prior established access token. We don't
      // know its exact expiration, but start the refresh cycle shortly after
      // page load.
      this.nextRefresh = new Date();
      this.nextRefresh.setTime(this.nextRefresh.getTime() + 10000);
    }

    // Proactively extends a login session when it's close to being expired.
    this.syncSubscription = synchronizer.syncSlow(() => {
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
   * Adds an authorization header to an HTTP request.
   */
  private modifyRequest(init?: RequestInit): RequestInit | undefined {
    const accessToken = this.getCookie('access_token');
    if (accessToken) {
      if (!init) {
        init = { headers: new Headers() };
      } else if (!init.headers) {
        init.headers = new Headers();
      }
      const headers = init.headers as Headers;
      headers.append('Authorization', `Bearer ${accessToken}`);
    }

    return init;
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
    if (!this.authInfo.requireAuthentication || this.configService.getDisableLoginForm()) {
      if (!this.user$.value) {
        // Written such that it bypasses our interceptor
        const response = await fetch(`${this.baseHref}api/user`);
        this.user$.next(new User(await response.json() as UserInfo));
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
          const user = new User(await response.json() as UserInfo);
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
          return Promise.reject('Unexpected response when retrieving user info');
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
    return this.yamcsService.yamcsClient.fetchAccessTokenWithPassword(username, password).then(loginInfo => {
      this.updateLoginState(loginInfo);
      this.user$.next(new User(loginInfo.user));
      return this.extractClaims(loginInfo.access_token);
    });
  }

  public loginWithAuthorizationCode(authorizationCode: string) {
    return this.yamcsService.yamcsClient.fetchAccessTokenWithAuthorizationCode(authorizationCode).then(loginInfo => {
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
    return this.yamcsService.yamcsClient.fetchAccessTokenWithRefreshToken(refreshToken).then(loginInfo => {
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

    if (navigateToLoginPage) {
      if (this.logoutRedirectUrl) {
        window.location.href = this.logoutRedirectUrl;
      } else if (!this.configService.getConfig().disableLoginForm) {
        const redirectURI = this.buildOpenIDRedirectURI();
        window.location.href = this.buildRedirector({
          clientId: 'yamcs-web',
          authorizationEndpoint: `${location.protocol}//${location.host}${this.baseHref}auth/authorize`,
          scope: 'openid',
        }, redirectURI);
      }
    }
  }

  public buildOpenIDRedirectURI() {
    return `${location.protocol}//${location.host}${this.baseHref}cb`;
  }

  public buildServerSideOpenIDRedirectURI() {
    return `${location.protocol}//${location.host}${this.baseHref}oidc-browser-callback`;
  }

  private buildRedirector(openid: OpenIDConnectInfo, redirectURI: string) {
    let url = openid.authorizationEndpoint;
    url += `?client_id=${encodeURIComponent(openid.clientId)}`;
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
    };
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
