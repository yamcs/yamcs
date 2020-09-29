import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TraceElementInfo } from '../../client';

@Component({
  selector: 'app-trace-element',
  templateUrl: './TraceElement.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TraceElement {

  @Input()
  element: TraceElementInfo;
}
