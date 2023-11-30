import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { TimelineBand, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { resolveProperties } from '../properties';
import { propertyInfo } from './Spacer';

@Component({
  selector: 'app-edit-spacer',
  templateUrl: './EditSpacerComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditSpacerComponent implements AfterViewInit {

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
    const props = resolveProperties(propertyInfo, this.band.properties || {});

    // Angular does not seem to have form.addGroup. So we get creative.
    // The properties sub-group is set in the parent component, and here
    // we append to it in a roundabout way.

    const propConfig: any = {
      height: [props.height, [Validators.required]],
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
