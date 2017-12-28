import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { MdbRoutingModule, routingComponents } from './mdb-routing.module';

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class MdbModule {
}
