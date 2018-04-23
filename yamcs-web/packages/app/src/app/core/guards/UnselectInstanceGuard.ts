import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { YamcsService } from '../services/YamcsService';

@Injectable()
export class UnselectInstanceGuard implements CanActivate {

  constructor(private yamcsService: YamcsService) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    this.yamcsService.unselectInstance();
    return true;
  }
}
