import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router, RouterStateSnapshot } from '@angular/router';
import { AuthInfo } from '@yamcs/client';
import { filter, take } from 'rxjs/operators';
import { AuthService } from '../services/AuthService';

@Injectable()
export class AuthGuard implements CanActivate, CanActivateChild {

  constructor(private authService: AuthService, private router: Router) {
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    if (this.authService.isLoggedIn()) {
      return Promise.resolve(true);
    }

    if (this.authService.isAccessTokenAvailable()) {
      // A valid cookie is still set from a previous visit. Use it
      // to get a fresh cookie while also logging in.
      return this.authService.refreshAccessToken().then(() => true);
    }

    // Check if authentication is maybe disabled for this server
    // This waits until the /auth http request triggered in AuthService
    // is back. This should only happen once at app init.
    let authInfo = this.authService.authInfo$.value;
    if (authInfo !== null) {
      return this.handleAuthInfo(authInfo, state);
    }

    // AuthService must still be initialising. Await the outcome
    authInfo = await this.authService.authInfo$.pipe(
      filter(x => x !== null),
      take(1),
    ).toPromise();

    return this.handleAuthInfo(authInfo!, state);
  }

  async canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return this.canActivate(route, state);
  }

  private async handleAuthInfo(authInfo: AuthInfo, state: RouterStateSnapshot): Promise<boolean> {
    if (!authInfo.requireAuthentication) {
      return true;
    }

    for (const flow of authInfo.flow) {
      console.log('try flow ', flow.type);
      console.log('would go to ', state.url);
      if (flow.type === 'PASSWORD') {
        this.router.navigate(['/login'], { queryParams: { next: state.url } });
        return false;
      } else if (flow.type === 'SPNEGO') {
        try {
          await this.attemptSpnego();
          return true;
        } catch {
          continue;
        }
      } else {
        console.warn('Unrecognized flow type ' + flow.type);
      }
    }
    return false;
  }

  private async attemptSpnego() {
    const response = await fetch('/auth/spnego', {
      credentials: 'include',
    });

    if (response.status === 200) {
      const authorizationCode = (await response.text()).trim();
      await this.authService.loginWithAuthorizationCode(authorizationCode);
    }
  }
}
