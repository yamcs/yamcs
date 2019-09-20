import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CommandCompletion } from './command-history/CommandCompletion';
import { CommandQueuesTable } from './command-queues/CommandQueuesTable';
import { QueuedCommandsTable } from './command-queues/QueuedCommandsTable';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    CommandCompletion,
    CommandQueuesTable,
    QueuedCommandsTable,
    SendCommandWizardStep,
  ],
})
export class CommandingModule {
}
