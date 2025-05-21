import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { StackFilePageTabsComponent } from '../stack-file-page-tabs/stack-file-page-tabs.component';
import { StackFileService } from '../stack-file/StackFileService';

@Component({
  selector: 'app-stack-file-log',
  templateUrl: './stack-file-log.component.html',
  styleUrl: './stack-file-log.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StackFilePageTabsComponent, WebappSdkModule],
})
export class StackFileLogComponent {
  objectName = input.required<string>();

  folderLink = computed(() => {
    const objectName = this.objectName();
    const idx = objectName.lastIndexOf('/');
    if (idx === -1) {
      return '/procedures/stacks/browse/';
    } else {
      const folderName = objectName.substring(0, idx);
      return '/procedures/stacks/browse/' + folderName;
    }
  });

  constructor(
    readonly yamcs: YamcsService,
    readonly stackFileService: StackFileService,
  ) {}
}
