import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { FlexLayoutModule } from '@angular/flex-layout';

import { OverlayModule } from '@angular/cdk/overlay';
import { CdkTableModule } from '@angular/cdk/table';
import {
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
  MatProgressBarModule,
  MatProgressSpinnerModule,
  MatRadioModule,
  MatSelectModule,
  MatSidenavModule,
  MatSliderModule,
  MatSlideToggleModule,
  MatSortModule,
  MatTableModule,
  MatTabsModule,
  MatToolbarModule,
  MatTooltipModule,
} from '@angular/material';

import { YaSimpleTableComponent } from './template/SimpleTableDirective';
import { YaTableComponent } from './template/TableDirective';
import { ValuePipe } from './pipes/ValuePipe';
import { ToolbarActions } from './template/ToolbarActions';
import { SidebarNavItem } from './template/SidebarNavItem';
import { DetailToolbar } from './template/DetailToolbar';
import { YaDataTableComponent } from './template/DataTableDirective';
import { UnitsPipe } from './pipes/UnitsPipe';
import { OperatorPipe } from './pipes/OperatorPipe';
import { EmptyMessage } from './template/EmptyMessage';
import { TabDetailIcon } from './template/TabDetailIcon';
import { ParameterPlot } from './widgets/ParameterPlot';
import { SelectInstanceDialog } from './template/SelectInstanceDialog';
import { ParameterSeries } from './widgets/ParameterSeries';
import { DateTimePipe } from './pipes/DateTimePipe';
import { Expirable } from './template/Expirable';
import { AlarmLevel } from './template/AlarmLevel';
import { Dots } from './template/Dots';
import { ActionLink } from './template/ActionLink';
import { TextAction } from './template/TextAction';
import { ColumnChooser } from './template/ColumnChooser';
import { FormatBytesPipe } from './pipes/FormatBytesPipe';
import { Select } from './template/Select';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { MayReadTablesGuard } from '../core/guards/MayReadTablesGuard';
import { MayControlServicesGuard } from '../core/guards/MayControlServicesGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';
import { UnselectInstanceGuard } from '../core/guards/UnselectInstanceGuard';

const materialModules = [
  OverlayModule,
  CdkTableModule,
  FlexLayoutModule,
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
  MatProgressBarModule,
  MatProgressSpinnerModule,
  MatRadioModule,
  MatSelectModule,
  MatSidenavModule,
  MatSliderModule,
  MatSlideToggleModule,
  MatSortModule,
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
  AlarmLevel,
  ColumnChooser,
  DetailToolbar,
  Dots,
  EmptyMessage,
  Expirable,
  SidebarNavItem,
  ParameterPlot,
  SelectInstanceDialog,
  ParameterSeries,
  Select,
  TabDetailIcon,
  TextAction,
  ToolbarActions,
];

const pipes = [
  DateTimePipe,
  FormatBytesPipe,
  OperatorPipe,
  UnitsPipe,
  ValuePipe,
];

const guards = [
  AuthGuard,
  InstanceExistsGuard,
  MayControlServicesGuard,
  MayGetMissionDatabaseGuard,
  MayReadEventsGuard,
  MayReadTablesGuard,
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
    SelectInstanceDialog,
  ],
  providers: [
    guards,
    pipes, // Make pipes available in components too
  ]
})
export class SharedModule {
}
