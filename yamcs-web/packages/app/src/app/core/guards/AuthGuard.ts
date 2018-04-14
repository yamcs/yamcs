import { CanActivate, Router, ActivatedRouteSnapshot, RouterStateSnapshot, CanActivateChild } from '@angular/router';
import { Injectable } from '@angular/core';
import { AuthService } from '../services/AuthService';

@Injectable()
export class AuthGuard implements CanActivate, CanActivateChild {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    if (this.authService.isLoggedIn()) {
      return Promise.resolve(true);
    } else if (this.authService.isAccessTokenAvailable()) {
      // A valid cookie is still set from a previous visit. Use it
      // to get a fresh cookie while also logging in.
      return this.authService.refreshAccessToken().then(() => true);
    }

    this.router.navigate(['/login'], { queryParams: { next: state.url } });
    return Promise.resolve(false);
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }
}
