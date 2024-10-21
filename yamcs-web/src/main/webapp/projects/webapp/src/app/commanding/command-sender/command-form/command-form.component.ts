import { ChangeDetectionStrategy, Component, Input, viewChild } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Command, User, WebappSdkModule, YaStepper, YaStepperStep } from '@yamcs/webapp-sdk';
import { AuthService } from '../../../core/services/AuthService';
import { CommandArgumentsForm } from './command-arguments-form.component';
import { CommandCommentForm } from './command-comment-form.component';
import { CommandOptionsForm } from './command-options-form.component';
import { CommandConfiguration } from './CommandConfiguration';
import { StackAdvancementForm } from './stack-advancement-form.component';
import { StackCommentForm } from './stack-comment-form.component';
import { TemplateProvider } from './TemplateProvider';

@Component({
  standalone: true,
  selector: 'app-command-form',
  templateUrl: './command-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommandArgumentsForm,
    CommandCommentForm,
    CommandOptionsForm,
    StackAdvancementForm,
    StackCommentForm,
    WebappSdkModule,
    YaStepper,
    YaStepperStep,
  ],
})
export class CommandFormComponent {

  private user: User;

  @Input()
  command: Command;

  @Input()
  templateProvider: TemplateProvider;

  @Input()
  stackMode = false;

  commandArgumentsForm = viewChild.required(CommandArgumentsForm);
  commandOptionsForm = viewChild(CommandOptionsForm);
  commandCommentForm = viewChild(CommandCommentForm);
  stackCommentForm = viewChild(StackCommentForm);
  stackAdvancementForm = viewChild(StackAdvancementForm);

  // At the top level, group all blocks in a single form,
  // makes it more convenient to check combined validation state.
  form = new FormGroup({});

  constructor(authService: AuthService) {
    this.user = authService.getUser()!;
  }

  showCommandOptions() {
    return this.user.hasSystemPrivilege('CommandOptions');
  }

  getResult(optionsStruct = false): CommandConfiguration {
    const commandArgumentsForm = this.commandArgumentsForm();
    const args = commandArgumentsForm.getResult();

    const commandOptionsForm = this.commandOptionsForm();
    const extra = commandOptionsForm?.getResult(optionsStruct) ?? {};
    const stream = commandOptionsForm?.getStream();

    let comment;
    let advancement;
    if (this.stackMode) {
      const stackCommentForm = this.stackCommentForm();
      comment = stackCommentForm?.getResult();

      const stackAdvancementForm = this.stackAdvancementForm();
      advancement = stackAdvancementForm?.getResult();
    } else {
      const commandCommentForm = this.commandCommentForm();
      comment = commandCommentForm?.getResult();
    }

    return {
      args,
      extra,
      stream,
      comment,
      advancement,
    };
  }
}
