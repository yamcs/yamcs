import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { CreateUserRequest, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './create-user.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class CreateUserComponent {

  form: UntypedFormGroup;

  constructor(
    formBuilder: UntypedFormBuilder,
    title: Title,
    private router: Router,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    title.setTitle('Create a User');
    this.form = formBuilder.group({
      name: new UntypedFormControl('', [Validators.required]),
      displayName: new UntypedFormControl(),
      email: new UntypedFormControl(),
      password: new UntypedFormControl(),
      passwordConfirmation: new UntypedFormControl(),
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: CreateUserRequest = {
      name: formValue.name,
    };
    if (formValue.displayName) {
      options.displayName = formValue.displayName;
    }
    if (formValue.email) {
      options.email = formValue.email;
    }
    if (formValue.password) {
      if (formValue.password !== formValue.passwordConfirmation) {
        alert('Password confirmation does not match password');
        return;
      }
      options.password = formValue.password;
    }
    this.yamcs.yamcsClient.createUser(options)
      .then(() => this.router.navigateByUrl(`/admin/iam/users/${formValue.name}`))
      .catch(err => this.messageService.showError(err));
  }
}
