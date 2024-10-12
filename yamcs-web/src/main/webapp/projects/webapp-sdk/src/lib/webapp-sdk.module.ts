import { DragDropModule } from '@angular/cdk/drag-drop';
import { OverlayModule } from '@angular/cdk/overlay';
import { CdkTableModule } from '@angular/cdk/table';
import { CommonModule } from '@angular/common';
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
import { StorageUrlPipe } from '../public-api';
import { YaBinaryInput } from './components/binary-input/binary-input.component';
import { YaBreadcrumbTrail } from './components/breadcrumb/breadcrumb-trail.component';
import { YaBreadcrumb } from './components/breadcrumb/breadcrumb.component';
import { YaButton } from './components/button/button.component';
import { YaColumnChooser } from './components/column-chooser/column-chooser.component';
import { YaDateTimeInput } from './components/date-time-input/date-time-input.component';
import { YaDetailPane } from './components/detail-pane/detail-pane.component';
import { YaDetailToolbar } from './components/detail-toolbar/detail-toolbar.component';
import { YaDots } from './components/dots/dots.component';
import { YaDownloadButton } from './components/download-button/download-button.component';
import { YaDownloadMenuItem } from './components/download-menu-item/download-menu-item.component';
import { YaDurationInput } from './components/duration-input/duration-input.component';
import { YaEmptyMessage } from './components/empty-message/empty-message.component';
import { YaErrors } from './components/errors/errors.component';
import { YaExpirable } from './components/expirable/expirable.component';
import { YaFilterInput } from './components/filter/filter-input.component';
import { YaFilterTextarea } from './components/filter/filter-textarea.component';
import { YaHelp } from './components/help/help.component';
import { YaHelpDialog } from './components/help/help.dialog';
import { YaHexIntegerInput } from './components/hex-integer-input/hex-integer-input.component';
import { YaHighlight } from './components/highlight/highlight.component';
import { YaHref } from './components/href/href.directive';
import { YaIconAction } from './components/icon-action/icon-action.component';
import { YaInterval } from './components/interval/interval.component';
import { YaLabel } from './components/label/label.component';
import { YaLabels } from './components/labels/labels.component';
import { YaLed } from './components/led/led.component';
import { YaMessageBar } from './components/message-bar/message-bar.component';
import { YaMore } from './components/more/more.component';
import { YaMultiSelect } from './components/multi-select/multi-select.component';
import { YaOption } from './components/option/option.component';
import { YaPageButton } from './components/page-button/page-button.component';
import { YaPageIconButton } from './components/page-icon-button/page-icon-button.component';
import { YaProgress } from './components/progress/progress.component';
import { YaSearchFilter } from './components/search-filter/search-filter.component';
import { YaSearchFilter2 } from './components/search-filter2/search-filter2.component';
import { YaSelect } from './components/select/select.component';
import { YaSidebarNavGroup } from './components/sidebar/sidebar-nav-group.component';
import { YaSidebarNavItem } from './components/sidebar/sidebar-nav-item.component';
import { YaTableToggle } from './components/table-toggle/table-toggle.component';
import { YaTableTop } from './components/table-top/table-top.component';
import { YaTagSelect } from './components/tag-select/tag-select.component';
import { YaTextAction } from './components/text-action/text-action.component';
import { YaTimezoneSelect } from './components/timezone-select/timezone-select.component';
import { YaTitleCopy } from './components/title-copy/title-copy.component';
import { YaToolbarActions } from './components/toolbar-actions/toolbar-actions.component';
import { YaValue } from './components/value/value.component';
import { YaWarningMessage } from './components/warning-message/warning-message.component';
import { DataTableDirective } from './directives/data-table.directive';
import { SimpleTableDirective } from './directives/simple-table.directive';
import { TableDirective } from './directives/table.directive';
import { ActionLogFormatPipe } from './pipes/action-log-format.pipe';
import { AliasPipe } from './pipes/alias.pipe';
import { ArrayContainsPipe } from './pipes/array-contains.pipe';
import { BasenamePipe } from './pipes/basename.pipe';
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
import { ParentsPipe } from './pipes/parents.pipe';
import { PrintJsonPipe } from './pipes/print-json.pipe';
import { ReversePipe } from './pipes/reverse.pipe';
import { ShortNamePipe } from './pipes/short-name.pipe';
import { SpaceSystemPipe } from './pipes/space-system.pipe';
import { ToValuePipe } from './pipes/to-value.pipe';
import { UnitsPipe } from './pipes/units.pipe';
import { ValuePipe } from './pipes/value.pipe';
import { YaPrintZone } from './print/print-zone.component';
import { PrintableDirective } from './print/printable.directive';

const pipes = [
  ActionLogFormatPipe,
  AliasPipe,
  ArrayContainsPipe,
  BasenamePipe,
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
  HexPipe,
  MayAccessAdminAreaPipe,
  MemberPathPipe,
  MillisDurationPipe,
  NanosDurationPipe,
  NvlPipe,
  OperatorPipe,
  ParameterTypeForPathPipe,
  ParentsPipe,
  PrintJsonPipe,
  ReversePipe,
  ShortNamePipe,
  SpaceSystemPipe,
  StorageUrlPipe,
  ToValuePipe,
  UnitsPipe,
  ValuePipe,
];

const directives = [
  DataTableDirective,
  PrintableDirective,
  SimpleTableDirective,
  TableDirective,
  YaHref,
];

const sharedComponents = [
  YaBinaryInput,
  YaBreadcrumb,
  YaBreadcrumbTrail,
  YaButton,
  YaColumnChooser,
  YaDateTimeInput,
  YaDetailPane,
  YaDetailToolbar,
  YaDots,
  YaDownloadButton,
  YaDownloadMenuItem,
  YaDurationInput,
  YaEmptyMessage,
  YaErrors,
  YaExpirable,
  YaFilterInput,
  YaFilterTextarea,
  YaHelp,
  YaHelpDialog,
  YaHexIntegerInput,
  YaHighlight,
  YaIconAction,
  YaInterval,
  YaLabel,
  YaLabels,
  YaLed,
  YaMessageBar,
  YaMore,
  YaMultiSelect,
  YaOption,
  YaPageButton,
  YaPageIconButton,
  YaProgress,
  YaPrintZone,
  YaSearchFilter,
  YaSearchFilter2,
  YaSelect,
  YaSidebarNavGroup,
  YaSidebarNavItem,
  YaTableToggle,
  YaTableTop,
  YaTagSelect,
  YaTextAction,
  YaTimezoneSelect,
  YaTitleCopy,
  YaToolbarActions,
  YaValue,
  YaWarningMessage,
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
    ReactiveFormsModule,
    RouterModule,
    materialModules,
    directives,
    pipes,
    sharedComponents,
  ],
  exports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    directives,
    materialModules,
    sharedComponents,
    pipes,
  ],
})
export class WebappSdkModule { }
