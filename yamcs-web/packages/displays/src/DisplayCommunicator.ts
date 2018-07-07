import { NamedObjectId } from '@yamcs/client';

export interface DisplayCommunicator {

  triggerParameterAction(id: NamedObjectId): void;

  getObjectURL(bucketName: string, objectName: string): string;

  getObject(bucketName: string, objectName: string): Promise<Response>;

  getXMLObject(bucketName: string, objectName: string): Promise<XMLDocument>;
}
