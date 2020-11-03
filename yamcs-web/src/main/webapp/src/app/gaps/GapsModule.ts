import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { GapsRoutingModule, routingComponents } from './GapsRoutingModule';
import { RequestPlaybackDialog } from './RequestPlaybackDialog';

@NgModule({
  imports: [
    SharedModule,
    GapsRoutingModule,
  ],
  declarations: [
    routingComponents,
    RequestPlaybackDialog,
  ],
})
export class GapsModule {
}
