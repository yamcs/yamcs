import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { MdbRoutingModule, routingComponents } from './mdb-routing.module';
import { MdbPageTemplateComponent } from './template/mdb-page-template';
import { MdbToolbarComponent } from './template/mdb-toolbar';
import { ParametersTableComponent } from './parameters/parameters-table';
import { AlgorithmsTableComponent } from './algorithms/algorithms-table';
import { ContainersTableComponent } from './containers/containers-table';
import { CommandsTableComponent } from './commands/commands-table';

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    routingComponents,
    AlgorithmsTableComponent,
    CommandsTableComponent,
    ContainersTableComponent,
    MdbPageTemplateComponent,
    MdbToolbarComponent,
    ParametersTableComponent,
  ]
})
export class MdbModule {
}
