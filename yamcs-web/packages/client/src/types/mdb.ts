export interface MissionDatabase {
  configName: string;
  name: string;
  version: string;
  spaceSystem: SpaceSystem[];
}

export interface NameDescription {
  name: string;
  qualifiedName: string;
  alias?: NamedObjectId[];
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
  | 'EXTERNAL1'
  | 'EXTERNAL2'
  | 'EXTERNAL3'
  | 'LOCAL'
  | 'SYSTEM'
  | 'TELEMETERED';

  type?: ParameterType;
  usedBy?: UsedByInfo;
}

export interface UsedByInfo {
  algorithm?: Algorithm[];
  container?: Container[];
}

export interface UnitInfo {
  unit: string;
}

export interface NamedObjectId {
  namespace?: string;
  name: string;
}

export interface ParameterType {
  engType: string;
  dataEncoding?: DataEncoding;
  unitSet?: UnitInfo[];
  defaultAlarm: AlarmInfo;
  enumValue: EnumValue[];
  absoluteTimeInfo: AbsoluteTimeInfo;
}

export interface AbsoluteTimeInfo {
  initialValue: string;
  scale: number;
  offset: number;
  offsetFrom: Parameter;
  epoch: string;
}

export interface AlarmInfo {
  minViolations: number;
  staticAlarmRange: AlarmRange[];
  enumerationAlarm: EnumerationAlarm[];
}

export interface EnumerationAlarm {
  level: AlarmLevelType;
  label: string;
}

export interface DataEncoding {
  type: string;
  littleEndian: boolean;
  sizeInBits: number;
  encoding: string;
  defaultCalibrator: Calibrator;
  contextCalibrator: Calibrator[];
}

export interface Calibrator {
  type: string;
  polynomialCalibrator: PolynomialCalibrator;
  splineCalibrator: SplineCalibrator;
  javaExpressionCalibrator: JavaExpressionCalibrator;
}

export interface PolynomialCalibrator {
  coefficient: number[];
}

export interface SplineCalibrator {
  point: SplinePoint[];
}

export interface SplinePoint {
  raw: number;
  calibrated: number;
}

export interface JavaExpressionCalibrator {
  formula: string;
}

export interface Command extends NameDescription {
  baseCommand?: Command;
  abstract: boolean;
  argument: Argument[];
  argumentAssignment: ArgumentAssignment[];
  significance: Significance;
  constraint: TransmissionConstraint[];
  commandContainer: CommandContainer;
}

export interface CommandContainer extends NameDescription {
  sizeInBits: number;
  baseContainer?: Container;
  entry: SequenceEntry[];
}

export interface Argument {
  name: string;
  description: string;
  initialValue: string;
  type: ArgumentType;
}

export interface ArgumentType {
  engType: string;
  dataEncoding: DataEncoding;
  unitSet: UnitInfo[];
  enumValue: EnumValue[];
  rangeMin: number;
  rangeMax: number;
}

export interface ArgumentAssignment {
  name: string;
  value: string;
}

export interface Significance {
  consequenceLevel: 'NONE' | 'WATCH' | 'WARNING' | 'DISTRESS' | 'CRITICAL' | 'SEVERE';
  reasonForWarning: string;
}

export interface TransmissionConstraint {
  comparison: ComparisonInfo[];
  timeout: number;
}

export interface EnumValue {
  value: number;
  label: string;
}

export type AlarmLevelType = 'NORMAL' | 'WATCH' | 'WARNING' | 'DISTRESS' | 'CRITICAL' | 'SEVERE';

export interface AlarmRange {
  level: AlarmLevelType;
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
  outputParameter: OutputParameter[];
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
  repeat: RepeatInfo;

  container?: Container;
  parameter?: Parameter;
  argument?: Argument;
  fixedValue?: FixedValue;
}

export interface FixedValue {
  name: string;
  hexValue: string;
  sizeInBits: number;
}

export interface RepeatInfo {
  fixedCount: number;
  dynamicCount: Parameter;
  bitsBetween: number;
}

export interface GetParametersOptions {
  namespace?: string;
  recurse?: boolean;
  type?: string;
  q?: string;
  pos?: number;
  limit?: number;
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
