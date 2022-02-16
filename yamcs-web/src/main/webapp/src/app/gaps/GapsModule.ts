import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { GapsRoutingModule, routingComponents } from './GapsRoutingModule';
import { RequestMultipleRangesPlaybackDialog } from './RequestMultipleRangesPlaybackDialog';
import { RequestSingleRangePlaybackDialog } from './RequestSingleRangePlaybackDialog';

@NgModule({
  imports: [
    SharedModule,
    GapsRoutingModule,
  ],
  declarations: [
    routingComponents,
    RequestSingleRangePlaybackDialog,
    RequestMultipleRangesPlaybackDialog,
  ],
})
export class GapsModule {
}
