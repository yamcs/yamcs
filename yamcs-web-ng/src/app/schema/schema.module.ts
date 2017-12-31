import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { SchemaRoutingModule, routingComponents } from './schema-routing.module';

import { PrettySqlPipe } from './pipes/prettysql.pipe';

const pipes = [
  PrettySqlPipe,
];


@NgModule({
  imports: [
    SharedModule,
    SchemaRoutingModule,
  ],
  declarations: [
    routingComponents,
    pipes,
  ]
})
export class SchemaModule {
}
