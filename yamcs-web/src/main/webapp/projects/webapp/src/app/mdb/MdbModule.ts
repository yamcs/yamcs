import { NgModule } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { SharedModule } from '../shared/SharedModule';
import { MdbRoutingModule, routingComponents } from './MdbRoutingModule';
import { AlgorithmDetail } from './algorithms/AlgorithmDetail';
import { ArgumentEnumDialog } from './commands/ArgumentEnumDialog';
import { CommandDetail } from './commands/CommandDetail';
import { ContainerDetail } from './containers/ContainerDetail';
import { ParameterTypeDetail } from './parameterTypes/ParameterTypeDetail';
import { ParameterCalibration } from './parameters/ParameterCalibration';
import { ParameterDetail } from './parameters/ParameterDetail';
import { PolynomialPipe } from './pipes/PolynomialPipe';

const pipes = [
  PolynomialPipe,
];

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
    WebappSdkModule,
  ],
  declarations: [
    routingComponents,
    pipes,
    AlgorithmDetail,
    ArgumentEnumDialog,
    CommandDetail,
    ContainerDetail,
    ParameterCalibration,
    ParameterDetail,
    ParameterTypeDetail,
  ],
})
export class MdbModule {
}
