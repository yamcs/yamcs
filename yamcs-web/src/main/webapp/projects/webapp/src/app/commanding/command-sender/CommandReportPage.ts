import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommandHistoryEntry, CommandHistoryRecord, CommandSubscription, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './CommandReportPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandReportPage implements OnDestroy {

  private commandSubscription: CommandSubscription;
  command$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
  ) {
    const id = route.snapshot.paramMap.get('commandId')!;

    this.commandSubscription = yamcs.yamcsClient.createCommandSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
      ignorePastCommands: false,
    }, wsEntry => {
      if (wsEntry.id === id) {
        this.mergeEntry(wsEntry, false);
      }
    });
    this.commandSubscription.addReplyListener(() => {
      yamcs.yamcsClient.getCommandHistoryEntry(this.yamcs.instance!, id).then(entry => {
        this.mergeEntry(entry, true /* append ws replies to rest response */);
      });
    });
  }

  private mergeEntry(entry: CommandHistoryEntry, reverse: boolean) {
    const rec = this.command$.value;
    if (rec) {
      const mergedRec = rec.mergeEntry(entry, reverse);
      this.command$.next(mergedRec);
    } else {
      this.command$.next(new CommandHistoryRecord(entry));
    }
  }

  ngOnDestroy() {
    this.commandSubscription?.cancel();
  }
}
