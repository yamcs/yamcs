import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-edit-time-ruler',
  templateUrl: './edit-time-ruler.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class EditTimeRulerComponent implements AfterViewInit {

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
    // Angular does not seem to have form.addGroup. So we get creative.
    // The properties sub-group is set in the parent component, and here
    // we append to it in a roundabout way.

    const propConfig: any = {
      timezone: [this.band.properties!['timezone'], [Validators.required]],
    };

    const propertiesGroup = this.form.get('properties') as UntypedFormGroup;
    for (const controlName in propConfig) {
      const config = propConfig[controlName];
      propertiesGroup.addControl(controlName, new UntypedFormControl(config[0], config[1]));
    }

    this.formConfigured$.next(true);
    this.changeDetection.detectChanges();
  }
}
