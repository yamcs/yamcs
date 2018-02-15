export interface MissionDatabase {
  configName: string;
  name: string;
  version: string;
  spaceSystem: SpaceSystem[];
}

export interface NameDescription {
  name: string;
  qualifiedName: string;
  alias?: Alias[];
  shortDescription?: string;
  longDescription?: string;
}

export interface SpaceSystem extends NameDescription {
  version: string;
  history?: HistoryInfo[];
  sub: SpaceSystem[];
}

export interface HistoryInfo {
  version: string;
  date: string;
  message: string;
  author: string;
}

export interface Parameter extends NameDescription {
  dataSource: 'COMMAND'
  | 'COMMAND_HISTORY'
  | 'CONSTANT'
  | 'DERIVED'
  | 'LOCAL'
  | 'SYSTEM'
  | 'TELEMETERED';

  unitSet?: UnitInfo[];
  type?: ParameterType;
}

export interface UnitInfo {
  unit: string;
}

export interface Alias {
  namespace: string;
  name: string;
}

export interface ParameterType {
  engType: string;
  dataEncoding: DataEncoding;
}

export interface DataEncoding {
  type: string;
  littleEndian: boolean;
  sizeInBits: number;
  encoding: string;
}

export interface Command extends NameDescription {
  baseCommand?: Command;
  abstract: boolean;
}

export interface AlarmRange {
  level: 'NORMAL' | 'WATCH' | 'WARNING' | 'DISTRESS' | 'CRITICAL' | 'SEVERE';
  minInclusive: number;
  maxInclusive: number;
  minExclusive: number;
  maxExclusive: number;
}

export interface Algorithm extends NameDescription {
  scope: 'GLOBAL' | 'COMMAND_VERIFICATION';
  language: string;
  text: string;
  inputParameter: InputParameter[];
  OutputParameter: OutputParameter[];
  onParameterUpdate: Parameter[];
  onPeriodicRate: number[];
}

export interface InputParameter {
  parameter: Parameter;
  inputName: string;
  parameterInstance: number;
  mandatory: boolean;
}

export interface OutputParameter {
  parameter: Parameter;
  outputName: string;
}

export interface Container extends NameDescription {
  maxInterval: number;
  sizeInBits: number;
  baseContainer: Container;
  restrictionCriteria: ComparisonInfo[];
  entry: SequenceEntry[];
}

export type OperatorType = 'EQUAL_TO'
| 'NOT_EQUAL_TO'
| 'GREATER_THAN'
| 'GREATER_THAN_OR_EQUAL_TO'
| 'SMALLER_THAN'
| 'SMALLER_THAN_OR_EQUAL_TO';

export interface ComparisonInfo {
  parameter: Parameter;
  operator: OperatorType;
  value: string;
}

export interface SequenceEntry {
  locationInBits: number;
  referenceLocation: 'CONTAINER_START' | 'PREVIOUS_ENTRY';
  container: Container;
  parameter: Parameter;
  repeat: RepeatInfo;
}

export class RepeatInfo {
  fixedCount: number;
  dynamicCount: Parameter;
  bitsBetween: number;
}

export interface GetParametersOptions {
  namespace?: string;
  recurse?: boolean;
  type?: string;
  q?: string;
}

export interface GetAlgorithmsOptions {
  namespace?: string;
  recurse?: boolean;
  q?: string;
}

export interface GetContainersOptions {
  namespace?: string;
  recurse?: boolean;
  q?: string;
}

export interface GetCommandsOptions {
  namespace?: string;
  recurse?: boolean;
  q?: string;
}
