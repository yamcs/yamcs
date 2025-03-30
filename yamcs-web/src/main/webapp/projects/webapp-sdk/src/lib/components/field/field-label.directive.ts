import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide a field label to a field without causing a circular dependency
 */
export const YA_FIELD = new InjectionToken<any>('YA_FIELD');

/** Flag field labels for use with the portal directive */
@Directive({
  selector: '[ya-field-label]',
})
export class YaFieldLabel extends CdkPortal {
  _closestField = inject(YA_FIELD, { optional: true });
}
