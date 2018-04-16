import { Injectable, OnDestroy } from '@angular/core';
import { YamcsService } from './YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { AccessTokenResponse, UserInfo } from '@yamcs/client';

export interface Claims {
  iss: string;
  sub: string;
  iat: number;
  exp: number;
}

// How often to check if the access token is close to expiring.
// Could also use setTimeout instead of setInterval i guess.
const TOKEN_CHECK_INTERVAL = 10000;

@Injectable()
export class AuthService implements OnDestroy {

  public authRequired$ = new BehaviorSubject<boolean | null>(null);
  public userInfo$ = new BehaviorSubject<UserInfo | null>(null);

  // Indicates when the access_token is due to expire
  // This is only set after fetching a fresh access token from the server.
  // It is not set if a cookie is available from a previous visit, because
  // this is a bit fragile to retrieve.
  private tokenExpiration: Date;

  private tokenRefresher: number;

  constructor(private yamcsService: YamcsService) {

    // Simplistic token refresher that replaces an aged
    // access token with a newer one. As long as the browser
    // application remains open, the 'session' will not expire.
    this.tokenRefresher = window.setInterval(() => {
      const accessToken = this.getCookie('access_token');
      if (accessToken && this.tokenExpiration) {
        if (this.tokenExpiration.getTime() - (3 * TOKEN_CHECK_INTERVAL) < new Date().getTime()) {
          console.log('Refreshing access token');
          this.refreshAccessToken();
        }
      }
    }, TOKEN_CHECK_INTERVAL);

    yamcsService.yamcsClient.getAuthInfo().then(authInfo => {
      this.authRequired$.next(authInfo.requireAuthentication);
    });
  }

  isAccessTokenAvailable() {
    return this.getCookie('access_token') !== null;
  }

  isLoggedIn() {
    return this.userInfo$.value !== null && this.isAccessTokenAvailable();
  }

  getUserInfo() {
    return this.userInfo$.value;
  }

  refreshAccessToken() {
    const accessToken = this.getCookie('access_token');
    if (accessToken) {
      this.yamcsService.yamcsClient.accessToken = accessToken;
      return this.yamcsService.yamcsClient.refreshAccessToken().then(loginInfo => {
        this.updateCookie(loginInfo);
        this.userInfo$.next(loginInfo.user);
      });
    } else {
      throw new Error('Cannot refresh access token without an existing token');
    }
  }

  login(username: string, password: string) {
    // Store in cookie so that the token survives browser refreshes
    // and so it is added to the header of a websocket request.
    return this.yamcsService.yamcsClient.login(username, password).then(loginInfo => {
      this.updateCookie(loginInfo);
      this.userInfo$.next(loginInfo.user);
      return this.extractClaims(loginInfo.access_token);
    });
  }

  private updateCookie(tokenResponse: AccessTokenResponse) {
    const expireMillis = tokenResponse.expires_in * 1000;
    const cookieExpiration = new Date();
    cookieExpiration.setTime(cookieExpiration.getTime() + expireMillis);
    let cookie = `access_token=${encodeURIComponent(tokenResponse.access_token)}`;
    cookie += `; expires=${cookieExpiration.toUTCString()}`;
    cookie += '; path=/';
    document.cookie = cookie;
    this.tokenExpiration = cookieExpiration;
  }

  logout() {
    document.cookie = 'access_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    this.yamcsService.yamcsClient.accessToken = undefined;
    this.userInfo$.next(null);
  }

  hasSystemPrivilege(privilege: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.systemPrivileges) {
      for (const expression of userInfo.systemPrivileges) {
        if (privilege.match(expression)) {
          return true;
        }
      }
    }
    return false;
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
    return null;
  }

  ngOnDestroy() {
    clearInterval(this.tokenRefresher);
  }
}
