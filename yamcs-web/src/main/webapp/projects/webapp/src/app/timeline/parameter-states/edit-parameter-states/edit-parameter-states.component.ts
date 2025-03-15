import { TitleCasePipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
} from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';
import { TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { resolveProperties } from '../../shared/properties';
import {
  createValueMappingPropertyInfo,
  propertyInfo,
  resolveValueMappingProperties,
} from '../ParameterStateBand';
import { ParameterStatesStylesComponent } from '../parameter-states-styles/parameter-states-styles.component';

@Component({
  selector: 'app-edit-parameter-states',
  templateUrl: './edit-parameter-states.component.html',
  styleUrl: './edit-parameter-states.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ParameterStatesStylesComponent, TitleCasePipe, WebappSdkModule],
})
export class EditParameterStatesComponent implements AfterViewInit {
  @Input()
  form: FormGroup;

  @Input()
  band: TimelineBand;

  formConfigured$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private changeDetection: ChangeDetectorRef,
    private formBuilder: FormBuilder,
  ) {}

  ngAfterViewInit() {
    const props = resolveProperties(propertyInfo, this.band.properties || {});

    // Angular does not seem to have form.addGroup. So we get creative.
    // The properties sub-group is set in the parent component, and here
    // we append to it in a roundabout way.

    const propConfig: any = {
      frozen: [props.frozen, [Validators.required]],
      height: [props.height, [Validators.required]],
      parameter: [props.parameter, [Validators.required]],
    };

    const propertiesGroup = this.form.get('properties') as FormGroup;
    for (const controlName in propConfig) {
      const config = propConfig[controlName];
      propertiesGroup.addControl(
        controlName,
        new FormControl(config[0], config[1]),
      );
    }

    let idx = 0;
    while (true) {
      const mappingPropertyInfo = createValueMappingPropertyInfo(idx);
      const mappingProperties = resolveValueMappingProperties(
        idx,
        mappingPropertyInfo,
        this.band.properties || {},
      );
      if (!mappingProperties.type) {
        break;
      }
      idx++;
      if (mappingProperties.type === 'value') {
        const mappingForm = this.addValueMapping();
        mappingForm.patchValue(mappingProperties);
      } else if (mappingProperties.type === 'range') {
        const mappingForm = this.addRangeMapping();
        mappingForm.patchValue(mappingProperties);
      }
    }

    this.formConfigured$.next(true);
    this.changeDetection.detectChanges();
  }

  get valueMappings() {
    return this.form.controls['valueMappings'] as FormArray;
  }

  addValueMapping() {
    const form = this.formBuilder.group({
      type: ['value', [Validators.required]],
      value: ['', [Validators.required]],
      label: [''],
      color: [''],
    });
    this.valueMappings.push(form);
    return form;
  }

  addRangeMapping() {
    const form = this.formBuilder.group({
      type: ['range', [Validators.required]],
      start: ['', [Validators.required]],
      end: ['', [Validators.required]],
      label: [''],
      color: [''],
    });
    this.valueMappings.push(form);
    return form;
  }

  removeMapping(index: number) {
    this.valueMappings.removeAt(index);
  }

  moveUp(index: number) {
    const form = this.valueMappings.at(index);
    this.valueMappings.removeAt(index);
    this.valueMappings.insert(index - 1, form);
  }

  moveDown(index: number) {
    const form = this.valueMappings.at(index);
    this.valueMappings.removeAt(index);
    this.valueMappings.insert(index + 1, form);
  }
}
