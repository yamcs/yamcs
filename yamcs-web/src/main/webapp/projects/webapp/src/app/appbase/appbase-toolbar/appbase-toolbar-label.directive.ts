import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide a label to a toolbar without causing a circular dependency
 */
export const APP_APPBASE_TOOLBAR = new InjectionToken<any>(
  'APP_APPBASE_TOOLBAR',
);

/** Flag labels for use with the portal directive */
@Directive({
  selector: '[app-appbase-toolbar-label]',
})
export class AppAppBaseToolbarLabel extends CdkPortal {
  _closestToolbar = inject(APP_APPBASE_TOOLBAR, { optional: true });
}
