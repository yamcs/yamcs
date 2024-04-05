import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { resolveProperties } from '../../shared/properties';
import { propertyInfo } from '../ItemBand';
import { ItemBandStylesComponent } from '../item-band-styles/item-band-styles.component';

@Component({
  standalone: true,
  selector: 'app-edit-item-band',
  templateUrl: './edit-item-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ItemBandStylesComponent,
    WebappSdkModule,
  ],
})
export class EditItemBandComponent implements AfterViewInit {

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
      frozen: [props.frozen, [Validators.required]],
      itemBackgroundColor: [props.itemBackgroundColor, [Validators.required]],
      itemBorderColor: [props.itemBorderColor, [Validators.required]],
      itemBorderWidth: [props.itemBorderWidth, [Validators.required]],
      itemCornerRadius: [props.itemCornerRadius, [Validators.required]],
      itemHeight: [props.itemHeight, [Validators.required]],
      itemMarginLeft: [props.itemMarginLeft, [Validators.required]],
      itemTextColor: [props.itemTextColor, [Validators.required]],
      itemTextOverflow: [props.itemTextOverflow, [Validators.required]],
      itemTextSize: [props.itemTextSize, [Validators.required]],
      marginBottom: [props.marginBottom, [Validators.required]],
      marginTop: [props.marginTop, [Validators.required]],
      multiline: [props.multiline, [Validators.required]],
      spaceBetweenItems: [props.spaceBetweenItems, [Validators.required]],
      spaceBetweenLines: [props.spaceBetweenLines, [Validators.required]],
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
