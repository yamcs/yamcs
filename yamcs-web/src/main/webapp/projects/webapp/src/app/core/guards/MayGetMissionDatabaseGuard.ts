import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/AuthService';

export const mayGetMissionDatabaseGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(MayGetMissionDatabaseGuard).canActivate(route, state);
};

export const mayGetMissionDatabaseGuardChildFn: CanActivateChildFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(MayGetMissionDatabaseGuard).canActivateChild(route, state);
};

@Injectable({ providedIn: 'root' })
class MayGetMissionDatabaseGuard {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (this.authService.getUser()!.hasSystemPrivilege('GetMissionDatabase')) {
      return true;
    }

    this.router.navigate(['/403'], { queryParams: { page: state.url } });
    return false;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.canActivate(route, state);
  }
}
