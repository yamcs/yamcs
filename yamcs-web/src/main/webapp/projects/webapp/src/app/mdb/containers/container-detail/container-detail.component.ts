import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Container, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

@Component({
  standalone: true,
  selector: 'app-container-detail',
  templateUrl: './container-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MarkdownComponent,
    WebappSdkModule,
  ],
})
export class ContainerDetailComponent {

  @Input()
  container: Container;

  constructor(readonly yamcs: YamcsService) {
  }
}
