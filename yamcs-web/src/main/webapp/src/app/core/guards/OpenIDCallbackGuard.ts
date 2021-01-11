import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import * as utils from '../../shared/utils';
import { AuthService } from '../services/AuthService';

@Injectable()
export class OpenIDCallbackGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {
  }

  async canActivate(route: ActivatedRouteSnapshot) {
    const oidcState = route.queryParamMap.get('state');
    const oidcCode = route.queryParamMap.get('code');
    // Note: this callback usually gives us a "session_state" as well. We're ignoring
    // that as long as we don't have a need for it.

    if (!oidcState || !oidcCode) {
      console.error('Unexpected callback. Could not find query params: "state" and "code"');
      return false;
    }

    // Exchange our upstream code for a Yamcs-level access token.
    await this.authService.loginWithAuthorizationCode(oidcCode);

    // At this point, everything worked. Yamcs cookies are in place. And we
    // can navigate the user to the original attempted URL.
    this.router.navigateByUrl(utils.fromBase64URL(oidcState));

    return false;
  }
}
