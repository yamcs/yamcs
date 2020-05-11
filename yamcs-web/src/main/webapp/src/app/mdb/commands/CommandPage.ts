import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Command } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPage {

  command$ = new BehaviorSubject<Command | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private title: Title,
  ) {
    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeCommand(qualifiedName);
    });
  }

  changeCommand(qualifiedName: string) {
    this.yamcs.yamcsClient.getCommand(this.yamcs.instance!, qualifiedName).then(command => {
      this.command$.next(command);
      this.title.setTitle(command.name);
    });
  }
}
