import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide a label to a toolbar without causing a circular dependency
 */
export const APP_ADMIN_TOOLBAR = new InjectionToken<any>('APP_ADMIN_TOOLBAR');

/** Flag labels for use with the portal directive */
@Directive({
  selector: '[app-admin-toolbar-label]',
})
export class AppAdminToolbarLabel extends CdkPortal {
  _closestToolbar = inject(APP_ADMIN_TOOLBAR, { optional: true });
}
