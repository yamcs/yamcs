import { Service, inject } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateChildFn,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { AuthService, YamcsService } from '@yamcs/webapp-sdk';

export const authGuardFn: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  return inject(AuthGuard).canActivate(route, state);
};

export const authGuardChildFn: CanActivateChildFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  return inject(AuthGuard).canActivateChild(route, state);
};

@Service()
class AuthGuard {
  private authService = inject(AuthService);
  private yamcs = inject(YamcsService);
  private router = inject(Router);

  async canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): Promise<boolean> {
    try {
      await this.authService.loginAutomatically();
      this.yamcs.yamcsClient.prepareWebSocketClient();
      return true;
    } catch (err: any) {
      if (err.name === 'NetworkError' || err.name === 'TypeError') {
        // TypeError is how Fetch API reports network or CORS failure
        this.router.navigate(['/down'], { skipLocationChange: true });
        return false;
      } else {
        this.authService.logout(false);
        this.authService.redirectToLogin(state.url);
        return false;
      }
    }
  }

  async canActivateChild(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): Promise<boolean> {
    return this.canActivate(route, state);
  }
}
