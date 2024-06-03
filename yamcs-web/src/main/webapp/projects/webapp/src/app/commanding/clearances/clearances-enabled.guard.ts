import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { ConfigService, WebsiteConfig } from '@yamcs/webapp-sdk';

export const clearancesEnabledGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(ClearancesEnabledGuard).canActivate(route, state);
};

export const clearancesEnabledGuardChildFn: CanActivateChildFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(ClearancesEnabledGuard).canActivateChild(route, state);
};

@Injectable({
  providedIn: 'root',
})
class ClearancesEnabledGuard {

  private config: WebsiteConfig;

  constructor(configService: ConfigService, private router: Router) {
    this.config = configService.getConfig();
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.config.commandClearanceEnabled;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.canActivate(route, state);
  }
}
