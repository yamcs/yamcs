import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { SystemRoutingModule, routingComponents } from './system-routing.module';

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class SystemModule {
}
