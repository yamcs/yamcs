import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService, TimelineBand, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './CreateViewPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateViewPage {

  form: UntypedFormGroup;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private formBuilder: UntypedFormBuilder,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Create a View');
    this.form = this.formBuilder.group({
      name: ['', [Validators.required]],
      bands: [[], []],
    });
  }

  onConfirm() {
    const formValue = this.form.value;
    this.yamcs.yamcsClient.createTimelineView(this.yamcs.instance!, {
      name: formValue.name,
      bands: formValue.bands.map((v: TimelineBand) => v.id),
    }).then(view => this.router.navigateByUrl(`/timeline/chart?c=${this.yamcs.context}&view=${view.id}`))
      .catch(err => this.messageService.showError(err));
  }
}
