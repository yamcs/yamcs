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
    } catch (err: any) {
      if (err.name === 'NetworkError' || err.name === 'TypeError') { // TypeError is how Fetch API reports network or CORS failure
        this.router.navigate(['/down'], { queryParams: { next: state.url } });
        return false;
      } else {
        this.authService.logout(false);
        if (this.authInfo.openid) {
          const redirectURI = this.authService.buildServerSideOpenIDRedirectURI();
          window.location.href = this.buildRedirector(this.authInfo.openid, redirectURI);
        } else {
          const redirectURI = this.authService.buildOpenIDRedirectURI();
          window.location.href = this.buildRedirector({
            clientId: 'yamcs-web',
            authorizationEndpoint: `${location.protocol}//${location.host}${this.baseHref}auth/authorize`,
            scope: 'openid',
          }, redirectURI);
        }
        return false;
      }
    }
  }

  async canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }

  private buildRedirector(openid: OpenIDConnectInfo, redirectURI: string) {
    // The current client-side URL gets passed as state
    // When the whole OIDC setup is done, we will get it back on the /oidc-browser-callback route
    // together with a code that we can exchange for a valid Yamcs access token.
    const state = utils.toBase64URL(this.router.url);

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
