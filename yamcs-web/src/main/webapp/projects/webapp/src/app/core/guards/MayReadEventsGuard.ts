import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/AuthService';

export const mayReadEventsGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(MayReadEventsGuard).canActivate(route, state);
};

export const mayReadEventsGuardChildFn: CanActivateChildFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(MayReadEventsGuard).canActivateChild(route, state);
};

@Injectable({ providedIn: 'root' })
class MayReadEventsGuard {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (this.authService.getUser()!.hasSystemPrivilege('ReadEvents')) {
      return true;
    }

    this.router.navigate(['/403'], { queryParams: { page: state.url } });
    return false;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.canActivate(route, state);
  }
}
