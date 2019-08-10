import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { GroupInfo } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './GroupPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupPage {

  group$ = new BehaviorSubject<GroupInfo | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
  ) {
    route.paramMap.subscribe(params => {
      const name = params.get('name')!;
      this.changeGroup(name);
    });
  }

  private changeGroup(name: string) {
    this.yamcs.yamcsClient.getGroup(name).then(group => {
      this.group$.next(group);
      this.title.setTitle(group.name);
    });
  }
}
