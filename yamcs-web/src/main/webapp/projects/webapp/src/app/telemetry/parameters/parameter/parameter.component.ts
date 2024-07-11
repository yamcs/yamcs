import { ChangeDetectionStrategy, Component, OnChanges, OnDestroy, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ConfigService, Formatter, MessageService, Parameter, ParameterSubscription, ParameterValue, Value, WebsiteConfig, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { SetParameterDialogComponent } from '../set-parameter-dialog/set-parameter-dialog.component';

import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './parameter.component.html',
  styleUrl: './parameter.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ParameterComponent implements OnChanges, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'parameter' });

  config: WebsiteConfig;
  parameter$ = new BehaviorSubject<Parameter | null>(null);
  offset$ = new BehaviorSubject<string | null>(null);

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: ParameterSubscription;

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private messageService: MessageService,
    private dialog: MatDialog,
    private title: Title,
    private formatter: Formatter,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();
  }

  ngOnChanges() {
    const qualifiedName = this.qualifiedName();
    this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, qualifiedName).then(parameter => {
      this.parameter$.next(parameter);

      if (qualifiedName !== parameter.qualifiedName) {
        this.offset$.next(qualifiedName.substring(parameter.qualifiedName.length));
      } else {
        this.offset$.next(null);
      }

      this.updateTitle();
    }).catch(err => {
      this.messageService.showError(err);
    });

    if (this.parameterValueSubscription) {
      this.parameterValueSubscription.cancel();
    }

    this.parameterValueSubscription = this.yamcs.yamcsClient.createParameterSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      id: [{ name: qualifiedName }],
      abortOnInvalid: false,
      sendFromCache: true,
      updateOnExpiration: true,
      action: 'REPLACE',
    }, data => {
      this.parameterValue$.next(data.values ? data.values[0] : null);
      this.updateTitle();
    });
  }

  updateTitle() {
    const parameter = this.parameter$.getValue();
    const offset = this.offset$.getValue();
    if (parameter) {
      let title = parameter.name;
      if (offset) {
        title += offset;
      }
      const pval = this.parameterValue$.getValue();
      if (pval?.engValue) {
        title += ': ' + this.formatter.formatValue(pval.engValue);
        if (parameter.type && parameter.type.unitSet) {
          title += ' ' + utils.getUnits(parameter.type.unitSet);
        }
        if (pval.rangeCondition && pval.rangeCondition === 'LOW') {
          title += ' ↓';
        } else if (pval.rangeCondition && pval.rangeCondition === 'HIGH') {
          title += ' ↑';
        }
      }
      this.title.setTitle(title);
    }
  }

  isWritable() {
    const parameter = this.parameter$.value;
    if (parameter) {
      return parameter.dataSource === 'LOCAL'
        || parameter.dataSource === 'EXTERNAL1'
        || parameter.dataSource === 'EXTERNAL2'
        || parameter.dataSource === 'EXTERNAL3';
    }
    return false;
  }

  maySetParameter() {
    const parameter = this.parameter$.value;
    if (parameter) {
      return this.authService.getUser()!.hasObjectPrivilege('WriteParameter', parameter.qualifiedName);
    }
    return false;
  }

  mayReadAlarms() {
    return this.authService.getUser()!.hasSystemPrivilege('ReadAlarms');
  }

  mayReadMissionDatabase() {
    return this.authService.getUser()!.hasSystemPrivilege('GetMissionDatabase');
  }

  setParameter() {
    const parameter = this.parameter$.value!;
    const dialogRef = this.dialog.open(SetParameterDialogComponent, {
      width: '600px',
      data: {
        parameter: this.parameter$.value
      }
    });
    dialogRef.afterClosed().subscribe((value: Value) => {
      if (value) {
        this.yamcs.yamcsClient
          .setParameterValue(this.yamcs.instance!, this.yamcs.processor!, parameter.qualifiedName, value)
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  ngOnDestroy() {
    this.parameterValueSubscription?.cancel();
  }
}
