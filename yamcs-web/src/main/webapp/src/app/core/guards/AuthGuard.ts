import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router, RouterStateSnapshot } from '@angular/router';
import { AuthInfo, OpenIDConnectInfo } from '../../client';
import * as utils from '../../shared/utils';
import { AuthService } from '../services/AuthService';
import { ConfigService } from '../services/ConfigService';
import { YamcsService } from '../services/YamcsService';

@Injectable()
export class AuthGuard implements CanActivate, CanActivateChild {

  private authInfo: AuthInfo;

  constructor(
    @Inject(APP_BASE_HREF) private baseHref: string,
    private authService: AuthService,
    private yamcs: YamcsService,
    private router: Router,
    configService: ConfigService,
  ) {
    this.authInfo = configService.getAuthInfo();
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    try {
      await this.authService.loginAutomatically();
      this.yamcs.yamcsClient.prepareWebSocketClient();
      return true;
    } catch (err) {
      if (err.name === 'NetworkError' || err.name === 'TypeError') { // TypeError is how Fetch API reports network or CORS failure
        this.router.navigate(['/down'], { queryParams: { next: state.url } });
        return false;
      } else {
        if (this.authInfo.openid) {
          window.location.href = this.buildRedirector(this.authInfo.openid);
        } else {
          this.authService.logout(false);
          this.router.navigate(['/login'], { queryParams: { next: state.url } });
        }
        return false;
      }
    }
  }

  async canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }

  private buildRedirector(openid: OpenIDConnectInfo) {
    // The current client-side URL gets passed as state
    // When the whole OIDC setup is done, we will get it back on the /oidc-browser-callback route
    // together with a code that we can exchange for a valid Yamcs access token.
    const state = utils.toBase64URL(this.router.url);
    const redirectURI = this.authService.buildOpenIDRedirectURI();

    let url = openid.authorizationEndpoint;
    url += `?client_id=${encodeURIComponent(openid.clientId)}`;
    url += `&state=${state}`;
    url += '&response_mode=query';
    url += '&response_type=code';
    url += `&scope=${encodeURIComponent(openid.scope)}`;
    url += `&redirect_uri=${encodeURIComponent(redirectURI)}`;

    return url;
  }
}
