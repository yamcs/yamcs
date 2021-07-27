import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { TimelineBand } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './EditBandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditBandPage {

  band$: Promise<TimelineBand>;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    route: ActivatedRoute,
    private router: Router,
    readonly location: Location,
  ) {
    title.setTitle('Edit Band');
    const id = route.snapshot.paramMap.get('band')!;
    this.band$ = yamcs.yamcsClient.getTimelineBand(yamcs.instance!, id);
    this.band$.catch(err => this.messageService.showError(err));
  }

  onConfirm(band: TimelineBand) {
    this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`);
  }
}
