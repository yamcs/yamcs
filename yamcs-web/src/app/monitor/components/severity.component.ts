import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-severity',
  templateUrl: './severity.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeverityComponent {

  @Input()
  severity: string;
}
