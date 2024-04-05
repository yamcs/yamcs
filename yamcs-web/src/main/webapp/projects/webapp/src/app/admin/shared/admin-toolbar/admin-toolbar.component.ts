import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-admin-toolbar',
  templateUrl: './admin-toolbar.component.html',
  styleUrl: './admin-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AdminToolbarComponent {
}
