import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AuthInfo, TokenResponse, UserInfo } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { YamcsService } from './YamcsService';

export interface Claims {
  iss: string;
  sub: string;
  iat: number;
  exp: number;
}

// TODO enforce only one access token request is done at a time.
// Because otherwise it may be that our refresh token do not updated
// correctly (server enforces single use).
@Injectable({
  providedIn: 'root',
})
export class AuthService {

  public authInfo$ = new BehaviorSubject<AuthInfo | null>(null);
  public userInfo$ = new BehaviorSubject<UserInfo | null>(null);

  constructor(private yamcsService: YamcsService, private router: Router) {
    yamcsService.yamcsClient.getAuthInfo().then(authInfo => {
      this.authInfo$.next(authInfo);
    });

    // Restore access token from cookie on app bootstrap
    // We don't actually need the access token to be in a cookie in the app.
    // The refresh token gives us enough.
    // It's only really useful to pass authentication info on the websocket
    // client. So maybe we should refactor this stuff in the yamcsclient.
    const accessToken = this.getCookie('access_token');
    if (accessToken) {
      yamcsService.yamcsClient.setAccessToken(accessToken);
    }

    /*
     * Attempts to prevent 401 exceptions by checking if locally available
     * tokens should (still) work. If not, then the user is navigated
     * to the login page.
     */
    yamcsService.yamcsClient.addHttpInInterceptor(async (url: string) => {
      if (url === '/api/user') { // Bypass to prevent loginAutomatically loop
        return Promise.resolve();
      }
      try {
        await this.loginAutomatically();
      } catch (err) {
        this.router.navigate(['/login'], { queryParams: { next: '/' } });
        throw err;
      }
    });

    /*
     * Responds to 401 issues that may still occur despite our good intentions.
     */
    yamcsService.yamcsClient.addHttpOutInterceptor(async (url: string, response: Response) => {
      if (response.status === 401) {
        this.logout();
        try {
          await this.loginAutomatically();
        } catch (err) {
          this.router.navigate(['/login'], { queryParams: { next: '/' } });
          throw err;
        }
      }
    });
  }

  getUserInfo() {
    return this.userInfo$.value;
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
      if (!this.userInfo$.value) {
        const userInfo = await this.yamcsService.yamcsClient.getUserInfo();
        this.userInfo$.next(userInfo);
      }
      return; // TODO get user details
    }

    // Use already available tokens when we can
    const accessToken = this.getCookie('access_token');
    if (accessToken) {
      if (!this.userInfo$.value) {
        const userInfo = await this.yamcsService.yamcsClient.getUserInfo();
        this.userInfo$.next(userInfo);
      }
      return this.extractClaims(accessToken);
    }
    const refreshToken = this.getCookie('refresh_token');
    if (refreshToken) {
      try {
        return await this.loginWithRefreshToken(refreshToken);
      } catch {
        console.log('Server refused our refresh token');
        // Ignore
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

    this.logout();
    throw new Error('Could not login automatically');
  }

  /**
   * Logs in via user-provided username/password credentials. This would have
   * to come from our login page.
   */
  public login(username: string, password: string) {
    // Store in cookie so that the token survives browser refreshes
    // and so it is added to the header of a websocket request.
    return this.yamcsService.yamcsClient.fetchAccessTokenWithPassword(username, password).then(loginInfo => {
      this.updateLoginCookies(loginInfo);
      this.yamcsService.yamcsClient.setAccessToken(loginInfo.access_token);
      this.userInfo$.next(loginInfo.user);
      return this.extractClaims(loginInfo.access_token);
    });
  }

  private loginWithAuthorizationCode(authorizationCode: string) {
    // Store in cookie so that the token survives browser refreshes
    // and so it is added to the header of a websocket request.
    return this.yamcsService.yamcsClient.fetchAccessTokenWithAuthorizationCode(authorizationCode).then(loginInfo => {
      this.updateLoginCookies(loginInfo);
      this.yamcsService.yamcsClient.setAccessToken(loginInfo.access_token);
      this.userInfo$.next(loginInfo.user);
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
      this.yamcsService.yamcsClient.setAccessToken(loginInfo.access_token);
      this.userInfo$.next(loginInfo.user);
      return this.extractClaims(loginInfo.access_token);
    });
  }

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
  logout() {
    document.cookie = 'access_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    document.cookie = 'refresh_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    this.yamcsService.unselectInstance(); // TODO needed here?
    this.yamcsService.yamcsClient.clearAccessToken();
    this.userInfo$.next(null);
  }

  hasSystemPrivilege(privilege: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.systemPrivileges) {
      for (const expression of userInfo.systemPrivileges) {
        if (privilege.match(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  hasParameterPrivilege(parameter: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.tmParaPrivileges) {
      for (const expression of userInfo.tmParaPrivileges) {
        if (parameter.match(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  hasSetParameterPrivilege(parameter: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.tmParaSetPrivileges) {
      for (const expression of userInfo.tmParaSetPrivileges) {
        if (parameter.match(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  hasPacketPrivilege(packet: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.tmPacketPrivileges) {
      for (const expression of userInfo.tmPacketPrivileges) {
        if (packet.match(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  hasCommandPrivilege(command: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.tcPrivileges) {
      for (const expression of userInfo.tcPrivileges) {
        if (command.match(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  hasStreamPrivilege(stream: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.streamPrivileges) {
      for (const expression of userInfo.streamPrivileges) {
        if (stream.match(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  hasCommandHistoryPrivilege(command: string) {
    const userInfo = this.userInfo$.value;
    if (userInfo && userInfo.superuser) {
      return true;
    }
    if (userInfo && userInfo.cmdHistoryPrivileges) {
      for (const expression of userInfo.cmdHistoryPrivileges) {
        if (command.match(expression)) {
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
}
