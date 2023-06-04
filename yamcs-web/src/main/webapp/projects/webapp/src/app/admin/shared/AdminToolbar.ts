import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-admin-toolbar',
  templateUrl: './AdminToolbar.html',
  styleUrls: ['./AdminToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminToolbar {
}
