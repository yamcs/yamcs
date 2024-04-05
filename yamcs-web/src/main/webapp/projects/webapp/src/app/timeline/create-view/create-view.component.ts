import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService, TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { BandMultiSelectComponent } from '../shared/band-multi-select/band-multi-select.component';

@Component({
  standalone: true,
  templateUrl: './create-view.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BandMultiSelectComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class CreateViewComponent {

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
