import { CdkPortal } from '@angular/cdk/portal';
import { Directive, InjectionToken, inject } from '@angular/core';

/**
 * Provide an attr label to an attr without causing a circular dependency
 */
export const YA_ATTR = new InjectionToken<any>('YA_ATTR');

/** Flag field labels for use with the portal directive */
@Directive({
  selector: '[ya-attr-label]',
})
export class YaAttrLabel extends CdkPortal {
  _closestAttr = inject(YA_ATTR, { optional: true });
}
