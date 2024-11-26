import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-stack-file-page-tabs',
  templateUrl: './stack-file-page-tabs.component.html',
  styleUrl: './stack-file-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StackFilePageTabsComponent {

  objectName = input.required<string>();

  constructor(readonly yamcs: YamcsService) {
  }
}
