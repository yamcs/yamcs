import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CommandQueuesTable } from './command-queues/CommandQueuesTable';
import { QueuedCommandsTable } from './command-queues/QueuedCommandsTable';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    CommandQueuesTable,
    QueuedCommandsTable,
  ],
})
export class CommandingModule {
}
