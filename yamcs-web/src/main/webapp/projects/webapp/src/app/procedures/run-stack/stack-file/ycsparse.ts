import { AdvancementParams, CheckStep, CommandOption, CommandStep, Step, Value } from '@yamcs/webapp-sdk';

export function parseYCS(json: string, commandOptions: CommandOption[]) {
  const steps: Step[] = [];

  const stack: { [key: string]: any; } = JSON.parse(json);
  if (stack.steps) { // v2
    for (const step of stack.steps) {
      if (step.type === 'check') {
        steps.push(parseCheckEntry(step));
      } else if (step.type === 'command') {
        steps.push(parseCommandEntry(step, commandOptions));
      } else {
        console.warn(`Unexpected entry of type '${step.type}'`);
      }
    }
  } else if (stack.commands) { // v1
    for (const command of stack.commands) {
      steps.push(parseCommandEntry(command, commandOptions));
    }
  }

  const advancement: AdvancementParams = {
    acknowledgment: stack.advancement?.acknowledgment || 'Acknowledge_Queued',
    wait: stack.advancement?.wait != null ? stack.advancement?.wait : 0
  };

  return [steps, advancement] as const;
}

function parseCommandEntry(entry: { [key: string]: any; }, commandOptions: CommandOption[]) {
  const result: CommandStep = {
    type: 'command',
    name: entry.name,
    namespace: entry.namespace,
    ...(entry.comment && { comment: entry.comment }),
    ...(entry.arguments && { args: parseArguments(entry.arguments) }),
    ...(entry.extraOptions && { extra: parseExtraOptions(entry.extraOptions, commandOptions) }),
    ...(entry.advancement && { advancement: entry.advancement })
  };
  return result;
}

function parseCheckEntry(entry: { [key: string]: any; }) {
  // No transformations
  return entry as CheckStep;
}

function parseArguments(commandArguments: Array<any>) {
  const args: { [key: string]: any; } = {};
  for (let argument of commandArguments) {
    args[argument.name] = argument.value;
  }
  return args;
}

function parseExtraOptions(options: Array<any>, commandOptions: CommandOption[]) {
  const extra: { [key: string]: Value; } = {};
  for (let option of options) {
    const stringValue = new String(option.value).toString();
    const value = convertOptionStringToValue(option.id, stringValue, commandOptions);
    if (value) {
      extra[option.id] = value;
    }
  }
  return extra;
}

function convertOptionStringToValue(id: string, value: string, commandOptions: CommandOption[]): Value | null {
  for (const option of commandOptions) {
    if (option.id === id) {
      switch (option.type) {
        case 'BOOLEAN':
          return { type: 'BOOLEAN', booleanValue: value === 'true' };
        case 'NUMBER':
          return { type: 'SINT32', sint32Value: Number(value) };
        default:
          return { type: 'STRING', stringValue: value };
      }
    }
  }
  return null;
}
