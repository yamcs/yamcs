import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CreateServiceAccountRequest, CreateServiceAccountResponse, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { ApplicationCredentialsDialog } from './ApplicationCredentialsDialog';

@Component({
  templateUrl: './CreateServiceAccountPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateServiceAccountPage {

  form: UntypedFormGroup;

  constructor(
    formBuilder: UntypedFormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private messageService: MessageService,
    readonly location: Location,
    private dialog: MatDialog,
  ) {
    title.setTitle('Create service account');
    this.form = formBuilder.group({
      name: new UntypedFormControl('', [Validators.required]),
    });
  }

  onConfirm() {
    const options: CreateServiceAccountRequest = {
      name: this.form.value.name,
    };
    this.yamcs.yamcsClient.createServiceAccount(options)
      .then(response => this.onServerConfirm(response))
      .catch(err => this.messageService.showError(err));
  }

  private onServerConfirm(response: CreateServiceAccountResponse) {
    const dialogRef = this.dialog.open(ApplicationCredentialsDialog, {
      disableClose: true,
      closeOnNavigation: false,
      data: response,
      width: '550px',
    });
    dialogRef.afterClosed().subscribe(() => {
      this.router.navigate(['..'], { relativeTo: this.route, });
    });
  }
}
