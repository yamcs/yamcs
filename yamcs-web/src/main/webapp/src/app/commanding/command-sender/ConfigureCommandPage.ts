import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Command, CommandHistoryEntry, Instance } from '../../client';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandForm } from './CommandForm';

@Component({
  templateUrl: './ConfigureCommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfigureCommandPage implements AfterViewInit {

  @ViewChild('commandForm')
  commandForm: CommandForm;

  @ViewChild('another', { static: false })
  anotherChild: ElementRef;

  instance: Instance;
  config: WebsiteConfig;

  command$ = new BehaviorSubject<Command | null>(null);
  template$ = new BehaviorSubject<CommandHistoryEntry | null>(null);

  armControl = new FormControl();

  constructor(
    route: ActivatedRoute,
    private router: Router,
    title: Title,
    private messageService: MessageService,
    private yamcs: YamcsService,
    private location: Location,
    configService: ConfigService,
  ) {
    this.instance = yamcs.getInstance();
    this.config = configService.getConfig();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;

    title.setTitle(`Send a command: ${qualifiedName}`);

    const promises: Promise<any>[] = [
      this.yamcs.getInstanceClient()!.getCommand(qualifiedName),
    ];

    const templateId = route.snapshot.queryParamMap.get('template');
    if (templateId) {
      const promise = this.yamcs.getInstanceClient()!.getCommandHistoryEntry(templateId);
      promises.push(promise);
    }

    Promise.all(promises).then(responses => {
      const command = responses[0];
      let template: CommandHistoryEntry | undefined;
      if (responses.length > 1) {
        template = responses[1];
      }
      this.command$.next(command);
      this.template$.next(template || null);
    });
  }

  ngAfterViewInit() {
    if (this.config.twoStageCommanding) {
      this.commandForm.form.valueChanges.subscribe(() => {
        this.armControl.setValue(false);
      });
      this.commandForm.form.statusChanges.subscribe(() => {
        if (this.commandForm.form.valid) {
          this.armControl.enable();
        } else {
          this.armControl.disable();
        }
      });
    }
  }

  goBack() {
    this.location.back();
  }

  sendCommand() {
    this.armControl.setValue(false);

    const assignments = this.commandForm.getAssignments();
    const comment = this.commandForm.getComment();

    const processor = this.yamcs.getProcessor();
    const qname = this.command$.value!.qualifiedName;
    this.yamcs.getInstanceClient()!.issueCommand(processor.name, qname, {
      assignment: assignments,
      comment,
    }).then(response => {
      this.router.navigate(['/commanding/report', response.id], {
        queryParams: {
          instance: this.instance.name,
        }
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
  }
}
