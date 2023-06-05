import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TraceElementInfo } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-trace-element',
  templateUrl: './TraceElement.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TraceElement {

  @Input()
  element: TraceElementInfo;
}
