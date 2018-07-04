import { DisplaySource, NamedObjectId } from '@yamcs/client';

export interface DisplayCommunicator {

  triggerParameterAction(id: NamedObjectId): void;

  resolvePath(path: string): string;

  retrieveText(path: string): Promise<string>;

  retrieveXML(path: string): Promise<XMLDocument>;

  retrieveDisplayResource(path: string, source: DisplaySource): Promise<string>;

  retrieveXMLDisplayResource(path: string, source: DisplaySource): Promise<XMLDocument>;
}
