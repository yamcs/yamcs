import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { CreateUserRequest } from '@yamcs/client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateUserPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateUserPage {

  form: FormGroup;

  constructor(
    formBuilder: FormBuilder,
    title: Title,
    private router: Router,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    title.setTitle('Create a User');
    this.form = formBuilder.group({
      username: new FormControl('', [Validators.required]),
      name: new FormControl('', [Validators.required]),
      email: new FormControl('', [Validators.required]),
      password: new FormControl(),
      passwordConfirmation: new FormControl(),
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: CreateUserRequest = {
      username: formValue.username,
      name: formValue.name,
      email: formValue.email,
    };
    if (formValue.password) {
      if (formValue.password !== formValue.passwordConfirmation) {
        alert('Password confirmation does not match password');
        return;
      }
      options.password = formValue.password;
    }
    this.yamcs.yamcsClient.createUser(options)
      .then(() => this.router.navigateByUrl(`/admin/user-administration/users/${formValue.username}`))
      .catch(err => this.messageService.showError(err));
  }
}
