import { Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-storage-page',
  templateUrl: './storage-page.component.html',
  styleUrl: './storage-page.component.css',
  imports: [WebappSdkModule],
})
export class StoragePageComponent {
  @Input()
  noscroll = false;
}
