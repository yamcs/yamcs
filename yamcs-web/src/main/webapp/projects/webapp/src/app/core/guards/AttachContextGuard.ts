import { Location } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { YamcsService } from '@yamcs/webapp-sdk';

export const attachContextGuardFn: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  return inject(AttachContextGuard).canActivate(route, state);
};

@Injectable({ providedIn: 'root' })
class AttachContextGuard {

  constructor(
    private yamcsService: YamcsService,
    private router: Router,
    private location: Location,
  ) { }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    let instanceId: string = route.queryParams['c'];
    let processorId: string | undefined;
    if (instanceId.indexOf('__') !== -1) {
      const parts = instanceId.split('__');
      instanceId = parts[0];
      processorId = parts[1];
    }

    return new Promise<boolean>((resolve, reject) => {
      this.yamcsService.setContext(instanceId, processorId)
        .then(() => resolve(true))
        .catch(err => {
          if (err.statusCode === 404) {
            this.router.navigate(['/404'], {
              queryParams: { page: state.url },
            }).then(() => {
              // Keep the attempted URL in the address bar.
              this.location.replaceState(state.url);
            });
          } else {
            this.router.navigate(['/down']).then(() => {
              // Keep the attempted URL in the address bar.
              this.location.replaceState(state.url);
            });
          }
          resolve(false);
        });
    });
  }
}
