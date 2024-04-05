import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-storage-page',
  templateUrl: './storage-page-template.component.html',
  styleUrl: './storage-page-template.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StoragePageTemplateComponent {

  @Input()
  noscroll = false;
}
