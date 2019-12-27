import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { SystemInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AdminHomePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminHomePage {

  info$ = new BehaviorSubject<SystemInfo | null>(null);

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Admin Area');
    this.refresh();
  }

  refresh() {
    this.yamcs.yamcsClient.getSystemInfo().then(info => this.info$.next(info));
  }
}
