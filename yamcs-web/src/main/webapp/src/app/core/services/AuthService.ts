import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthInfo, HttpHandler, TokenResponse, UserInfo } from '../../client';
import { User } from '../../shared/User';
import { ConfigService } from './ConfigService';
import { YamcsService } from './YamcsService';

export interface Claims {
  iss: string;
  sub: string;
  iat: number;
  exp: number;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {

  private authInfo: AuthInfo;
  public user$ = new BehaviorSubject<User | null>(null);

  constructor(
    private yamcsService: YamcsService,
    configService: ConfigService,
    private router: Router,
    @Inject(APP_BASE_HREF) private baseHref: string,
  ) {
    this.authInfo = configService.getAuthInfo();

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
      } catch (err) {
        if (err.name === 'TypeError') { // TypeError is how Fetch API reports network or CORS failure
          this.router.navigate(['/down'], { queryParams: { next: '/' } });
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
  public async loginAutomatically(): Promise<any> {
    if (!this.authInfo.requireAuthentication) {
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
    if (accessToken) {
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
    // This is done before any other flows, because it does not
    // require user intervention when successful.
    let spnego = false;
    for (const flow of this.authInfo.flow) {
      if (flow.type === 'SPNEGO') {
        spnego = true;
      }
    }
    if (spnego) {
      return await this.loginWithSpnego();
    }

    this.logout(false);
    throw new Error('Could not login automatically');
  }

  /**
   * Logs in via user-provided username/password credentials. This would have
   * to come from our login page.
   */
  public login(username: string, password: string) {
    return this.yamcsService.yamcsClient.fetchAccessTokenWithPassword(username, password).then(loginInfo => {
      this.updateLoginCookies(loginInfo);
      this.user$.next(new User(loginInfo.user));
      return this.extractClaims(loginInfo.access_token);
    });
  }

  private loginWithAuthorizationCode(authorizationCode: string) {
    return this.yamcsService.yamcsClient.fetchAccessTokenWithAuthorizationCode(authorizationCode).then(loginInfo => {
      this.updateLoginCookies(loginInfo);

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
      this.updateLoginCookies(loginInfo);
      this.user$.next(new User(loginInfo.user));
      return this.extractClaims(loginInfo.access_token);
    });
  }

  /*
   * Store in cookie so that the token survives browser refreshes and so it
   * is added to the header of a websocket request.
   */
  private updateLoginCookies(tokenResponse: TokenResponse) {
    const expireMillis = tokenResponse.expires_in * 1000;
    const cookieExpiration = new Date();
    cookieExpiration.setTime(cookieExpiration.getTime() + expireMillis);
    let cookie = `access_token=${encodeURIComponent(tokenResponse.access_token)}`;
    cookie += `; expires=${cookieExpiration.toUTCString()}`;
    cookie += '; path=/';
    document.cookie = cookie;

    // Store refresh token in a Session Cookie (bound to browser, not tab)
    cookie = `refresh_token=${encodeURIComponent(tokenResponse.refresh_token)}`;
    cookie += '; path=/';
    document.cookie = cookie;
  }

  /**
   * Clear all data from a previous login session
   */
  logout(navigateToLoginPage: boolean) {
    this.clearCookie('access_token');
    this.clearCookie('refresh_token');

    this.yamcsService.clearContext(); // TODO needed here?
    this.user$.next(null);

    if (navigateToLoginPage) {
      this.router.navigate(['/login'], { queryParams: { next: '/' } });
    }
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
    document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  }
}
