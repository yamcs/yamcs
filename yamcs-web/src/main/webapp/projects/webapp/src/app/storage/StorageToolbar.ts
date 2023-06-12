import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-storage-toolbar',
  templateUrl: './StorageToolbar.html',
  styleUrls: ['./StorageToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StorageToolbar {
}
