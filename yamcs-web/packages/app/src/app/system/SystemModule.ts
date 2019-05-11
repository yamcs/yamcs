import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { routingComponents, SystemRoutingModule } from './SystemRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
  ],
})
export class SystemModule {
}
