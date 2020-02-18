import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router, RouterStateSnapshot } from '@angular/router';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';

@Injectable({
  providedIn: 'root',
})
export class ClearancesEnabledGuard implements CanActivate, CanActivateChild {

  private config: WebsiteConfig;

  constructor(configService: ConfigService, private router: Router) {
    this.config = configService.getConfig();
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.config.commandClearances;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.canActivate(route, state);
  }
}
