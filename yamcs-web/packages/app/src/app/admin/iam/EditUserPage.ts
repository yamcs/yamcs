import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { EditUserRequest, UserInfo } from '@yamcs/client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './EditUserPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditUserPage {

  form: FormGroup;
  user$: Promise<UserInfo>;
  private user: UserInfo;

  constructor(
    formBuilder: FormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private messageService: MessageService,
    readonly location: Location,
  ) {
    title.setTitle('Edit User');
    const username = route.snapshot.paramMap.get('username')!;
    this.user$ = yamcs.yamcsClient.getUser(username);
    this.user$.then(user => {
      this.user = user;
      this.form = formBuilder.group({
        displayName: new FormControl(user.displayName),
        email: new FormControl(user.email),
        active: new FormControl(user.active),
        superuser: new FormControl(user.superuser),
        password: new FormControl(),
        passwordConfirmation: new FormControl(),
      });
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: EditUserRequest = {};
    if (formValue.displayName !== this.user.displayName) {
      options.displayName = formValue.displayName;
    }
    if (formValue.email !== this.user.email) {
      options.email = formValue.email;
    }
    if (formValue.active !== this.user.active) {
      options.active = formValue.active;
    }
    if (formValue.superuser !== this.user.superuser) {
      options.superuser = formValue.superuser;
    }
    if (formValue.password) {
      if (formValue.password !== formValue.passwordConfirmation) {
        alert('Password confirmation does not match password');
        return;
      }
      options.password = formValue.password;
    }

    this.yamcs.yamcsClient.editUser(this.user.name, options)
      .then(() => this.router.navigate(['..'], { relativeTo: this.route }))
      .catch(err => this.messageService.showError(err));
  }
}
