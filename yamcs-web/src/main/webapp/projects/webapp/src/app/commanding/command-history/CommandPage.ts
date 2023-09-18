import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommandHistoryEntry, CommandHistoryRecord, CommandSubscription, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './CommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPage {

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
