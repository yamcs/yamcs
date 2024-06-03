import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommandHistoryEntry, CommandHistoryRecord, CommandSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CommandDetailComponent } from '../command-detail/command-detail.component';

@Component({
  standalone: true,
  templateUrl: './command.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommandDetailComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class CommandComponent {

  private commandSubscription: CommandSubscription;
  command$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
  ) {
    const id = route.snapshot.paramMap.get('commandId')!;
    yamcs.yamcsClient.getCommandHistoryEntry(yamcs.instance!, id).then(entry => {
      this.mergeEntry(entry);
      this.commandSubscription = yamcs.yamcsClient.createCommandSubscription({
        instance: yamcs.instance!,
        processor: yamcs.processor!,
        ignorePastCommands: false,
      }, wsEntry => {
        if (wsEntry.id === id) {
          this.mergeEntry(wsEntry);
        }
      });
    });
  }

  private mergeEntry(entry: CommandHistoryEntry) {
    const rec = this.command$.value;
    if (rec) {
      const mergedRec = rec.mergeEntry(entry);
      this.command$.next(mergedRec);
    } else {
      this.command$.next(new CommandHistoryRecord(entry));
    }
  }

  ngOnDestroy() {
    this.commandSubscription?.cancel();
  }
}
