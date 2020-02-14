import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { CreateGroupRequest } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { AddMembersDialog, MemberItem } from './AddMembersDialog';

@Component({
  templateUrl: './CreateGroupPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateGroupPage {

  form: FormGroup;

  memberItems$ = new BehaviorSubject<MemberItem[]>([]);

  constructor(
    formBuilder: FormBuilder,
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
      name: new FormControl('', [Validators.required]),
      description: new FormControl(),
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
