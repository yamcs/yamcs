import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { utils } from '@yamcs/webapp-sdk';
import { AuthService } from '../services/AuthService';

export const openIDCallbackGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  return inject(OpenIDCallbackGuard).canActivate(route);
};

@Injectable({ providedIn: 'root' })
class OpenIDCallbackGuard {

  constructor(private authService: AuthService, private router: Router) {
  }

  async canActivate(route: ActivatedRouteSnapshot) {
    const oidcState = route.queryParamMap.get('state');
    const oidcCode = route.queryParamMap.get('code');
    // Note: this callback usually gives us a "session_state" as well. We're ignoring
    // that as long as we don't have a need for it.

    if (!oidcCode) {
      console.error('Unexpected callback. Could not find query param: "code"');
      return false;
    }

    // Exchange our upstream code for a Yamcs-level access token.
    await this.authService.loginWithAuthorizationCode(oidcCode);

    // At this point, everything worked. Yamcs cookies are in place. And we
    // can navigate the user to the original attempted URL.
    if (oidcState) {
      this.router.navigateByUrl(utils.fromBase64URL(oidcState));
    } else {
      this.router.navigateByUrl('/');
    }

    return false;
  }
}
