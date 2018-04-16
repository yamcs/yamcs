import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { YamcsService } from '../services/YamcsService';

@Injectable()
export class InstanceExistsGuard implements CanActivate {

  constructor(private yamcsService: YamcsService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    const instanceId = route.queryParams['instance'];

    return new Promise<boolean>((resolve, reject) => {
      this.yamcsService.yamcsClient.getInstance(instanceId).then(instance => {
        this.yamcsService.selectInstance(instance)
          .then(() => resolve(true))
          .catch(() => resolve(false));
      }).catch(err => {
        this.router.navigate(['/404'], {
          queryParams: {
            page: state.url,
          },
          // Would prefer the attempted URL stays in the browser address bar
          // but unfortunately below property does not work. Follow this issue:
          // https://github.com/angular/angular/issues/17004
          //
          // skipLocationChange: true
        });
        resolve(false);
      });
    });
  }
}
