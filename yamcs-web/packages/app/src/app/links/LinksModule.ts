import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { LinksRoutingModule, routingComponents } from './LinksRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    LinksRoutingModule,
  ],
  declarations: [
    routingComponents,
  ],
})
export class LinksModule {
}
