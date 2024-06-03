import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-storage-toolbar',
  templateUrl: './storage-toolbar.component.html',
  styleUrl: './storage-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StorageToolbarComponent {
}
