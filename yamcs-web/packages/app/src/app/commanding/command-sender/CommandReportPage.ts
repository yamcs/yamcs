import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
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

  private commandSubscription: Subscription;
  command$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
  ) {
    const id = route.snapshot.paramMap.get('commandId')!;

    this.instance = yamcs.getInstance();

    yamcs.getInstanceClient()!.getCommandUpdates({
      ignorePastCommands: false,
    }).then(response => {
      yamcs.getInstanceClient()!.getCommandHistoryEntry(id).then(command => {
        this.command$.next(new CommandHistoryRecord(command));
        this.commandSubscription = response.command$.pipe(
          filter(entry => printCommandId(entry.commandId) === id),
        ).subscribe(entry => {
          const rec = this.command$.value!;
          const mergedRec = rec.mergeEntry(entry);
          this.command$.next(mergedRec);
        });
      });
    });
  }

  ngOnDestroy() {
    if (this.commandSubscription) {
      this.commandSubscription.unsubscribe();
    }
  }
}
