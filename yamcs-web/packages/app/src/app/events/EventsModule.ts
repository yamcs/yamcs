import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateEventDialog } from './CreateEventDialog';
import { EventSeverity } from './EventSeverity';
import { EventsRoutingModule, routingComponents } from './EventsRoutingModule';

const dialogComponents = [
  CreateEventDialog,
];

@NgModule({
  imports: [
    SharedModule,
    EventsRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    EventSeverity,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class EventsModule {
}
