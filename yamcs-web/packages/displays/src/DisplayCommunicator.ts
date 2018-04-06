import { NamedObjectId } from '@yamcs/client';

export interface DisplayCommunicator {

  triggerParameterAction(id: NamedObjectId): void;

  resolvePath(path: string): string;

  retrieveText(path: string): Promise<string>;

  retrieveXML(path: string): Promise<XMLDocument>;

  retrieveXMLDisplayResource(path: string): Promise<XMLDocument>;
}
