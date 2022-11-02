import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateEventDialog } from './CreateEventDialog';
import { EventMessage } from './EventMessage';
import { EventSeverity } from './EventSeverity';
import { EventsRoutingModule, routingComponents } from './EventsRoutingModule';
import { ExportEventsDialog } from './ExportEventsDialog';

@NgModule({
  imports: [
    SharedModule,
    EventsRoutingModule,
  ],
  declarations: [
    routingComponents,
    CreateEventDialog,
    EventMessage,
    EventSeverity,
    ExportEventsDialog,
  ],
})
export class EventsModule {
}
