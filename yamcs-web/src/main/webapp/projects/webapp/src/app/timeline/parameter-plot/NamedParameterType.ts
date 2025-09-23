import { ParameterType } from '@yamcs/webapp-sdk';

// Added as a quick solution to support aggregate or array members into plots.
// Perhaps a better approach is to really make use of named parameter types coming
// from the server.

// Remark that a "Parameter" is a kind-of "NamedParameterType"

export interface NamedParameterType {
  qualifiedName: string;
  type?: ParameterType;
}
