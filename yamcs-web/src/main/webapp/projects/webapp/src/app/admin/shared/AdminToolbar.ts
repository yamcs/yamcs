import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-admin-toolbar',
  templateUrl: './AdminToolbar.html',
  styleUrl: './AdminToolbar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminToolbar {
}
