import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './CreateBandPage.html',
  styleUrls: ['./CreateBandPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBandPage {

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
  ) {
    title.setTitle('Create a Band');
  }
}
