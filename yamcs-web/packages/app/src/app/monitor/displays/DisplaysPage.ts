import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Placeholder just to work around being able to capture file paths via
 * angular router '**' wildcard, rather than being forced to use
 * query parameters.
 */
@Component({
  template: '<router-outlet></router-outlet>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPage {

}
