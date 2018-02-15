import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { MdbRoutingModule, routingComponents } from './MdbRoutingModule';
import { MdbPageTemplate } from './template/MdbPageTemplate';
import { MdbToolbar } from './template/MdbToolbar';
import { ParametersTable } from './parameters/ParametersTable';
import { AlgorithmsTable } from './algorithms/AlgorithmsTable';
import { ContainersTable } from './containers/ContainersTable';
import { CommandsTable } from './commands/CommandsTable';

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    routingComponents,
    AlgorithmsTable,
    CommandsTable,
    ContainersTable,
    MdbPageTemplate,
    MdbToolbar,
    ParametersTable,
  ]
})
export class MdbModule {
}
