import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { FlexLayoutModule } from '@angular/flex-layout';

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

const materialModules = [
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
  DetailToolbar,
  EmptyMessage,
  SidebarNavItem,
  ParameterPlot,
  SelectInstanceDialog,
  TabDetailIcon,
  ToolbarActions,
];

const pipes = [
  OperatorPipe,
  UnitsPipe,
  ValuePipe,
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
})
export class SharedModule {
}
