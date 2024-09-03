import { ParameterValue } from './monitoring';

export interface MissionDatabase {
  configName: string;
  name: string;
  version: string;
  spaceSystems: SpaceSystem[];
  parameterCount: number;
  containerCount: number;
  commandCount: number;
  algorithmCount: number;
  parameterTypeCount: number;
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

export interface SpaceSystemsPage {
  spaceSystems?: SpaceSystem[];
  continuationToken?: string;
  totalSize: number;
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
  | 'GROUND'
  | 'LOCAL'
  | 'SYSTEM'
  | 'TELEMETERED';

  type?: ParameterType;
  usedBy?: UsedByInfo;
  path?: string[];
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
  name: string;
  qualifiedName: string;
  shortDescription: string;
  longDescription: string;
  alias: NamedObjectId[];
  engType: string;
  arrayInfo?: ArrayInfo;
  dataEncoding?: DataEncoding;
  unitSet?: UnitInfo[];
  defaultAlarm: AlarmInfo;
  contextAlarm: ContextAlarmInfo[];
  enumValue: EnumValue[];
  absoluteTimeInfo: AbsoluteTimeInfo;
  member: ParameterMember[];
  signed?: boolean;
  sizeInBits?: number;
  oneStringValue?: string;
  zeroStringValue?: string;
  usedBy?: Parameter[];
}

export interface ArrayInfo {
  type: ParameterType;
  dimensions: number;
}

export interface Member {
  name: string;
  type: ParameterType | ArgumentType;
  initialValue?: string;
  shortDescription: string;
  longDescription: string;
  alias: NamedObjectId[];
}

export interface ParameterMember extends Member {
  type: ParameterType;
}

export interface ArgumentMember extends Member {
  type: ArgumentType;
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
  staticAlarmRanges: AlarmRange[];
  enumerationAlarms: EnumerationAlarm[];
}

export interface ContextAlarmInfo {
  comparison: ComparisonInfo[];
  context: string;
  alarm: AlarmInfo;
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
  contextCalibrators: ContextCalibrator[];
}

export interface Calibrator {
  type: string;
  polynomialCalibrator: PolynomialCalibrator;
  splineCalibrator: SplineCalibrator;
  javaExpressionCalibrator: JavaExpressionCalibrator;
}

export interface ContextCalibrator {
  comparison: ComparisonInfo[];
  calibrator: Calibrator;
  context: string;
}

export interface PolynomialCalibrator {
  coefficients: number[];
}

export interface SplineCalibrator {
  points: SplinePoint[];
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
  effectiveSignificance: Significance;
  constraint: TransmissionConstraint[];
  commandContainer?: CommandContainer;
  verifier?: Verifier[];
}

export type TerminationActionType = 'SUCCESS' | 'FAIL';

export interface Verifier {
  stage: string;
  container?: Container;
  algorithm?: Algorithm;
  expression?: string;
  onSuccess: TerminationActionType;
  onFail: TerminationActionType;
  onTimeout: TerminationActionType;
  checkWindow: CheckWindow;
}

export interface CheckWindow {
  timeToStartChecking?: number;
  timeToStopChecking: number;
  relativeTo: string;
}

export interface CommandContainer extends NameDescription {
  sizeInBits: number;
  baseContainer?: Container;
  entry: SequenceEntry[];
}

export interface Argument {
  name: string;
  description: string;
  initialValue?: string;
  type: ArgumentType;
}

export interface ArgumentType {
  engType: string;
  dataEncoding: DataEncoding;
  unitSet: UnitInfo[];
  enumValue: EnumValue[];
  signed?: boolean;
  rangeMin?: number;
  rangeMax?: number;
  minChars?: number;
  maxChars?: number;
  minBytes?: number;
  maxBytes?: number;
  member?: ArgumentMember[];
  zeroStringValue?: string;
  oneStringValue?: string;
  dimensions?: ArgumentDimension[];
  elementType?: ArgumentType;
}

export interface ArgumentDimension {
  fixedValue: string;
  argument: string;
  parameter: Parameter;
  slope: string;
  intercept: string;
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
  expression: string;
  timeout: number;
}

export interface EnumValue {
  value: number;
  label: string;
  description: string;
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
  scope: 'GLOBAL' | 'COMMAND_VERIFICATION' | 'CONTAINER_PROCESSING';
  type: 'CUSTOM' | 'MATH';
  language: string;
  text: string;
  inputParameter: InputParameter[];
  outputParameter: OutputParameter[];
  onParameterUpdate: Parameter[];
  onPeriodicRate: number[];
  mathElements: any[];
}

export interface AlgorithmStatus {
  active: boolean;
  traceEnabled: boolean;
  runCount: number;
  lastRun?: string;
  errorCount: number;
  execTimeNs: number;
  errorMessage?: string;
  errorTime?: string;
}

export interface AlgorithmTrace {
  runs?: AlgorithmRun[];
  logs?: AlgorithmLog[];
}

export interface AlgorithmRun {
  time: string;
  inputs?: ParameterValue[];
  outputs?: ParameterValue[];
  returnValue: string;
  error: string;
}

export interface AlgorithmLog {
  time: string;
  msg: string;
}

export interface AlgorithmOverrides {
  textOverride?: AlgorithmTextOverride;
}

export interface AlgorithmTextOverride {
  algorithm: string;
  text: string;
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
  maxInterval?: number;
  sizeInBits?: number;
  baseContainer?: Container;
  archivePartition: boolean;
  restrictionCriteriaExpression?: string;
  entry?: SequenceEntry[];
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
  indirectParameterRef?: IndirectParameterRef;
}

export interface FixedValue {
  name: string;
  hexValue: string;
  sizeInBits: number;
}

export interface IndirectParameterRef {
  parameter: Parameter;
  aliasNamespace: string;
}

export interface RepeatInfo {
  fixedCount: number;
  dynamicCount: Parameter;
  bitsBetween: number;
}

export interface GetParametersOptions {
  type?: string;
  source?: string;
  q?: string;
  system?: string;
  searchMembers?: boolean;
  details?: boolean;
  pos?: number;
  limit?: number;
  next?: string;
}

export interface GetParameterTypesOptions {
  q?: string;
  system?: string;
  pos?: number;
  limit?: number;
  next?: string;
  fields?: Array<keyof ParameterType>;
}

export interface ParametersPage {
  systems?: SpaceSystem[];
  parameters?: Parameter[];
  continuationToken?: string;
  totalSize: number;
}

export interface ParameterTypesPage {
  systems?: SpaceSystem[];
  parameterTypes?: ParameterType[];
  continuationToken?: string;
  totalSize: number;
}

export interface GetAlgorithmsOptions {
  scope?: string;
  q?: string;
  system?: string;
  pos?: number;
  limit?: number;
}

export interface AlgorithmsPage {
  systems?: SpaceSystem[];
  algorithms?: Algorithm[];
  continuationToken?: string;
  totalSize: number;
}

export interface GetContainersOptions {
  q?: string;
  system?: string;
  pos?: number;
  limit?: number;
}

export interface ContainersPage {
  systems?: SpaceSystem[];
  containers?: Container[];
  continuationToken?: string;
  totalSize: number;
}

export interface GetCommandsOptions {
  noAbstract?: boolean;
  q?: string;
  system?: string;
  pos?: number;
  limit?: number;
  details?: boolean;
  fields?: Array<keyof Command>;
}

export interface CommandsPage {
  systems?: SpaceSystem[];
  commands?: Command[];
  continuationToken?: string;
  totalSize: number;
}
