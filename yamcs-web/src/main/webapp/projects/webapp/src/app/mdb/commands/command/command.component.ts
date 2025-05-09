import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Command, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { CommandDetailComponent } from '../command-detail/command-detail.component';

@Component({
  templateUrl: './command.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommandDetailComponent, WebappSdkModule],
})
export class CommandComponent {
  command$ = new BehaviorSubject<Command | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private title: Title,
  ) {
    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe((params) => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeCommand(qualifiedName);
    });
  }

  changeCommand(qualifiedName: string) {
    this.yamcs.yamcsClient
      .getCommand(this.yamcs.instance!, qualifiedName)
      .then((command) => {
        this.command$.next(command);
        this.title.setTitle(command.name);
      });
  }
}
