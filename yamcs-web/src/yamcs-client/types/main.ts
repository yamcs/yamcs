import { Processor } from './system';

export interface Instance {
  name: string;
  state: string;
  processor: Processor[];
}

export interface ClientInfo {
  instance: string;
  id: number;
  username: string;
  applicationName: string;
  processorName: string;
  state: 'CONNECTED' | 'DISCONNECTED';
  currentClient: boolean;
  loginTimeUTC: string;
}

export interface UserInfo {
  login: string;
  clientInfo: ClientInfo[];
  roles: string[];
  tmParaPrivileges: string[];
  tmParaSetPrivileges: string[];
  tmPacketPrivileges: string[];
  tcPrivileges: string[];
  systemPrivileges: string[];
}
