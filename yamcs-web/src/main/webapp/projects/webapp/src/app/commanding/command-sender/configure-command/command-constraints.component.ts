import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import {
  Command,
  MillisDurationPipe,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { ExpressionComponent } from '../../../shared/expression/expression.component';
import { LiveExpressionComponent } from '../../../shared/live-expression/live-expression.component';

@Component({
  selector: 'app-command-constraints',
  templateUrl: './command-constraints.component.html',
  styleUrl: './command-constraints.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ExpressionComponent,
    LiveExpressionComponent,
    MillisDurationPipe,
    WebappSdkModule,
  ],
})
export class CommandConstraintsComponent {
  command = input<Command | null>(null);
  relto = input<string | null>(null);
}
