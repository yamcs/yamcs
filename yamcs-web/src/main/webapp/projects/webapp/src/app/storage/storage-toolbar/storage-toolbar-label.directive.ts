import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide a label to a toolbar without causing a circular dependency
 */
export const APP_STORAGE_TOOLBAR = new InjectionToken<any>(
  'APP_STORAGE_TOOLBAR',
);

/** Flag labels for use with the portal directive */
@Directive({
  selector: '[app-storage-toolbar-label]',
})
export class AppStorageToolbarLabel extends CdkPortal {
  _closestToolbar = inject(APP_STORAGE_TOOLBAR, { optional: true });
}
