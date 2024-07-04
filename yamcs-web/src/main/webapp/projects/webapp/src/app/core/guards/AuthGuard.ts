import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthInfo, ConfigService, OpenIDConnectInfo, YamcsService, utils } from '@yamcs/webapp-sdk';
import { AuthService } from '../services/AuthService';

export const authGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(AuthGuard).canActivate(route, state);
};

export const authGuardChildFn: CanActivateChildFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(AuthGuard).canActivateChild(route, state);
};

@Injectable({ providedIn: 'root' })
class AuthGuard {

  private authInfo: AuthInfo;

  constructor(
    @Inject(APP_BASE_HREF) private baseHref: string,
    private authService: AuthService,
    private yamcs: YamcsService,
    private router: Router,
    private configService: ConfigService,
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
        this.router.navigate(['/down'], { skipLocationChange: true });
        return false;
      } else {
        if (this.configService.getConfig().logoutRedirectUrl) {
          this.authService.logout(true /* redirect to external login */);
        } else {
          this.authService.logout(false);
        }
        if (this.authInfo.openid) {
          const redirectURI = this.authService.buildServerSideOpenIDRedirectURI();
          window.location.href = this.buildRedirector(this.authInfo.openid, redirectURI, state.url);
        } else if (!this.configService.getConfig().disableLoginForm) {
          const redirectURI = this.authService.buildOpenIDRedirectURI();
          window.location.href = this.buildRedirector({
            clientId: 'yamcs-web',
            authorizationEndpoint: `${location.protocol}//${location.host}${this.baseHref}auth/authorize`,
            scope: 'openid',
          }, redirectURI, state.url);
        }
        return false;
      }
    }
  }

  async canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }

  private buildRedirector(openid: OpenIDConnectInfo, redirectURI: string, next: string) {
    // The current client-side URL gets passed as state
    // When the whole OIDC setup is done, we will get it back on the /oidc-browser-callback route
    // together with a code that we can exchange for a valid Yamcs access token.
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
}
