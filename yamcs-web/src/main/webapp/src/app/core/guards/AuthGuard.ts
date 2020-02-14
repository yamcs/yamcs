import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/AuthService';

@Injectable()
export class AuthGuard implements CanActivate, CanActivateChild {

  constructor(private authService: AuthService, private router: Router) {
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    try {
      await this.authService.loginAutomatically();
      return true;
    } catch (err) {
      if (err.name === 'NetworkError' || err.name === 'TypeError') { // TypeError is how Fetch API reports network or CORS failure
        this.router.navigate(['/down'], { queryParams: { next: state.url } });
        return false;
      } else {
        this.authService.logout(false);
        this.router.navigate(['/login'], { queryParams: { next: state.url } });
        return false;
      }
    }
  }

  async canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }
}
