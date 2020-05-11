import { Location } from '@angular/common';
import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { YamcsService } from '../services/YamcsService';

@Injectable()
export class AttachContextGuard implements CanActivate {

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
              queryParams: {
                page: state.url,
              },
            }).then(() => {
              // Keep the attempted URL in the address bar.
              // skipLocationChange would be better, but it does not work in guard...
              // https://github.com/angular/angular/issues/16981
              this.location.replaceState(state.url);
            });
          } else {
            this.router.navigate(['/down'], {
              queryParams: {
                page: state.url,
              },
            }).then(() => {
              // Keep the attempted URL in the address bar.
              // skipLocationChange would be better, but it does not work in guard...
              // https://github.com/angular/angular/issues/16981
              this.location.replaceState(state.url);
            });
          }
          resolve(false);
        });
    });
  }
}
