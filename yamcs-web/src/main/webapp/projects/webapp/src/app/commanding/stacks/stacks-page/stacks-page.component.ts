import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';

@Component({
  standalone: true,
  selector: 'app-stacks-page',
  templateUrl: './stacks-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstancePageTemplateComponent,
    InstanceToolbarComponent,
    WebappSdkModule,
  ],
})
export class StacksPageComponent {

  constructor(title: Title, readonly yamcs: YamcsService) {
    title.setTitle('Command stacks');
  }
}
