import { Injectable, inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { YamcsService } from '../services/YamcsService';

export const clearContextGuardFn: CanActivateFn = () => inject(ClearContextGuard).canActivate();

@Injectable({ providedIn: 'root' })
class ClearContextGuard {

  constructor(private yamcsService: YamcsService) {
  }

  canActivate() {
    this.yamcsService.clearContext();
    return true;
  }
}
