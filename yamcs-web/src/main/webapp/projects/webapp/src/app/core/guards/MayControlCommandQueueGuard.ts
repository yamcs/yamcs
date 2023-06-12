import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/AuthService';

export const mayControlCommandQueueGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(MayControlCommandQueueGuard).canActivate(route, state);
};

export const mayControlCommandQueueGuardChildFn: CanActivateChildFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(MayControlCommandQueueGuard).canActivateChild(route, state);
};

@Injectable({ providedIn: 'root' })
class MayControlCommandQueueGuard {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (this.authService.getUser()!.hasSystemPrivilege('ControlCommandQueue')) {
      return true;
    }

    this.router.navigate(['/403'], { queryParams: { page: state.url } });
    return false;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.canActivate(route, state);
  }
}
