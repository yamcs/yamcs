import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AuthInfo, HttpHandler, TokenResponse, UserInfo } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { User } from '../../shared/User';
import { YamcsService } from './YamcsService';

export interface Claims {
  iss: string;
  sub: string;
  iat: number;
  exp: number;
}

// TODO enforce only one access token request is done at a time
// because otherwise our refresh token may not update correctly
// (server enforces single use).
@Injectable({
  providedIn: 'root',
})
export class AuthService {

  public authInfo$ = new BehaviorSubject<AuthInfo | null>(null);
  public user$ = new BehaviorSubject<User | null>(null);

  private accessToken?: string;
  private refreshToken?: string;

  constructor(private yamcsService: YamcsService, private router: Router) {
    yamcsService.yamcsClient.getAuthInfo().then(authInfo => {
      this.authInfo$.next(authInfo);
    });

    // Restore refresh token from session cookie on app bootstrap
    // Should maybe refactor this stuff in the yamcsclient.
    this.refreshToken = this.getCookie('refresh_token');

    /*
     * Attempts to prevent 401 exceptions by checking if locally available
     * tokens should (still) work. If not, then the user is navigated
     * to the login page.
     */
    yamcsService.yamcsClient.setHttpInterceptor(async (next: HttpHandler, url: string, init?: RequestInit) => {
      try {
        // Verify or fetch a token when necessary
        await this.loginAutomatically();

        let response = await next.handle(url, init);
        if (response.status === 401) {

          // Server must have refused our access token. Attempt to refresh.
          this.accessToken = undefined;
          this.yamcsService.yamcsClient.clearAccessToken();
          await this.loginAutomatically();
          if (init) {
            (init.headers as Headers).set('Authorization', `Bearer ${this.accessToken}`);
          }
          response = await next.handle(url, init);
        }

        return response;
      } catch (err) {
        this.logout(true);
        throw err;
      }
    });
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
    const authInfo = await this.authInfo$.pipe(
      filter(x => x !== null),
      take(1),
    ).toPromise();

    if (!authInfo!.requireAuthentication) {
      if (!this.user$.value) {
        // Written such that it bypasses our interceptor
        const response = await fetch('/api/user');
        this.user$.next(new User(await response.json() as UserInfo));
      }
      return;
    }

    // Use already available tokens when we can
    if (this.accessToken) {
      if (!this.user$.value) {
        // Written such that it bypasses our interceptor
        const headers = new Headers();
        headers.append('Authorization', `Bearer ${this.accessToken}`);
        const response = await fetch('/api/user', { headers });
        if (response.status === 200) {
          const user = new User(await response.json() as UserInfo);
          this.user$.next(user);
        } else if (response.status === 401) {
          if (this.refreshToken) {
            this.accessToken = undefined;
            this.yamcsService.yamcsClient.clearAccessToken();
            try {
              return await this.loginWithRefreshToken(this.refreshToken);
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
      return this.extractClaims(this.accessToken);
    }
    if (this.refreshToken) {
      try {
        return await this.loginWithRefreshToken(this.refreshToken);
      } catch {
        console.log('Server refused our refresh token');
        this.logout(false);
      }
    }
    // If server supports spnego, attempt browser negotiation.
    // This is done before any other flows, because it does not
    // require user intervention when successful.
    let spnego = false;
    for (const flow of authInfo!.flow) {
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
      this.accessToken = loginInfo.access_token;
      this.refreshToken = loginInfo.refresh_token;
      this.yamcsService.yamcsClient.setAccessToken(loginInfo.access_token);
      this.user$.next(new User(loginInfo.user));
      return this.extractClaims(loginInfo.access_token);
    });
  }

  private loginWithAuthorizationCode(authorizationCode: string) {
    return this.yamcsService.yamcsClient.fetchAccessTokenWithAuthorizationCode(authorizationCode).then(loginInfo => {
      this.updateLoginCookies(loginInfo);
      this.accessToken = loginInfo.access_token;
      this.refreshToken = loginInfo.refresh_token;
      this.yamcsService.yamcsClient.setAccessToken(loginInfo.access_token);
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
      this.accessToken = loginInfo.access_token;
      this.refreshToken = loginInfo.refresh_token;
      this.yamcsService.yamcsClient.setAccessToken(loginInfo.access_token);
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
    this.accessToken = undefined;
    this.refreshToken = undefined;

    this.yamcsService.unselectInstance(); // TODO needed here?
    this.yamcsService.yamcsClient.clearAccessToken();
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
      return parts.pop()!.split(';').shift();
    }
    return undefined;
  }
}
