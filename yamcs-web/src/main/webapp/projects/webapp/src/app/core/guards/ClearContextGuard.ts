import { Service, inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { YamcsService } from '@yamcs/webapp-sdk';

export const clearContextGuardFn: CanActivateFn = () =>
  inject(ClearContextGuard).canActivate();

@Service()
class ClearContextGuard {
  private yamcsService = inject(YamcsService);

  canActivate() {
    this.yamcsService.clearContext();
    return true;
  }
}
