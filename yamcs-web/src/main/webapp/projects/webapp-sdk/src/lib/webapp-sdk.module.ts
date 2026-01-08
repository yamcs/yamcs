import { DragDropModule } from '@angular/cdk/drag-drop';
import { OverlayModule } from '@angular/cdk/overlay';
import { CdkTableModule } from '@angular/cdk/table';
import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterModule } from '@angular/router';
import { YaPrintZoneHide } from '../lib/print/print-zone-hide.directive';
import { YaPrintZoneShow } from '../lib/print/print-zone-show.directive';
import { YaActionLogSummary } from './components/action-log-summary/action-log-summary.component';
import { YaAlarmLevel } from './components/alarm-level/alarm-level.component';
import { YaAttrDivider } from './components/attr-list/attr-divider.component';
import { YaAttrLabel } from './components/attr-list/attr-label.directive';
import { YaAttrList } from './components/attr-list/attr-list.component';
import { YaAttr } from './components/attr-list/attr.component';
import { YaBinaryInput } from './components/binary-input/binary-input.component';
import { YaBreadcrumbTrail } from './components/breadcrumb/breadcrumb-trail.component';
import { YaBreadcrumb } from './components/breadcrumb/breadcrumb.component';
import { YaButtonGroup } from './components/button-group/button-group.component';
import { YaButton } from './components/button/button.component';
import { YaColorInput } from './components/color-input/color-input.component';
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
import { YaFieldDivider } from './components/field-divider/field-divider.component';
import { YaFieldLabel } from './components/field/field-label.directive';
import { YaField } from './components/field/field.component';
import { YaFilterBar } from './components/filter-bar/filter-bar.component';
import { YaFilterInput } from './components/filter/filter-input.component';
import { YaFilterTextarea } from './components/filter/filter-textarea.component';
import { YaFormContent } from './components/form-content/form-content.component';
import { YaForm } from './components/form/form.component';
import { YaHelp } from './components/help/help.component';
import { YaHelpDialog } from './components/help/help.dialog';
import { YaHexIntegerInput } from './components/hex-integer-input/hex-integer-input.component';
import { YaHighlight } from './components/highlight/highlight.component';
import { YaHref } from './components/href/href.directive';
import { YaIconAction } from './components/icon-action/icon-action.component';
import { YaIconButton } from './components/icon-button/icon-button.component';
import { YaInlineSelect } from './components/inline-select/inline-select.component';
import { YaInstancePage } from './components/instance-page/instance-page.component';
import { YaInstanceToolbarLabel } from './components/instance-toolbar/instance-toolbar-label.directive';
import { YaInstanceToolbar } from './components/instance-toolbar/instance-toolbar.component';
import { YaInterval } from './components/interval/interval.component';
import { YaLabel } from './components/label/label.component';
import { YaLabels } from './components/labels/labels.component';
import { YaLed } from './components/led/led.component';
import { YaMessageBar } from './components/message-bar/message-bar.component';
import { YaMeta } from './components/meta/meta.component';
import { YaMore } from './components/more/more.component';
import { YaMultiSelect } from './components/multi-select/multi-select.component';
import { YaOption } from './components/option/option.component';
import { YaPageButton } from './components/page-button/page-button.component';
import { YaPageIconButton } from './components/page-icon-button/page-icon-button.component';
import { YaPageTabs } from './components/page-tabs/page-tabs.component';
import { YaPanel } from './components/panel/panel.component';
import { YaParameterStatus } from './components/parameter-status/parameter-status.component';
import { YaProgress } from './components/progress/progress.component';
import { YaSearchFilter } from './components/search-filter/search-filter.component';
import { YaSearchFilter2 } from './components/search-filter2/search-filter2.component';
import { YaSelect } from './components/select/select.component';
import { YaSidebarNavGroup } from './components/sidebar/sidebar-nav-group.component';
import { YaSidebarNavItem } from './components/sidebar/sidebar-nav-item.component';
import { YaSlideToggle } from './components/slide-toggle/slide-toggle.component';
import { YaSliderInput } from './components/slider-input/slider-input.component';
import { YaStepperStepActions } from './components/stepper/stepper-step-actions.component';
import { YaStepperStep } from './components/stepper/stepper-step.component';
import { YaStepper } from './components/stepper/stepper.component';
import { YaTableCheckbox } from './components/table-checkbox/table-checkbox.component';
import { YaTableToggle } from './components/table-toggle/table-toggle.component';
import { YaTableTop } from './components/table-top/table-top.component';
import { YaTableWindow } from './components/table-window/table-window.component';
import { YaTagSelect } from './components/tag-select/tag-select.component';
import { YaTextAction } from './components/text-action/text-action.component';
import { YaTimezoneSelect } from './components/timezone-select/timezone-select.component';
import { YaTitleCopy } from './components/title-copy/title-copy.component';
import { YaToolbar } from './components/toolbar/toolbar.component';
import { YaValue } from './components/value/value.component';
import { YaVerticalDivider } from './components/vertical-divider/vertical-divider.component';
import { YaWarningMessage } from './components/warning-message/warning-message.component';
import { DataTableDirective } from './directives/data-table.directive';
import { AliasPipe } from './pipes/alias.pipe';
import { AllConstraintsPipe } from './pipes/all-constraints.pipe';
import { ArrayContainsPipe } from './pipes/array-contains.pipe';
import { BasenamePipe } from './pipes/basename.pipe';
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
import { ParameterTypeForPathPipe } from './pipes/parameter-type-for-path.pipe';
import { ParentsPipe } from './pipes/parents.pipe';
import { PrintJsonPipe } from './pipes/print-json.pipe';
import { PrintObjPipe } from './pipes/print-obj.pipe';
import { RelativizePipe } from './pipes/relativize.pipe';
import { ReversePipe } from './pipes/reverse.pipe';
import { ShortNamePipe } from './pipes/short-name.pipe';
import { SpaceSystemPipe } from './pipes/space-system.pipe';
import { StorageUrlPipe } from './pipes/storage-url.pipe';
import { ToValuePipe } from './pipes/to-value.pipe';
import { UnitsPipe } from './pipes/units.pipe';
import { ValuePipe } from './pipes/value.pipe';
import { YaPrintZone } from './print/print-zone.component';
import { PrintableDirective } from './print/printable.directive';

const pipes = [
  AliasPipe,
  AllConstraintsPipe,
  ArrayContainsPipe,
  BasenamePipe,
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
  ParameterTypeForPathPipe,
  ParentsPipe,
  PrintJsonPipe,
  PrintObjPipe,
  RelativizePipe,
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
  YaAttrLabel,
  YaFieldLabel,
  YaHref,
  YaPrintZoneHide,
  YaPrintZoneShow,
];

const sharedComponents = [
  YaActionLogSummary,
  YaAlarmLevel,
  YaAttr,
  YaAttrDivider,
  YaAttrList,
  YaBinaryInput,
  YaBreadcrumb,
  YaBreadcrumbTrail,
  YaButton,
  YaButtonGroup,
  YaColorInput,
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
  YaField,
  YaFieldDivider,
  YaFilterBar,
  YaFilterInput,
  YaFilterTextarea,
  YaForm,
  YaFormContent,
  YaHelp,
  YaHelpDialog,
  YaHexIntegerInput,
  YaHighlight,
  YaIconAction,
  YaIconButton,
  YaInlineSelect,
  YaInterval,
  YaInstancePage,
  YaInstanceToolbar,
  YaInstanceToolbarLabel,
  YaLabel,
  YaLabels,
  YaLed,
  YaMessageBar,
  YaMeta,
  YaMore,
  YaMultiSelect,
  YaOption,
  YaPageButton,
  YaPageIconButton,
  YaPageTabs,
  YaPanel,
  YaParameterStatus,
  YaProgress,
  YaPrintZone,
  YaSearchFilter,
  YaSearchFilter2,
  YaSelect,
  YaSidebarNavGroup,
  YaSidebarNavItem,
  YaSliderInput,
  YaSlideToggle,
  YaStepper,
  YaStepperStep,
  YaStepperStepActions,
  YaTableCheckbox,
  YaTableToggle,
  YaTableTop,
  YaTableWindow,
  YaTagSelect,
  YaTextAction,
  YaTimezoneSelect,
  YaTitleCopy,
  YaToolbar,
  YaValue,
  YaVerticalDivider,
  YaWarningMessage,
];

const materialModules = [
  OverlayModule,
  CdkTableModule,
  DragDropModule,
  MatAutocompleteModule,
  MatButtonModule,
  MatDatepickerModule,
  MatDialogModule,
  MatIconModule,
  MatListModule,
  MatMenuModule,
  MatNativeDateModule,
  MatPaginatorModule,
  MatSidenavModule,
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
export class WebappSdkModule {}
