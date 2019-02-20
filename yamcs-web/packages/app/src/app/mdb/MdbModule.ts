import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AlgorithmDetail } from './algorithms/AlgorithmDetail';
import { ArgumentEnumDialog } from './commands/ArgumentEnumDialog';
import { CommandDetail } from './commands/CommandDetail';
import { IssueCommandDialog } from './commands/IssueCommandDialog';
import { ContainerDetail } from './containers/ContainerDetail';
import { MdbRoutingModule, routingComponents } from './MdbRoutingModule';
import { ColorPalette } from './parameters/ColorPalette';
import { CompareParameterDialog } from './parameters/CompareParameterDialog';
import { ModifyParameterDialog } from './parameters/ModifyParameterDialog';
import { ParameterCalibration } from './parameters/ParameterCalibration';
import { ParameterDetail } from './parameters/ParameterDetail';
import { ParameterValuesTable } from './parameters/ParameterValuesTable';
import { SelectRangeDialog } from './parameters/SelectRangeDialog';
import { SetParameterDialog } from './parameters/SetParameterDialog';
import { SeverityMeter } from './parameters/SeverityMeter';
import { Thickness } from './parameters/Thickness';
import { PolynomialPipe } from './pipes/PolynomialPipe';

const pipes = [
  PolynomialPipe,
];

const dialogComponents = [
  ArgumentEnumDialog,
  CompareParameterDialog,
  IssueCommandDialog,
  ModifyParameterDialog,
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
    AlgorithmDetail,
    ColorPalette,
    CommandDetail,
    ContainerDetail,
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
