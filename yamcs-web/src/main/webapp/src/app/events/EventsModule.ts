import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateEventDialog } from './CreateEventDialog';
import { EventSeverity } from './EventSeverity';
import { EventsRoutingModule, routingComponents } from './EventsRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    EventsRoutingModule,
  ],
  declarations: [
    routingComponents,
    CreateEventDialog,
    EventSeverity,
  ],
})
export class EventsModule {
}
