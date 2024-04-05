import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, input } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommandHistoryEntry, CommandHistoryRecord, CommandSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CommandDetailComponent } from '../../command-history/command-detail/command-detail.component';
import { SendCommandWizardStepComponent } from '../send-command-wizard-step/send-command-wizard-step.component';

@Component({
  standalone: true,
  templateUrl: './command-report.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommandDetailComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    SendCommandWizardStepComponent,
    WebappSdkModule,
  ],
})
export class CommandReportComponent implements OnInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'command' });
  commandId = input.required<string>();

  private commandSubscription: CommandSubscription;
  command$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  constructor(readonly yamcs: YamcsService, private title: Title) { }

  ngOnInit(): void {
    this.title.setTitle(this.qualifiedName());

    const commandId = this.commandId();
    this.commandSubscription = this.yamcs.yamcsClient.createCommandSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      ignorePastCommands: false,
    }, wsEntry => {
      if (wsEntry.id === commandId) {
        this.mergeEntry(wsEntry, false);
      }
    });
    this.commandSubscription.addReplyListener(() => {
      this.yamcs.yamcsClient.getCommandHistoryEntry(this.yamcs.instance!, commandId).then(entry => {
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
