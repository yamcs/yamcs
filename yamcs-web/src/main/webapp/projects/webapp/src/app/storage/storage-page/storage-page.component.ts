import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  templateUrl: './storage-page.component.html',
  styleUrl: './storage-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class StoragePageComponent {
}
