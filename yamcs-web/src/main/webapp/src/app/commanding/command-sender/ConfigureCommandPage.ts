import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Command, CommandHistoryEntry, ConnectionInfo } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandForm } from './CommandForm';

@Component({
  templateUrl: './ConfigureCommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfigureCommandPage implements AfterViewInit, OnDestroy {

  @ViewChild('commandForm')
  commandForm: CommandForm;

  @ViewChild('another', { static: false })
  anotherChild: ElementRef;

  instance: string;
  config: WebsiteConfig;

  command$ = new BehaviorSubject<Command | null>(null);
  template$ = new BehaviorSubject<CommandHistoryEntry | null>(null);
  cleared$ = new BehaviorSubject<boolean>(true);

  private connectionInfoSubscription: Subscription;

  armControl = new FormControl();

  constructor(
    route: ActivatedRoute,
    private router: Router,
    title: Title,
    private messageService: MessageService,
    private yamcs: YamcsService,
    private location: Location,
    configService: ConfigService,
    authService: AuthService,
  ) {
    this.instance = yamcs.getInstance();
    this.config = configService.getConfig();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;

    title.setTitle(`Send a command: ${qualifiedName}`);

    const promises: Promise<any>[] = [
      this.yamcs.yamcsClient.getCommand(this.instance, qualifiedName),
    ];

    const templateId = route.snapshot.queryParamMap.get('template');
    if (templateId) {
      const promise = this.yamcs.yamcsClient.getCommandHistoryEntry(this.instance, templateId);
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

      if (this.config.commandClearances) {
        this.connectionInfoSubscription = yamcs.connectionInfo$.subscribe(connectionInfo => {
          this.cleared$.next(this.isCleared(connectionInfo, command.significance?.consequenceLevel));
        });
      }
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
    this.yamcs.yamcsClient.issueCommand(processor.instance, processor.name, qname, {
      assignment: assignments,
      comment,
    }).then(response => {
      this.router.navigate(['/commanding/report', response.id], {
        queryParams: {
          instance: this.instance,
        }
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
  }

  private isCleared(connectionInfo: ConnectionInfo | null, level?: string) {
    if (!connectionInfo || !connectionInfo.clearance) {
      return false;
    }

    switch (connectionInfo.clearance) {
      case 'SEVERE':
        if (level === 'SEVERE') {
          return true;
        }
      // fall
      case 'CRITICAL':
        if (level === 'CRITICAL') {
          return true;
        }
      // fall
      case 'DISTRESS':
        if (level === 'DISTRESS') {
          return true;
        }
      // fall
      case 'WARNING':
        if (level === 'WARNING') {
          return true;
        }
      // fall
      case 'WATCH':
        if (level === 'WATCH') {
          return true;
        }
      // fall
      case 'NONE':
        if (level === 'NONE' || !level) {
          return true;
        }
    }

    return false;
  }

  ngOnDestroy() {
    if (this.connectionInfoSubscription) {
      this.connectionInfoSubscription.unsubscribe();
    }
  }
}
