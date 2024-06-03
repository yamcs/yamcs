import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-admin-page',
  templateUrl: './admin-page-template.component.html',
  styleUrl: './admin-page-template.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AdminPageTemplateComponent {

  @Input()
  noscroll = false;
}
