import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { EditUserRequest, UserInfo } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { AddRolesDialog, RoleItem } from './AddRolesDialog';

@Component({
  templateUrl: './EditUserPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditUserPage implements OnDestroy {

  form: FormGroup;
  user$: Promise<UserInfo>;
  private user: UserInfo;

  roleItems$ = new BehaviorSubject<RoleItem[]>([]);

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    formBuilder: FormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private messageService: MessageService,
    private dialog: MatDialog,
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
      });
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.dirty$.next(true);
      });
      const roleItems: RoleItem[] = [];
      if (user.roles) {
        for (const role of user.roles) {
          roleItems.push({
            label: role.name,
            role: role,
          });
        }
      }
      this.updateRoleItems(roleItems, false);
    });
  }

  showAddRolesDialog() {
    const dialogRef = this.dialog.open(AddRolesDialog, {
      data: {
        items: this.roleItems$.value,
      },
      width: '600px',
    });
    dialogRef.afterClosed().subscribe(roleItems => {
      if (roleItems) {
        this.updateRoleItems([
          ...this.roleItems$.value,
          ...roleItems,
        ]);
      }
    });
  }

  private updateRoleItems(items: RoleItem[], dirty = true) {
    items.sort((i1, i2) => (i1.label < i2.label) ? -1 : (i1.label > i2.label) ? 1 : 0);
    this.roleItems$.next(items);
    this.dirty$.next(dirty);
  }

  deleteItem(item: RoleItem) {
    this.updateRoleItems(this.roleItems$.value.filter(i => i !== item));
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: EditUserRequest = {
      roleAssignment: {
        roles: this.roleItems$.value.filter(item => item.role).map(item => item.role!.name),
      }
    };
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

    this.yamcs.yamcsClient.editUser(this.user.name, options)
      .then(() => this.router.navigate(['..'], { relativeTo: this.route }))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
