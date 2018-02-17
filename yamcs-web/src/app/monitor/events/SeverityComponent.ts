import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-severity',
  templateUrl: './SeverityComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeverityComponent {

  @Input()
  severity: string;
}
