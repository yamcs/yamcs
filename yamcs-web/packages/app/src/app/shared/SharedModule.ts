import { OverlayModule } from '@angular/cdk/overlay';
import { CdkTableModule } from '@angular/cdk/table';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatBadgeModule, MatButtonModule, MatButtonToggleModule, MatCardModule, MatCheckboxModule, MatDatepickerModule, MatDialogModule, MatExpansionModule, MatGridListModule, MatIconModule, MatInputModule, MatListModule, MatMenuModule, MatNativeDateModule, MatPaginatorModule, MatRadioModule, MatSelectModule, MatSidenavModule, MatSliderModule, MatSlideToggleModule, MatSnackBarModule, MatSortModule, MatTableModule, MatTabsModule, MatToolbarModule, MatTooltipModule } from '@angular/material';
import { RouterModule } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlCommandQueueGuard } from '../core/guards/MayControlCommandQueueGuard';
import { MayControlServicesGuard } from '../core/guards/MayControlServicesGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { MayReadTablesGuard } from '../core/guards/MayReadTablesGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { UnselectInstanceGuard } from '../core/guards/UnselectInstanceGuard';
import { HelpDialog } from './dialogs/HelpDialog';
import { SelectInstanceDialog } from './dialogs/SelectInstanceDialog';
import { SelectParameterDialog } from './dialogs/SelectParameterDialog';
import { SessionExpiredDialog } from './dialogs/SessionExpiredDialog';
import { AgoPipe } from './pipes/AgoPipe';
import { DateTimePipe } from './pipes/DateTimePipe';
import { DeltaWithPipe } from './pipes/DeltaWith';
import { DurationPipe } from './pipes/DurationPipe';
import { FilenamePipe } from './pipes/FilenamePipe';
import { FormatBytesPipe } from './pipes/FormatBytesPipe';
import { NvlPipe } from './pipes/NvlPipe';
import { OperatorPipe } from './pipes/OperatorPipe';
import { PrintJsonPipe } from './pipes/PrintJsonPipe';
import { SuperuserPipe } from './pipes/SuperuserPipe';
import { UnitsPipe } from './pipes/UnitsPipe';
import { ValuePipe } from './pipes/ValuePipe';
import { ServicesTable } from './services/ServicesTable';
import { ServiceState } from './services/ServiceState';
import { ActionLink } from './template/ActionLink';
import { Ago } from './template/Ago';
import { AlarmLevel } from './template/AlarmLevel';
import { ColumnChooser } from './template/ColumnChooser';
import { YaDataTableComponent } from './template/DataTableDirective';
import { DetailPane } from './template/DetailPane';
import { DetailToolbar } from './template/DetailToolbar';
import { Dots } from './template/Dots';
import { EmptyMessage } from './template/EmptyMessage';
import { Expirable } from './template/Expirable';
import { InstancePage } from './template/InstancePage';
import { InstancePageTemplate } from './template/InstancePageTemplate';
import { InstanceToolbar } from './template/InstanceToolbar';
import { Interval } from './template/Interval';
import { Select } from './template/Select';
import { SidebarNavItem } from './template/SidebarNavItem';
import { SignificanceLevel } from './template/SignificanceLevel';
import { YaSimpleTableComponent } from './template/SimpleTableDirective';
import { StartReplayDialog } from './template/StartReplayDialog';
import { TabDetailIcon } from './template/TabDetailIcon';
import { YaTableComponent } from './template/TableDirective';
import { TextAction } from './template/TextAction';
import { ToolbarActions } from './template/ToolbarActions';
import { AlarmLabel } from './widgets/AlarmLabel';
import { ConnectedLabel } from './widgets/ConnectedLabel';
import { Help } from './widgets/Help';
import { Hex } from './widgets/Hex';
import { Label } from './widgets/Label';
import { Labels } from './widgets/Labels';
import { ParameterLegend } from './widgets/ParameterLegend';
import { ParameterPlot } from './widgets/ParameterPlot';
import { ParameterSeries } from './widgets/ParameterSeries';
import { SlantedLabel } from './widgets/SlantedLabel';
import { TimestampTracker } from './widgets/TimestampTracker';

const materialModules = [
  OverlayModule,
  CdkTableModule,
  MatAutocompleteModule,
  MatBadgeModule,
  MatButtonModule,
  MatButtonToggleModule,
  MatCardModule,
  MatCheckboxModule,
  MatDatepickerModule,
  MatDialogModule,
  MatExpansionModule,
  MatGridListModule,
  MatIconModule,
  MatInputModule,
  MatListModule,
  MatMenuModule,
  MatNativeDateModule,
  MatPaginatorModule,
  MatRadioModule,
  MatSelectModule,
  MatSidenavModule,
  MatSliderModule,
  MatSlideToggleModule,
  MatSortModule,
  MatSnackBarModule,
  MatTableModule,
  MatTabsModule,
  MatToolbarModule,
  MatTooltipModule,
];

const sharedDirectives = [
  YaDataTableComponent,
  YaSimpleTableComponent,
  YaTableComponent,
];

const sharedComponents = [
  ActionLink,
  Ago,
  AlarmLabel,
  AlarmLevel,
  ColumnChooser,
  ConnectedLabel,
  DetailPane,
  DetailToolbar,
  Dots,
  EmptyMessage,
  Expirable,
  Help,
  HelpDialog,
  Hex,
  InstancePage,
  InstancePageTemplate,
  InstanceToolbar,
  Interval,
  Label,
  Labels,
  ParameterLegend,
  ParameterPlot,
  ParameterSeries,
  ServiceState,
  ServicesTable,
  SidebarNavItem,
  SelectInstanceDialog,
  SelectParameterDialog,
  Select,
  SessionExpiredDialog,
  SignificanceLevel,
  SlantedLabel,
  StartReplayDialog,
  TabDetailIcon,
  TextAction,
  TimestampTracker,
  ToolbarActions,
];

const pipes = [
  AgoPipe,
  DateTimePipe,
  DeltaWithPipe,
  DurationPipe,
  FilenamePipe,
  FormatBytesPipe,
  NvlPipe,
  OperatorPipe,
  PrintJsonPipe,
  SuperuserPipe,
  UnitsPipe,
  ValuePipe,
];

const guards = [
  AuthGuard,
  InstanceExistsGuard,
  MayControlCommandQueueGuard,
  MayControlServicesGuard,
  MayGetMissionDatabaseGuard,
  MayReadEventsGuard,
  MayReadTablesGuard,
  SuperuserGuard,
  UnselectInstanceGuard,
];

@NgModule({
  imports: [
    CommonModule,
    HttpClientModule,
    ReactiveFormsModule,
    RouterModule,
    materialModules,
  ],
  declarations: [
    sharedDirectives,
    sharedComponents,
    pipes,
  ],
  exports: [
    CommonModule,
    HttpClientModule,
    ReactiveFormsModule,
    RouterModule,
    materialModules,
    sharedDirectives,
    sharedComponents,
    pipes,
  ],
  entryComponents: [
    HelpDialog,
    SelectInstanceDialog,
    SelectParameterDialog,
    SessionExpiredDialog,
    StartReplayDialog,
  ],
  providers: [
    guards,
    pipes, // Make pipes available in components too
  ]
})
export class SharedModule {
}
