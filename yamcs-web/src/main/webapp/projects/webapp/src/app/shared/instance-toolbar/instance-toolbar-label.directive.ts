import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide a label to a toolbar without causing a circular dependency
 */
export const APP_INSTANCE_TOOLBAR = new InjectionToken<any>(
  'APP_INSTANCE_TOOLBAR',
);

/** Flag labels for use with the portal directive */
@Directive({
  selector: '[app-instance-toolbar-label]',
})
export class AppInstanceToolbarLabel extends CdkPortal {
  _closestToolbar = inject(APP_INSTANCE_TOOLBAR, { optional: true });
}
