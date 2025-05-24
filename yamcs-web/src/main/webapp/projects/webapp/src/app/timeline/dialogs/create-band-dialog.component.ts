import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  MessageService,
  SaveTimelineBandRequest,
  TimelineBand,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { CreateCommandBandComponent } from '../band-forms/create-command-band.component';
import { CreateItemBandComponent } from '../band-forms/create-item-band.component';
import { CreateParameterPlotComponent } from '../band-forms/create-parameter-plot.component';
import { CreateParameterStatesComponent } from '../band-forms/create-parameter-states.component';
import { CreateSpacerComponent } from '../band-forms/create-spacer.component';
import { CreateTimeRulerComponent } from '../band-forms/create-time-ruler.component';
import { BandTypeSelectionComponent } from './band-type-selection.component';

export interface CreateBandDialogData {
  title?: string;
  submitLabel?: string;
}

@Component({
  selector: 'app-create-band-dialog',
  templateUrl: './create-band-dialog.component.html',
  styleUrl: './create-band-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BandTypeSelectionComponent,
    CreateCommandBandComponent,
    CreateItemBandComponent,
    CreateParameterPlotComponent,
    CreateParameterStatesComponent,
    CreateSpacerComponent,
    CreateTimeRulerComponent,
    WebappSdkModule,
  ],
})
export class CreateBandDialogComponent {
  private dialogRef =
    inject<MatDialogRef<CreateBandDialogComponent, TimelineBand>>(MatDialogRef);
  readonly data = inject<CreateBandDialogData>(MAT_DIALOG_DATA);
  private messageService = inject(MessageService);
  private yamcs = inject(YamcsService);

  readonly config = {
    title: this.data?.title ?? 'Create',
    submitLabel: this.data?.submitLabel ?? 'Create band',
  };

  private commandBandEl = viewChild(CreateCommandBandComponent);
  private itemBandEl = viewChild(CreateItemBandComponent);
  private parameterPlotEl = viewChild(CreateParameterPlotComponent);
  private parameterStatesEl = viewChild(CreateParameterStatesComponent);
  private spacerEl = viewChild(CreateSpacerComponent);
  private timeRulerEl = viewChild(CreateTimeRulerComponent);

  bandType = signal<string | undefined>(undefined);

  isValid = signal<boolean>(false);

  onValidChange(valid: boolean) {
    this.isValid.set(valid);
  }

  resetBandType() {
    this.bandType.set(undefined);
    this.isValid.set(false);
  }

  createBand(bandType: string) {
    let request: SaveTimelineBandRequest | undefined = undefined;
    switch (bandType) {
      case 'COMMAND_BAND':
        request = this.commandBandEl()!.createRequest();
        break;
      case 'ITEM_BAND':
        request = this.itemBandEl()!.createRequest();
        break;
      case 'PARAMETER_PLOT':
        request = this.parameterPlotEl()!.createRequest();
        break;
      case 'PARAMETER_STATES':
        request = this.parameterStatesEl()!.createRequest();
        break;
      case 'SPACER':
        request = this.spacerEl()!.createRequest();
        break;
      case 'TIME_RULER':
        request = this.timeRulerEl()!.createRequest();
        break;
      default:
        console.error('Unexpected band type', bandType);
        return;
    }

    this.yamcs.yamcsClient
      .saveTimelineBand(this.yamcs.instance!, null, request)
      .then((band) => this.dialogRef.close(band))
      .catch((err) => this.messageService.showError(err));
  }
}
