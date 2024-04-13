import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { FormGroup, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CreateTimelineItemRequest, MessageService, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { itemPropertyInfo } from '../item-band/ItemBand';
import { ItemStylesComponent } from '../item-band/item-styles/item-styles.component';
import { resolveProperties } from '../shared/properties';

const OVERRIDE_SUFFIX = '_overrideBand';

@Component({
  standalone: true,
  templateUrl: './create-item-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ItemStylesComponent,
    WebappSdkModule,
  ],
})
export class CreateItemDialogComponent implements OnDestroy {

  startConstraintOptions: YaSelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;
  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<CreateItemDialogComponent>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const props = resolveProperties(itemPropertyInfo, {});
    this.form = formBuilder.group({
      name: ['', Validators.required],
      start: [utils.toISOString(yamcs.getMissionTime()), Validators.required],
      duration: ['', Validators.required],
      tags: [[], []],
      properties: formBuilder.group({
        backgroundColor: [props.backgroundColor, []],
        backgroundColor_overrideBand: false,
        borderColor: [props.borderColor, []],
        borderColor_overrideBand: false,
        borderWidth: [props.borderWidth, []],
        borderWidth_overrideBand: false,
        cornerRadius: [props.cornerRadius, []],
        cornerRadius_overrideBand: false,
        marginLeft: [props.marginLeft, []],
        marginLeft_overrideBand: false,
        textColor: [props.textColor, []],
        textColor_overrideBand: false,
        textSize: [props.textSize, []],
        textSize_overrideBand: false,
      }),
    });

    this.updateDisabledState();
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.updateDisabledState();
    });
  }

  private updateDisabledState() {
    const { controls } = this.propertiesGroup;
    for (const key in controls) {
      if (key.endsWith(OVERRIDE_SUFFIX)) {
        const styleControl = controls[key.substring(0, key.length - OVERRIDE_SUFFIX.length)];
        if (controls[key].value) {
          styleControl.enable({ onlySelf: true });
        } else {
          styleControl.disable({ onlySelf: true });
        }
      }
    }
  }

  get propertiesGroup(): FormGroup {
    return this.form.controls['properties'] as FormGroup;
  }

  save() {
    const options: CreateTimelineItemRequest = {
      type: this.data.type,
      name: this.form.value['name'],
      start: utils.toISOString(this.form.value['start']),
      duration: this.form.value['duration'],
      tags: this.form.value['tags'],
      properties: {},
    };
    const { controls: propControls } = this.propertiesGroup;
    for (const key in propControls) {
      if (key.endsWith(OVERRIDE_SUFFIX) && propControls[key].value) {
        const propName = key.substring(0, key.length - OVERRIDE_SUFFIX.length);
        options.properties![propName] = propControls[propName].value;
      }
    }

    this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, options)
      .then(item => this.dialogRef.close(item))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
