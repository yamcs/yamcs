import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import {
  Container,
  TimeAssociation,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
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

  formatTimeAssociation(timeAssociation?: TimeAssociation): string {
    if (!timeAssociation) {
      return '-';
    }

    const fragments = [];
    if (timeAssociation.instance !== undefined) {
      fragments.push(`instance ${timeAssociation.instance}`);
    }
    if (timeAssociation.interpolateTime !== undefined) {
      fragments.push(
        timeAssociation.interpolateTime ? 'interpolate' : 'no interpolation',
      );
    }
    if (timeAssociation.offset !== undefined) {
      const unit = timeAssociation.unit || 'si_second';
      fragments.push(`offset ${timeAssociation.offset} ${unit}`);
    }
    if (timeAssociation.useCalibratedValue !== undefined) {
      fragments.push(
        timeAssociation.useCalibratedValue ? 'calibrated' : 'raw',
      );
    }
    return fragments.length ? fragments.join(', ') : '-';
  }
}
