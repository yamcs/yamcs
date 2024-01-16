import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ActivitiesRoutingModule, routingComponents } from './ActivitiesRoutingModule';
import { ActivityDuration } from './ActivityDuration';
import { ActivityStatus } from './ActivityStatus';
import { SetFailedDialog } from './SetFailedDialog';
import { StartManualActivityDialog } from './StartManualActivityDialog';

@NgModule({
  imports: [
    SharedModule,
    ActivitiesRoutingModule,
  ],
  declarations: [
    routingComponents,
    ActivityDuration,
    ActivityStatus,
    SetFailedDialog,
    StartManualActivityDialog,
  ],
})
export class ActivitiesModule {
}
