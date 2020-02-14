import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { InstanceHomeRoutingModule, routingComponents } from './InstanceHomeRoutingModule';
import { TmStatsTable } from './TmStatsTable';

@NgModule({
  imports: [
    SharedModule,
    InstanceHomeRoutingModule,
  ],
  declarations: [
    routingComponents,
    TmStatsTable,
  ],
})
export class InstanceHomeModule {
}
