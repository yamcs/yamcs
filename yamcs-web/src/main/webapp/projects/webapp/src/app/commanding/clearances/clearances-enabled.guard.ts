import { Service, inject } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateChildFn,
  CanActivateFn,
  RouterStateSnapshot,
} from '@angular/router';
import { ConfigService } from '@yamcs/webapp-sdk';

export const clearancesEnabledGuardFn: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  return inject(ClearancesEnabledGuard).canActivate(route, state);
};

export const clearancesEnabledGuardChildFn: CanActivateChildFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  return inject(ClearancesEnabledGuard).canActivateChild(route, state);
};

@Service()
class ClearancesEnabledGuard {
  private configService = inject(ConfigService);

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): boolean {
    return this.configService.getConfig().commandClearanceEnabled;
  }

  canActivateChild(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
  ): boolean {
    return this.canActivate(route, state);
  }
}
