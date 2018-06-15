import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/AuthService';

@Injectable()
export class MayReadTablesGuard implements CanActivate, CanActivateChild {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const userInfo = this.authService.getUserInfo();
    if (userInfo) {
      if (userInfo.superuser) {
        return true;
      }
      const systemPrivileges = userInfo.systemPrivileges || [];
      for (const expression of systemPrivileges) {
        if ('MayReadTables'.match(expression)) {
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
