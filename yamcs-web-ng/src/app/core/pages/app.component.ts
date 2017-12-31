import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-root',
  template: '<router-outlet></router-outlet>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {

  title = 'Yamcs';
}
