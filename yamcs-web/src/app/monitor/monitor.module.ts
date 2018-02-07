import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { MonitorRoutingModule, routingComponents } from './monitor-routing.module';
import { EventTableComponent } from './components/event-table.component';

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    EventTableComponent,
  ]
})
export class MonitorModule {
}
