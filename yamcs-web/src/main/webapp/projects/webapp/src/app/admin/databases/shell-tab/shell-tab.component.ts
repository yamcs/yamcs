import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './shell-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ShellTabComponent {

}
