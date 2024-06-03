import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './storage-page.component.html',
  styleUrl: './storage-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StoragePageComponent {
}
