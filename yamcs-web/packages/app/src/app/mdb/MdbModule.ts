import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { MdbRoutingModule, routingComponents } from './MdbRoutingModule';
import { MdbPageTemplate } from './template/MdbPageTemplate';
import { MdbToolbar } from './template/MdbToolbar';
import { ParametersTable } from './parameters/ParametersTable';
import { AlgorithmsTable } from './algorithms/AlgorithmsTable';
import { ContainersTable } from './containers/ContainersTable';
import { CommandsTable } from './commands/CommandsTable';
import { AlgorithmDetail } from './algorithms/AlgorithmDetail';
import { ParameterDetail } from './parameters/ParameterDetail';
import { ContainerDetail } from './containers/ContainerDetail';
import { CommandDetail } from './commands/CommandDetail';
import { SeverityMeter } from './parameters/SeverityMeter';
import { ParameterValuesTable } from './parameters/ParameterValuesTable';
import { PolynomialPipe } from './pipes/PolynomialPipe';

const pipes = [
  PolynomialPipe,
];

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    pipes,
    routingComponents,
    AlgorithmsTable,
    AlgorithmDetail,
    CommandsTable,
    CommandDetail,
    ContainersTable,
    ContainerDetail,
    MdbPageTemplate,
    MdbToolbar,
    ParametersTable,
    ParameterDetail,
    ParameterValuesTable,
    SeverityMeter,
  ]
})
export class MdbModule {
}
