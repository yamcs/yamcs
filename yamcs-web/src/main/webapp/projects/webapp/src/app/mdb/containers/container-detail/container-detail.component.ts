import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Container, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { ExpressionComponent } from '../../../shared/expression/expression.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

@Component({
  selector: 'app-container-detail',
  templateUrl: './container-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ExpressionComponent, MarkdownComponent, WebappSdkModule],
})
export class ContainerDetailComponent {
  @Input()
  container: Container;

  constructor(readonly yamcs: YamcsService) {}
}
