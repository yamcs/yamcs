import { Directive } from '@angular/core';

/**
 * Shows the host element, except when used in PrintZone
 */
@Directive({
  selector: '[yaPrintZoneHide]',
  host: {
    class: 'ya-print-zone-hide',
  },
})
export class YaPrintZoneHide {}
