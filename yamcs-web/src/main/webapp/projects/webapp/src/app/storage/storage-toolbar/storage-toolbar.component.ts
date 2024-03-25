import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-storage-toolbar',
  templateUrl: './storage-toolbar.component.html',
  styleUrl: './storage-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class StorageToolbarComponent {
}
