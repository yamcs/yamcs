import { Component, input } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-stack-file-page-tabs',
  templateUrl: './stack-file-page-tabs.component.html',
  styleUrl: './stack-file-page-tabs.component.css',
  imports: [WebappSdkModule],
})
export class StackFilePageTabsComponent {
  objectName = input.required<string>();

  constructor(readonly yamcs: YamcsService) {}
}
