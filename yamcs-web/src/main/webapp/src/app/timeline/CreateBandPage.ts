import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './CreateBandPage.html',
  styleUrls: ['./CreateBandPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBandPage {

  timescaleForm: FormGroup;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
  ) {
    title.setTitle('Create a Band');
  }
}
