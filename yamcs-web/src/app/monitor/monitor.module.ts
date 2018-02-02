import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { MonitorRoutingModule, routingComponents } from './monitor-routing.module';

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class MonitorModule {
}
