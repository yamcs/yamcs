import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, CanActivateChild, Router } from '@angular/router';
import { Injectable } from '@angular/core';
import { AuthService } from '../services/AuthService';

@Injectable()
export class MayReadEventsGuard implements CanActivate, CanActivateChild {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const userInfo = this.authService.getUserInfo();
    if (userInfo) {
      const systemPrivileges = userInfo.systemPrivileges || [];
      for (const expression of systemPrivileges) {
        if ('MayReadEvents'.match(expression)) {
          return true;
        }
      }
    }

    this.router.navigate(['/403'], { queryParams: { page: state.url } });
    return false;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.canActivate(route, state);
  }
}
