import { NgModule } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { SelectInstanceDialog } from './dialogs/SelectInstanceDialog';
import { SelectParameterDialog } from './dialogs/SelectParameterDialog';
import { SessionExpiredDialog } from './dialogs/SessionExpiredDialog';
import { CommandSelector } from './forms/CommandSelector';
import { ObjectSelector } from './forms/ObjectSelector';
import { Hex } from './hex/Hex';
import { Markdown } from './markdown/Markdown';
import { AgoPipe } from './pipes/AgoPipe';
import { Ago } from './template/Ago';
import { AlarmLevel } from './template/AlarmLevel';
import { InstancePage } from './template/InstancePage';
import { InstancePageTemplate } from './template/InstancePageTemplate';
import { InstanceToolbar } from './template/InstanceToolbar';
import { SignificanceLevel } from './template/SignificanceLevel';
import { StartReplayDialog } from './template/StartReplayDialog';
import { AlarmLabel } from './widgets/AlarmLabel';
import { LiveExpression } from './widgets/LiveExpression';
import { ParameterLegend } from './widgets/ParameterLegend';
import { ParameterPlot } from './widgets/ParameterPlot';
import { ParameterSeries } from './widgets/ParameterSeries';
import { TimestampTracker } from './widgets/TimestampTracker';

const sharedComponents = [
  Ago,
  AlarmLabel,
  AlarmLevel,
  CommandSelector,
  Hex,
  InstancePage,
  InstancePageTemplate,
  InstanceToolbar,
  LiveExpression,
  Markdown,
  ObjectSelector,
  ParameterLegend,
  ParameterPlot,
  ParameterSeries,
  SelectInstanceDialog,
  SelectParameterDialog,
  SessionExpiredDialog,
  SignificanceLevel,
  StartReplayDialog,
  TimestampTracker,
];

const pipes = [
  AgoPipe,
];

@NgModule({
  imports: [
    WebappSdkModule,
  ],
  declarations: [
    sharedComponents,
    pipes,
  ],
  exports: [
    WebappSdkModule,
    sharedComponents,
    pipes,
  ],
  providers: [
    pipes, // Make pipes available in components too
  ]
})
export class SharedModule {
}
