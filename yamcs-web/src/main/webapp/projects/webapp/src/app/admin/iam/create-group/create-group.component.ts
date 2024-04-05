import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CreateGroupRequest, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { AddMembersDialogComponent, MemberItem } from '../add-members-dialog/add-members-dialog.component';

@Component({
  standalone: true,
  templateUrl: './create-group.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class CreateGroupComponent {

  form: UntypedFormGroup;

  memberItems$ = new BehaviorSubject<MemberItem[]>([]);

  constructor(
    formBuilder: UntypedFormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    readonly location: Location,
  ) {
    title.setTitle('Create a Group');
    this.form = formBuilder.group({
      name: new UntypedFormControl('', [Validators.required]),
      description: new UntypedFormControl(),
    });
  }

  showAddMembersDialog() {
    const dialogRef = this.dialog.open(AddMembersDialogComponent, {
      data: {
        items: this.memberItems$.value,
      },
      width: '600px',
    });
    dialogRef.afterClosed().subscribe(memberItems => {
      if (memberItems) {
        this.updateMemberItems([
          ...this.memberItems$.value,
          ...memberItems,
        ]);
      }
    });
  }

  private updateMemberItems(items: MemberItem[]) {
    items.sort((i1, i2) => (i1.label < i2.label) ? -1 : (i1.label > i2.label) ? 1 : 0);
    this.memberItems$.next(items);
  }

  deleteItem(item: MemberItem) {
    this.updateMemberItems(this.memberItems$.value.filter(i => i !== item));
  }

  onConfirm() {
    const options: CreateGroupRequest = {
      name: this.form.value.name,
      description: this.form.value.description,
      users: this.memberItems$.value.filter(item => item.user).map(item => item.user!.name),
    };
    this.yamcs.yamcsClient.createGroup(options)
      .then(() => this.router.navigate(['..'], { relativeTo: this.route }))
      .catch(err => this.messageService.showError(err));
  }
}
