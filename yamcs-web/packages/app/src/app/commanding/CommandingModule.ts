import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
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
    AcknowledgmentIcon,
    CommandQueuesTable,
    QueuedCommandsTable,
    SendCommandWizardStep,
  ],
})
export class CommandingModule {
}
