import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Clearance, Command, CommandHistoryEntry, CommandOptionType, ConfigService, MessageService, Value, WebsiteConfig, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { CommandForm, TemplateProvider } from './CommandForm';

@Component({
  templateUrl: './ConfigureCommandPage.html',
  styleUrls: ['./ConfigureCommandPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfigureCommandPage implements AfterViewInit, OnDestroy {

  @ViewChild('commandForm')
  commandForm: CommandForm;

  config: WebsiteConfig;

  command$ = new BehaviorSubject<Command | null>(null);
  templateProvider$ = new BehaviorSubject<TemplateProvider | null>(null);
  cleared$ = new BehaviorSubject<boolean>(true);

  private connectionInfoSubscription: Subscription;

  armControl = new UntypedFormControl();

  constructor(
    route: ActivatedRoute,
    private router: Router,
    title: Title,
    private messageService: MessageService,
    readonly yamcs: YamcsService,
    private location: Location,
    configService: ConfigService,
    authService: AuthService,
    private changeDetection: ChangeDetectorRef,
  ) {
    this.config = configService.getConfig();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;

    title.setTitle(`Send a command: ${qualifiedName}`);

    const promises: Promise<any>[] = [
      this.yamcs.yamcsClient.getCommand(this.yamcs.instance!, qualifiedName),
    ];

    const templateId = route.snapshot.queryParamMap.get('template');
    if (templateId) {
      const promise = this.yamcs.yamcsClient.getCommandHistoryEntry(this.yamcs.instance!, templateId);
      promises.push(promise);
    }

    Promise.all(promises).then(responses => {
      const command = responses[0] as Command;
      let template: CommandHistoryEntry | undefined;
      if (responses.length > 1) {
        template = responses[1];
      }
      this.command$.next(command);
      if (template) {
        this.templateProvider$.next(new CommandHistoryTemplateProvider(template));
      } else {
        this.templateProvider$.next(null);
      }

      if (this.config.commandClearanceEnabled) {
        this.connectionInfoSubscription = yamcs.clearance$.subscribe(clearance => {
          const significance = command.effectiveSignificance;
          this.cleared$.next(this.isCleared(clearance, significance?.consequenceLevel));
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

    this.commandForm.hasArguments$.subscribe(hasArguments => {
      this.changeDetection.detectChanges();
    });
  }

  goBack() {
    this.location.back();
  }

  sendCommand() {
    this.armControl.setValue(false);

    const args = this.commandForm.getAssignments();
    const comment = this.commandForm.getComment();
    const extra = this.commandForm.getExtraOptions();

    const qname = this.command$.value!.qualifiedName;

    this.yamcs.yamcsClient.issueCommand(this.yamcs.instance!, this.yamcs.processor!, qname, {
      args,
      comment,
      extra,
    }).then(response => {
      this.router.navigate(['/commanding/report', response.id], {
        queryParams: {
          c: this.yamcs.context,
        }
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
  }

  private isCleared(clearance: Clearance | null, level?: string) {
    if (!clearance) {
      return false;
    }

    switch (clearance.level) {
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

export class CommandHistoryTemplateProvider implements TemplateProvider {

  constructor(private entry: CommandHistoryEntry) {
  }

  getAssignment(argumentName: string) {
    if (this.entry.assignments) {
      for (const assignment of this.entry.assignments) {
        if (assignment.name === argumentName) {
          return assignment.value;
        }
      }
    }
  }

  getOption(id: string, expectedType: CommandOptionType) {
    for (const attr of (this.entry.attr || [])) {
      if (attr.name === id) {
        switch (expectedType) {
          case 'BOOLEAN':
            return this.getBooleanOption(attr.value);
          case 'NUMBER':
            return this.getNumberOption(attr.value);
          case 'STRING':
            return this.getStringOption(attr.value);
        }
      }
    }
  }

  private getBooleanOption(value: Value) {
    if (value.type === 'BOOLEAN') {
      return value;
    }
  }

  private getNumberOption(value: Value) {
    switch (value.type) {
      case 'SINT32':
      case 'UINT32':
      case 'SINT64':
      case 'UINT64':
        return value;
    }
  }

  private getStringOption(value: Value) {
    if (value.type === 'STRING') {
      return value;
    }
  }

  getComment() {
    // Don't copy
  }
}
