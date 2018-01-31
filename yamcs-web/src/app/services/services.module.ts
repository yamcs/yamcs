import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { ServicesRoutingModule, routingComponents } from './services-routing.module';

@NgModule({
  imports: [
    SharedModule,
    ServicesRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class ServicesModule {
}
