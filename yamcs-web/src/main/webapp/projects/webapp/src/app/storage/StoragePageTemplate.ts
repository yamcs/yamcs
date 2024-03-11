import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-storage-page',
  templateUrl: './StoragePageTemplate.html',
  styleUrl: './StoragePageTemplate.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StoragePageTemplate {

  @Input()
  noscroll = false;
}
