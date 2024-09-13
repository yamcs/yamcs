import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { LiveExpressionComponent } from '../../../shared/live-expression/live-expression.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';
import { StackedCommandEntry } from '../stack-file/StackedEntry';

@Component({
  standalone: true,
  selector: 'app-stacked-command-detail',
  templateUrl: './stacked-command-detail.component.html',
  styleUrl: './stacked-command-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LiveExpressionComponent,
    MarkdownComponent,
    WebappSdkModule,
    SignificanceLevelComponent,
  ]
})
export class StackedCommandDetailComponent {

  @Input()
  entry: StackedCommandEntry;
}
