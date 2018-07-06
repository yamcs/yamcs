import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AlgorithmDetail } from './algorithms/AlgorithmDetail';
import { AlgorithmsTable } from './algorithms/AlgorithmsTable';
import { ArgumentEnumDialog } from './commands/ArgumentEnumDialog';
import { CommandDetail } from './commands/CommandDetail';
import { CommandsTable } from './commands/CommandsTable';
import { IssueCommandDialog } from './commands/IssueCommandDialog';
import { ContainerDetail } from './containers/ContainerDetail';
import { ContainersTable } from './containers/ContainersTable';
import { MdbRoutingModule, routingComponents } from './MdbRoutingModule';
import { ColorPalette } from './parameters/ColorPalette';
import { CompareParameterDialog } from './parameters/CompareParameterDialog';
import { ModifyParameterDialog } from './parameters/ModifyParameterDialog';
import { ParameterCalibration } from './parameters/ParameterCalibration';
import { ParameterDetail } from './parameters/ParameterDetail';
import { ParametersTable } from './parameters/ParametersTable';
import { ParameterValuesTable } from './parameters/ParameterValuesTable';
import { SelectParameterDialog } from './parameters/SelectParameterDialog';
import { SelectRangeDialog } from './parameters/SelectRangeDialog';
import { SetParameterDialog } from './parameters/SetParameterDialog';
import { SeverityMeter } from './parameters/SeverityMeter';
import { Thickness } from './parameters/Thickness';
import { PolynomialPipe } from './pipes/PolynomialPipe';
import { MdbPageTemplate } from './template/MdbPageTemplate';
import { MdbToolbar } from './template/MdbToolbar';

const pipes = [
  PolynomialPipe,
];

const dialogComponents = [
  ArgumentEnumDialog,
  CompareParameterDialog,
  IssueCommandDialog,
  ModifyParameterDialog,
  SelectParameterDialog,
  SelectRangeDialog,
  SetParameterDialog,
];

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    dialogComponents,
    pipes,
    routingComponents,
    AlgorithmsTable,
    AlgorithmDetail,
    ColorPalette,
    CommandsTable,
    CommandDetail,
    ContainersTable,
    ContainerDetail,
    MdbPageTemplate,
    MdbToolbar,
    ParametersTable,
    ParameterCalibration,
    ParameterDetail,
    ParameterValuesTable,
    SeverityMeter,
    Thickness,
  ],
  entryComponents: [
    dialogComponents,
  ],
})
export class MdbModule {
}
