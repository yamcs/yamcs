import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { ClientsRoutingModule, routingComponents } from './clients-routing.module';

@NgModule({
  imports: [
    SharedModule,
    ClientsRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class ClientsModule {
}
