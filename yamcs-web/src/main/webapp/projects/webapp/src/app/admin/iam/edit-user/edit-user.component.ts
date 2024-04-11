import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { EditUserRequest, MessageService, UserInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { AddRolesDialogComponent, RoleItem } from '../add-roles-dialog/add-roles-dialog.component';

@Component({
  standalone: true,
  templateUrl: './edit-user.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class EditUserComponent implements OnDestroy {

  form: UntypedFormGroup;
  user$: Promise<UserInfo>;
  private user: UserInfo;

  roleItems$ = new BehaviorSubject<RoleItem[]>([]);

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    formBuilder: UntypedFormBuilder,
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
        displayName: new UntypedFormControl(user.displayName),
        email: new UntypedFormControl(user.email),
        active: new UntypedFormControl(user.active),
        superuser: new UntypedFormControl(user.superuser),
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
    const dialogRef = this.dialog.open(AddRolesDialogComponent, {
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
    this.formSubscription?.unsubscribe();
  }
}
