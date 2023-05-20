import { DragDropModule } from '@angular/cdk/drag-drop';
import { OverlayModule } from '@angular/cdk/overlay';
import { CdkTableModule } from '@angular/cdk/table';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatLegacyAutocompleteModule } from '@angular/material/legacy-autocomplete';
import { MatLegacyButtonModule } from '@angular/material/legacy-button';
import { MatLegacyCardModule } from '@angular/material/legacy-card';
import { MatLegacyCheckboxModule } from '@angular/material/legacy-checkbox';
import { MatLegacyDialogModule } from '@angular/material/legacy-dialog';
import { MatLegacyInputModule } from '@angular/material/legacy-input';
import { MatLegacyListModule } from '@angular/material/legacy-list';
import { MatLegacyMenuModule } from '@angular/material/legacy-menu';
import { MatLegacyPaginatorModule } from '@angular/material/legacy-paginator';
import { MatLegacyProgressBarModule } from '@angular/material/legacy-progress-bar';
import { MatLegacyRadioModule } from '@angular/material/legacy-radio';
import { MatLegacySelectModule } from '@angular/material/legacy-select';
import { MatLegacySlideToggleModule } from '@angular/material/legacy-slide-toggle';
import { MatLegacySliderModule } from '@angular/material/legacy-slider';
import { MatLegacySnackBarModule } from '@angular/material/legacy-snack-bar';
import { MatLegacyTableModule } from '@angular/material/legacy-table';
import { MatLegacyTabsModule } from '@angular/material/legacy-tabs';
import { MatLegacyTooltipModule } from '@angular/material/legacy-tooltip';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSortModule } from '@angular/material/sort';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterModule } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { ClearContextGuard } from '../core/guards/ClearContextGuard';
import { MayAccessAdminAreaGuard } from '../core/guards/MayAccessAdminAreaGuard';
import { MayControlAccessGuard } from '../core/guards/MayControlAccessGuard';
import { MayControlArchivingGuard } from '../core/guards/MayControlArchivingGuard';
import { MayControlCommandQueueGuard } from '../core/guards/MayControlCommandQueueGuard';
import { MayControlServicesGuard } from '../core/guards/MayControlServicesGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { MayReadSystemInfoGuard } from '../core/guards/MayReadSystemInfoGuard';
import { OpenIDCallbackGuard } from '../core/guards/OpenIDCallbackGuard';
import { ServerSideOpenIDCallbackGuard } from '../core/guards/ServerSideOpenIDCallbackGuard';
import { Breadcrumb } from './breadcrumb/Breadcrumb';
import { BreadcrumbTrail } from './breadcrumb/BreadcrumbTrail';
import { HelpDialog } from './dialogs/HelpDialog';
import { SelectInstanceDialog } from './dialogs/SelectInstanceDialog';
import { SelectParameterDialog } from './dialogs/SelectParameterDialog';
import { SessionExpiredDialog } from './dialogs/SessionExpiredDialog';
import { BinaryInput } from './forms/BinaryInput';
import { CommandSelector } from './forms/CommandSelector';
import { DateTimeInput } from './forms/DateTimeInput';
import { DownloadButton } from './forms/DownloadButton';
import { DownloadMenuItem } from './forms/DownloadMenuItem';
import { DurationInput } from './forms/DurationInput';
import { Errors } from './forms/Errors';
import { HexIntegerInput } from './forms/HexIntegerInput';
import { MultiSelect } from './forms/MultiSelect';
import { ObjectSelector } from './forms/ObjectSelector';
import { SearchFilter } from './forms/SearchFilter';
import { Select } from './forms/Select';
import { TimezoneSelect } from './forms/TimezoneSelect';
import { Hex } from './hex/Hex';
import { Highlight } from './highlight/Highlight';
import { Markdown } from './markdown/Markdown';
import { More } from './more/More';
import { ActionLogFormatPipe } from './pipes/ActionLogFormat';
import { AgoPipe } from './pipes/AgoPipe';
import { AliasPipe } from './pipes/AliasPipe';
import { ArrayContainsPipe } from './pipes/ArrayContainsPipe';
import { BasenamePipe } from './pipes/BasenamePipe';
import { BinarySizePipe } from './pipes/BinarySizePipe';
import { ClassNameHtmlPipe } from './pipes/ClassNameHtmlPipe';
import { DataRatePipe } from './pipes/DataRatePipe';
import { DateTimePipe } from './pipes/DateTimePipe';
import { DefaultProcessorPipe } from './pipes/DefaultProcessorPipe';
import { DeltaWithPipe } from './pipes/DeltaWith';
import { DurationPipe } from './pipes/DurationPipe';
import { EffectiveSignificancePipe } from './pipes/EffectiveSignificancePipe';
import { EntryForOffsetPipe } from './pipes/EntryForOffsetPipe';
import { ExtensionPipe } from './pipes/ExtensionPipe';
import { FilenamePipe } from './pipes/FilenamePipe';
import { FormatBytesPipe } from './pipes/FormatBytesPipe';
import { HexDumpPipe } from './pipes/HexDumpPipe';
import { HexPipe } from './pipes/HexPipe';
import { MayAccessAdminAreaPipe } from './pipes/MayAccessAdminAreaPipe';
import { MemberPathPipe } from './pipes/MemberPathPipe';
import { NanosDurationPipe } from './pipes/NanosDurationPipe';
import { NvlPipe } from './pipes/NvlPipe';
import { OperatorPipe } from './pipes/OperatorPipe';
import { ParameterTypeForPathPipe } from './pipes/ParameterTypeForPathPipe';
import { PrintJsonPipe } from './pipes/PrintJsonPipe';
import { ReversePipe } from './pipes/ReversePipe';
import { ShortNamePipe } from './pipes/ShortNamePipe';
import { SpaceSystemPipe } from './pipes/SpaceSystemPipe';
import { ToValuePipe } from './pipes/ToValuePipe';
import { UnitsPipe } from './pipes/UnitsPipe';
import { ValuePipe } from './pipes/ValuePipe';
import { PrintZone } from './print/PrintZone';
import { PrintableDirective } from './print/PrintableDirective';
import { Progress } from './progress/Progress';
import { SidebarNavGroup } from './sidebar/SidebarNavGroup';
import { SidebarNavItem } from './sidebar/SidebarNavItem';
import { YaDataTableComponent } from './table/DataTableDirective';
import { YaSimpleTableComponent } from './table/SimpleTableDirective';
import { YaTableComponent } from './table/TableDirective';
import { Ago } from './template/Ago';
import { AlarmLevel } from './template/AlarmLevel';
import { ColumnChooser } from './template/ColumnChooser';
import { DetailPane } from './template/DetailPane';
import { DetailToolbar } from './template/DetailToolbar';
import { Dots } from './template/Dots';
import { EmptyMessage } from './template/EmptyMessage';
import { Expirable } from './template/Expirable';
import { IconAction } from './template/IconAction';
import { InstancePage } from './template/InstancePage';
import { InstancePageTemplate } from './template/InstancePageTemplate';
import { InstanceToolbar } from './template/InstanceToolbar';
import { Interval } from './template/Interval';
import { Led } from './template/Led';
import { MessageBar } from './template/MessageBar';
import { SignificanceLevel } from './template/SignificanceLevel';
import { StartReplayDialog } from './template/StartReplayDialog';
import { TabDetailIcon } from './template/TabDetailIcon';
import { TextAction } from './template/TextAction';
import { ToolbarActions } from './template/ToolbarActions';
import { WarningMessage } from './template/WarningMessage';
import { TitleCopy } from './title-copy/TitleCopy';
import { ValueComponent } from './value/ValueComponent';
import { AlarmLabel } from './widgets/AlarmLabel';
import { Help } from './widgets/Help';
import { Label } from './widgets/Label';
import { Labels } from './widgets/Labels';
import { LiveExpression } from './widgets/LiveExpression';
import { ParameterLegend } from './widgets/ParameterLegend';
import { ParameterPlot } from './widgets/ParameterPlot';
import { ParameterSeries } from './widgets/ParameterSeries';
import { SlantedLabel } from './widgets/SlantedLabel';
import { TimestampTracker } from './widgets/TimestampTracker';

const materialModules = [
  OverlayModule,
  CdkTableModule,
  DragDropModule,
  MatLegacyAutocompleteModule,
  MatBadgeModule,
  MatLegacyButtonModule,
  MatButtonToggleModule,
  MatLegacyCardModule,
  MatLegacyCheckboxModule,
  MatDatepickerModule,
  MatLegacyDialogModule,
  MatExpansionModule,
  MatGridListModule,
  MatIconModule,
  MatLegacyInputModule,
  MatLegacyListModule,
  MatLegacyMenuModule,
  MatNativeDateModule,
  MatLegacyPaginatorModule,
  MatLegacyProgressBarModule,
  MatLegacyRadioModule,
  MatLegacySelectModule,
  MatSidenavModule,
  MatLegacySliderModule,
  MatLegacySlideToggleModule,
  MatSortModule,
  MatLegacySnackBarModule,
  MatLegacyTableModule,
  MatLegacyTabsModule,
  MatToolbarModule,
  MatLegacyTooltipModule,
];

const sharedDirectives = [
  PrintableDirective,
  YaDataTableComponent,
  YaSimpleTableComponent,
  YaTableComponent,
];

const sharedComponents = [
  Ago,
  AlarmLabel,
  AlarmLevel,
  BinaryInput,
  Breadcrumb,
  BreadcrumbTrail,
  ColumnChooser,
  CommandSelector,
  DateTimeInput,
  DetailPane,
  DetailToolbar,
  Dots,
  DownloadButton,
  DownloadMenuItem,
  DurationInput,
  EmptyMessage,
  Errors,
  Expirable,
  Help,
  HelpDialog,
  Hex,
  HexIntegerInput,
  Highlight,
  IconAction,
  InstancePage,
  InstancePageTemplate,
  InstanceToolbar,
  Interval,
  Label,
  Labels,
  Led,
  LiveExpression,
  Markdown,
  MessageBar,
  More,
  MultiSelect,
  ObjectSelector,
  ParameterLegend,
  ParameterPlot,
  ParameterSeries,
  PrintZone,
  Progress,
  SearchFilter,
  SelectInstanceDialog,
  SelectParameterDialog,
  Select,
  SessionExpiredDialog,
  SidebarNavGroup,
  SidebarNavItem,
  SignificanceLevel,
  SlantedLabel,
  StartReplayDialog,
  TabDetailIcon,
  TextAction,
  TimestampTracker,
  TimezoneSelect,
  TitleCopy,
  ToolbarActions,
  ValueComponent,
  WarningMessage,
];

const pipes = [
  ActionLogFormatPipe,
  AliasPipe,
  AgoPipe,
  ArrayContainsPipe,
  BasenamePipe,
  BinarySizePipe,
  ClassNameHtmlPipe,
  DataRatePipe,
  DateTimePipe,
  DefaultProcessorPipe,
  DeltaWithPipe,
  DurationPipe,
  EffectiveSignificancePipe,
  ExtensionPipe,
  FilenamePipe,
  FormatBytesPipe,
  EntryForOffsetPipe,
  HexDumpPipe,
  HexPipe,
  MayAccessAdminAreaPipe,
  MemberPathPipe,
  NanosDurationPipe,
  NvlPipe,
  OperatorPipe,
  ParameterTypeForPathPipe,
  PrintJsonPipe,
  ReversePipe,
  ShortNamePipe,
  SpaceSystemPipe,
  ToValuePipe,
  UnitsPipe,
  ValuePipe,
];

const guards = [
  AuthGuard,
  ClearContextGuard,
  AttachContextGuard,
  MayAccessAdminAreaGuard,
  MayControlAccessGuard,
  MayControlArchivingGuard,
  MayControlCommandQueueGuard,
  MayControlServicesGuard,
  MayGetMissionDatabaseGuard,
  MayReadEventsGuard,
  MayReadSystemInfoGuard,
  OpenIDCallbackGuard,
  ServerSideOpenIDCallbackGuard,
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
  providers: [
    guards,
    pipes, // Make pipes available in components too
  ]
})
export class SharedModule {
}
