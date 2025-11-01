import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  OnChanges,
} from '@angular/core';
import { FormControl } from '@angular/forms';
import {
  AuthService,
  Command,
  mdb,
  MillisDurationPipe,
  User,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { ExpressionComponent } from '../../../shared/expression/expression.component';
import { LiveExpressionComponent } from '../../../shared/live-expression/live-expression.component';
import { TemplateProvider } from '../command-form/TemplateProvider';

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
export class CommandConstraintsComponent implements OnChanges {
  command = input<Command | null>(null);
  relto = input<string | null>(null);
  templateProvider = input<TemplateProvider>();

  constraints = computed(() => {
    const command = this.command();
    return command ? mdb.getAllConstraints(command) : [];
  });

  disableConstraintCheckingControl = new FormControl(false);

  private user: User;

  constructor(authService: AuthService) {
    this.user = authService.getUser()!;
  }

  showCommandOptions() {
    return this.user.hasSystemPrivilege('CommandOptions');
  }

  ngOnChanges(): void {
    const templateProvider = this.templateProvider();
    if (templateProvider && this.showCommandOptions()) {
      const disabled = templateProvider.isDisableTransmissionConstraints();
      this.disableConstraintCheckingControl.setValue(disabled);
    }
  }

  isDisableConstraintChecking() {
    return this.disableConstraintCheckingControl.value;
  }
}
