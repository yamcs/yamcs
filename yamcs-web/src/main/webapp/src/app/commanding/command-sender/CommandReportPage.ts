import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { CommandHistoryEntry, CommandSubscription, Instance } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { printCommandId } from '../../shared/utils';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';

@Component({
  templateUrl: './CommandReportPage.html',
  styleUrls: ['./CommandReportPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandReportPage implements OnDestroy {

  instance: Instance;

  private commandSubscription: CommandSubscription;
  command$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
  ) {
    const id = route.snapshot.paramMap.get('commandId')!;

    this.instance = yamcs.getInstance();

    yamcs.yamcsClient.getCommandHistoryEntry(this.instance.name, id).then(entry => {
      this.mergeEntry(entry);
      this.commandSubscription = yamcs.yamcsClient.createCommandSubscription({
        instance: this.instance.name,
        processor: yamcs.getProcessor().name,
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
