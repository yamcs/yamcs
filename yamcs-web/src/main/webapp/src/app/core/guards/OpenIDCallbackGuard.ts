import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import * as utils from '../../shared/utils';
import { AuthService } from '../services/AuthService';

@Injectable()
export class OpenIDCallbackGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    const oidcState = route.queryParamMap.get('state');
    const oidcCode = route.queryParamMap.get('code');
    // Note: this callback usually gives us a "session_state" as well. We're ignoring
    // that as long as we don't have a need for it.

    if (!oidcState || !oidcCode) {
      console.error('Unexpected callback. Could not find query params: "state" and "code"');
      return false;
    }

    // Generate custom encoded data for interpretation by Yamcs when exchanging
    // the upstream code for an upstream access token (the browser does not need to
    // know about upstream tokens).
    const thirdPartyData = utils.generateUnsignedJWT({
      code: oidcCode,
      redirect_uri: this.authService.buildOpenIDRedirectURI(),
    });

    // Exchange our upstream code for a Yamcs-level access token.
    await this.authService.loginWithAuthorizationCode(`oidc ${thirdPartyData}`);

    // At this point, everything worked. Yamcs cookies are in place. And we
    // can navigate the user to the original attempted URL.
    this.router.navigateByUrl(utils.fromBase64URL(oidcState));

    return false;
  }
}
