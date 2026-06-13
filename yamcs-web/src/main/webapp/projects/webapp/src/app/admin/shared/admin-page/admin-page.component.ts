import { Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-admin-page',
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.css',
  imports: [WebappSdkModule],
})
export class AdminPageComponent {
  @Input()
  noscroll = false;
}
