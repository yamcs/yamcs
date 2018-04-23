import { CanActivate, Router, ActivatedRouteSnapshot, RouterStateSnapshot, CanActivateChild } from '@angular/router';
import { Injectable } from '@angular/core';
import { AuthService } from '../services/AuthService';
import { filter, take } from 'rxjs/operators';

@Injectable()
export class AuthGuard implements CanActivate, CanActivateChild {

  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    if (this.authService.isLoggedIn()) {
      return Promise.resolve(true);
    }

    if (this.authService.isAccessTokenAvailable()) {
      // A valid cookie is still set from a previous visit. Use it
      // to get a fresh cookie while also logging in.
      return this.authService.refreshAccessToken().then(() => true);
    }

    // Check if authentication is maybe disabled for this server
    // This wait until the /auth http request triggered in AuthService
    // is back. This should only happen once at app init.
    const authRequired = this.authService.authRequired$.value;
    if (authRequired !== null) {
      if (authRequired) {
        this.router.navigate(['/login'], { queryParams: { next: state.url } });
      }
      return Promise.resolve(!authRequired);
    }

    // AuthService must still be initialising. Await the outcome
    return new Promise<boolean>((resolve, reject) => {
      this.authService.authRequired$.pipe(
        filter(isRequired => isRequired !== null),
        take(1),
      ).subscribe(isRequired => {
        if (isRequired) {
          this.router.navigate(['/login'], { queryParams: { next: state.url } });
        }
        resolve(!isRequired);
      });
    });
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }
}
