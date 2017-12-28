import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  template: '<router-outlet></router-outlet>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MdbComponent {
}
