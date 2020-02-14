import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { EditGroupRequest, GroupInfo } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { AddMembersDialog, MemberItem } from './AddMembersDialog';

@Component({
  templateUrl: './EditGroupPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditGroupPage implements OnDestroy {

  form: FormGroup;
  group$: Promise<GroupInfo>;
  private group: GroupInfo;

  memberItems$ = new BehaviorSubject<MemberItem[]>([]);

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    formBuilder: FormBuilder,
    title: Title,
    private router: Router,
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private messageService: MessageService,
    private dialog: MatDialog,
    readonly location: Location,
  ) {
    title.setTitle('Edit Group');
    const name = route.snapshot.paramMap.get('name')!;
    this.group$ = yamcs.yamcsClient.getGroup(name);
    this.group$.then(group => {
      this.group = group;
      this.form = formBuilder.group({
        name: new FormControl(group.name),
        description: new FormControl(group.description),
      });
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.dirty$.next(true);
      });
      const memberItems: MemberItem[] = [];
      if (group.users) {
        for (const user of group.users) {
          memberItems.push({
            label: user.displayName || user.name,
            user: user,
          });
        }
      }
      this.updateMemberItems(memberItems, false);
    });
  }

  showAddMembersDialog() {
    const dialogRef = this.dialog.open(AddMembersDialog, {
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

  private updateMemberItems(items: MemberItem[], dirty = true) {
    items.sort((i1, i2) => (i1.label < i2.label) ? -1 : (i1.label > i2.label) ? 1 : 0);
    this.memberItems$.next(items);
    this.dirty$.next(dirty);
  }

  deleteItem(item: MemberItem) {
    this.updateMemberItems(this.memberItems$.value.filter(i => i !== item));
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: EditGroupRequest = {
      newName: formValue.name,
      description: formValue.description,
      memberInfo: {
        users: this.memberItems$.value.filter(item => item.user).map(item => item.user!.name),
      }
    };

    const newName = formValue.name;
    this.yamcs.yamcsClient.editGroup(this.group.name, options)
      .then(() => this.router.navigateByUrl(`/admin/iam/groups/${newName}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
