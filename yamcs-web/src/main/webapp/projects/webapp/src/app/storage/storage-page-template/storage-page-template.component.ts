import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-storage-page',
  templateUrl: './storage-page-template.component.html',
  styleUrl: './storage-page-template.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class StoragePageTemplateComponent {

  @Input()
  noscroll = false;
}
