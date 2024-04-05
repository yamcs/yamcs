import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TraceElementInfo, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-trace-element',
  templateUrl: './trace-element.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TraceElementComponent {

  @Input()
  element: TraceElementInfo;
}
