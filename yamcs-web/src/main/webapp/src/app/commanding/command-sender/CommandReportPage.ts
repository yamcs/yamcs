import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { CommandHistoryEntry, CommandSubscription } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { printCommandId } from '../../shared/utils';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';

@Component({
  templateUrl: './CommandReportPage.html',
  styleUrls: ['./CommandReportPage.css'],
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

    yamcs.yamcsClient.getCommandHistoryEntry(this.yamcs.instance!, id).then(entry => {
      this.mergeEntry(entry);
      this.commandSubscription = yamcs.yamcsClient.createCommandSubscription({
        instance: yamcs.instance!,
        processor: yamcs.processor!,
        ignorePastCommands: false,
      }, wsEntry => {
        if (printCommandId(wsEntry.commandId) === id) {
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
    if (this.commandSubscription) {
      this.commandSubscription.cancel();
    }
  }
}
