import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { SchemaRoutingModule, routingComponents } from './schema-routing.module';

@NgModule({
  imports: [
    SharedModule,
    SchemaRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class SchemaModule {
}
