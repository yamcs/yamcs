import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './support-page.component.html',
  styleUrl: './support-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class SupportPageComponent {}