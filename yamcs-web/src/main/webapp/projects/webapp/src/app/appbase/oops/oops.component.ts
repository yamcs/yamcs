import { ChangeDetectionStrategy, Component } from '@angular/core';

/*
  This image is inlined, because we may need to render it when the server is down too.
 */

@Component({
  selector: 'app-oops',
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './oops.component.html',
})
export class OopsComponent {}
