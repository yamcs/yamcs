import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Command, Instance } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPage {

  instance: Instance;
  command$ = new BehaviorSubject<Command | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
  ) {
    this.instance = yamcs.getInstance();

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeCommand(qualifiedName);
    });
  }

  changeCommand(qualifiedName: string) {
    this.yamcs.yamcsClient.getCommand(this.instance.name, qualifiedName).then(command => {
      this.command$.next(command);
      this.title.setTitle(command.name);
    });
  }
}
