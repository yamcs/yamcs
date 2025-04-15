import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide a label to a toolbar without causing a circular dependency
 */
export const YA_INSTANCE_TOOLBAR = new InjectionToken<any>(
  'YA_INSTANCE_TOOLBAR',
);

/** Flag labels for use with the portal directive */
@Directive({
  selector: '[ya-instance-toolbar-label]',
})
export class YaInstanceToolbarLabel extends CdkPortal {
  _closestToolbar = inject(YA_INSTANCE_TOOLBAR, { optional: true });
}
