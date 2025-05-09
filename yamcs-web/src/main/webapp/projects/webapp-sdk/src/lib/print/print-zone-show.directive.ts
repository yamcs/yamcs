import { Directive } from '@angular/core';

/**
 * Hides the host element, except when used in PrintZone
 */
@Directive({
  selector: '[yaPrintZoneShow]',
  host: {
    class: 'ya-print-zone-show',
    '[style.display]': '"none"',
  },
})
export class YaPrintZoneShow {}
