import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-edit-command-band',
  templateUrl: './edit-command-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class EditCommandBandComponent implements AfterViewInit {

  @Input()
  form: UntypedFormGroup;

  @Input()
  band: TimelineBand;

  formConfigured$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private changeDetection: ChangeDetectorRef,
  ) { }

  ngAfterViewInit() {
    this.formConfigured$.next(true);
    this.changeDetection.detectChanges();
  }
}
