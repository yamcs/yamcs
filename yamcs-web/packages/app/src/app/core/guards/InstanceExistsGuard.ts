import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot } from '@angular/router';
import { YamcsService } from '../services/YamcsService';

@Injectable()
export class InstanceExistsGuard implements CanActivate {

  constructor(private yamcsService: YamcsService) {
  }

  canActivate(route: ActivatedRouteSnapshot) {
    const instanceId = route.queryParams['instance'];

    return new Promise<boolean>((resolve, reject) => {
      this.yamcsService.yamcsClient.getInstance(instanceId).then(instance => {
        // TODO handle 404
        console.log('found instance ', instance);
        this.yamcsService.switchInstance(instance);
        resolve(true);
      });
    });

    /*
    this.router.navigate(['/404'], {
      // Would prefer the attempted URL stays in the browser address bar
      // but unfortunately below property does not work. Follow this issue:
      // https://github.com/angular/angular/issues/17004
      //
      // skipLocationChange: true
    });
    */
  }
}
