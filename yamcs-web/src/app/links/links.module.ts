import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { LinksRoutingModule, routingComponents } from './links-routing.module';

@NgModule({
  imports: [
    SharedModule,
    LinksRoutingModule,
  ],
  declarations: [
    routingComponents,
  ]
})
export class LinksModule {
}
