import { DragDropModule } from '@angular/cdk/drag-drop';
import { OverlayModule } from '@angular/cdk/overlay';
import { CdkTableModule } from '@angular/cdk/table';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterModule } from '@angular/router';
import { BinaryInputComponent } from '../public-api';
import { BreadcrumbTrailComponent } from './components/breadcrumb/breadcrumb-trail.component';
import { BreadcrumbComponent } from './components/breadcrumb/breadcrumb.component';
import { ColumnChooserComponent } from './components/column-chooser/column-chooser.component';
import { DateTimeInputComponent } from './components/date-time-input/date-time-input.component';
import { DetailPaneComponent } from './components/detail-pane/detail-pane.component';
import { DetailToolbarComponent } from './components/detail-toolbar/detail-toolbar.component';
import { DotsComponent } from './components/dots/dots.component';
import { DownloadButtonComponent } from './components/download-button/download-button.component';
import { DownloadMenuItemComponent } from './components/download-menu-item/download-menu-item.component';
import { DurationInputComponent } from './components/duration-input/duration-input.component';
import { EmptyMessageComponent } from './components/empty-message/empty-message.component';
import { ErrorsComponent } from './components/errors/errors.component';
import { ExpirableComponent } from './components/expirable/expirable.component';
import { HelpComponent } from './components/help/help.component';
import { HelpDialog } from './components/help/help.dialog';
import { HexIntegerInputComponent } from './components/hex-integer-input/hex-integer-input.component';
import { HighlightComponent } from './components/highlight/highlight.component';
import { IconActionComponent } from './components/icon-action/icon-action.component';
import { IntervalComponent } from './components/interval/interval.component';
import { LabelComponent } from './components/label/label.component';
import { LabelsComponent } from './components/labels/labels.component';
import { LedComponent } from './components/led/led.component';
import { MessageBarComponent } from './components/message-bar/message-bar.component';
import { MoreComponent } from './components/more/more.component';
import { MultiSelectComponent } from './components/multi-select/multi-select.component';
import { ProgressComponent } from './components/progress/progress.component';
import { SearchFilterComponent } from './components/search-filter/search-filter.component';
import { SelectComponent } from './components/select/select.component';
import { SidebarNavGroupComponent } from './components/sidebar/sidebar-nav-group.component';
import { SidebarNavItemComponent } from './components/sidebar/sidebar-nav-item.component';
import { TextActionComponent } from './components/text-action/text-action.component';
import { TimezoneSelectComponent } from './components/timezone-select/timezone-select.component';
import { TitleCopyComponent } from './components/title-copy/title-copy.component';
import { ToolbarActionsComponent } from './components/toolbar-actions/toolbar-actions.component';
import { ValueComponent } from './components/value/value.component';
import { WarningMessageComponent } from './components/warning-message/warning-message.component';
import { DataTableDirective } from './directives/data-table.directive';
import { SimpleTableDirective } from './directives/simple-table.directive';
import { TableDirective } from './directives/table.directive';
import { ActionLogFormatPipe } from './pipes/action-log-format.pipe';
import { AliasPipe } from './pipes/alias.pipe';
import { ArrayContainsPipe } from './pipes/array-contains.pipe';
import { BasenamePipe } from './pipes/basename.pipe';
import { BinarySizePipe } from './pipes/binary-size.pipe';
import { ClassNameHtmlPipe } from './pipes/class-name-html.pipe';
import { DataRatePipe } from './pipes/data-rate.pipe';
import { DateTimePipe } from './pipes/datetime.pipe';
import { DefaultProcessorPipe } from './pipes/default-processor.pipe';
import { DeltaWithPipe } from './pipes/delta-with.pipe';
import { DurationPipe } from './pipes/duration.pipe';
import { EntryForOffsetPipe } from './pipes/entry-for-offset.pipe';
import { ExtensionPipe } from './pipes/extension.pipe';
import { FilenamePipe } from './pipes/filename.pipe';
import { FormatBytesPipe } from './pipes/format-bytes.pipe';
import { HexDumpPipe } from './pipes/hex-dump.pipe';
import { HexPipe } from './pipes/hex.pipe';
import { MayAccessAdminAreaPipe } from './pipes/may-access-admin-area.pipe';
import { MemberPathPipe } from './pipes/member-path.pipe';
import { MillisDurationPipe } from './pipes/millis-duration.pipe';
import { NanosDurationPipe } from './pipes/nanos-duration.pipe';
import { NvlPipe } from './pipes/nvl.pipe';
import { OperatorPipe } from './pipes/operator.pipe';
import { ParameterTypeForPathPipe } from './pipes/parameter-type-for-path.pipe';
import { PrintJsonPipe } from './pipes/print-json.pipe';
import { ReversePipe } from './pipes/reverse.pipe';
import { ShortNamePipe } from './pipes/short-name.pipe';
import { SpaceSystemPipe } from './pipes/space-system.pipe';
import { ToValuePipe } from './pipes/to-value.pipe';
import { UnitsPipe } from './pipes/units.pipe';
import { ValuePipe } from './pipes/value.pipe';
import { PrintZoneComponent } from './print/print-zone.component';
import { PrintableDirective } from './print/printable.directive';

const pipes = [
  ActionLogFormatPipe,
  AliasPipe,
  ArrayContainsPipe,
  BasenamePipe,
  BinarySizePipe,
  ClassNameHtmlPipe,
  DataRatePipe,
  DateTimePipe,
  DefaultProcessorPipe,
  DeltaWithPipe,
  DurationPipe,
  EntryForOffsetPipe,
  ExtensionPipe,
  FilenamePipe,
  FormatBytesPipe,
  HexDumpPipe,
  HexIntegerInputComponent,
  HexPipe,
  MayAccessAdminAreaPipe,
  MemberPathPipe,
  MillisDurationPipe,
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

const directives = [
  DataTableDirective,
  PrintableDirective,
  SimpleTableDirective,
  TableDirective,
];

const sharedComponents = [
  BinaryInputComponent,
  BreadcrumbComponent,
  BreadcrumbTrailComponent,
  ColumnChooserComponent,
  DateTimeInputComponent,
  DetailPaneComponent,
  DetailToolbarComponent,
  DotsComponent,
  DownloadButtonComponent,
  DownloadMenuItemComponent,
  DurationInputComponent,
  EmptyMessageComponent,
  ErrorsComponent,
  ExpirableComponent,
  HelpComponent,
  HelpDialog,
  HighlightComponent,
  IconActionComponent,
  IntervalComponent,
  LabelComponent,
  LabelsComponent,
  LedComponent,
  MessageBarComponent,
  MoreComponent,
  MultiSelectComponent,
  PrintZoneComponent,
  ProgressComponent,
  SearchFilterComponent,
  SelectComponent,
  SidebarNavGroupComponent,
  SidebarNavItemComponent,
  TextActionComponent,
  TimezoneSelectComponent,
  TitleCopyComponent,
  ToolbarActionsComponent,
  ValueComponent,
  WarningMessageComponent,
];

const materialModules = [
  OverlayModule,
  CdkTableModule,
  DragDropModule,
  MatAutocompleteModule,
  MatBadgeModule,
  MatButtonModule,
  MatDatepickerModule,
  MatDialogModule,
  MatIconModule,
  MatListModule,
  MatMenuModule,
  MatNativeDateModule,
  MatPaginatorModule,
  MatSidenavModule,
  MatSlideToggleModule,
  MatSortModule,
  MatSnackBarModule,
  MatTableModule,
  MatTabsModule,
  MatToolbarModule,
  MatTooltipModule,
];

@NgModule({
  imports: [
    CommonModule,
    HttpClientModule, // Used when adding custom SVGs to icon registry
    ReactiveFormsModule,
    RouterModule,
    materialModules,
  ],
  declarations: [
    directives,
    sharedComponents,
    pipes,
  ],
  exports: [
    CommonModule,
    HttpClientModule,
    ReactiveFormsModule,
    RouterModule,
    directives,
    materialModules,
    sharedComponents,
    pipes,
  ],
  providers: [
    pipes, // Make pipes available in components too
  ],
})
export class WebappSdkModule { }
