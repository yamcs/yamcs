import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from '@angular/router';
import { YamcsService } from '../services/YamcsService';

@Injectable()
export class ClearContextGuard implements CanActivate {

  constructor(private yamcsService: YamcsService) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    this.yamcsService.clearContext();
    return true;
  }
}
