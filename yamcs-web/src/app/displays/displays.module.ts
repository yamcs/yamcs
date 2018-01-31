import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { DisplaysRoutingModule, routingComponents } from './displays-routing.module';

@NgModule({
  imports: [
    SharedModule,
    DisplaysRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class DisplaysModule {
}
