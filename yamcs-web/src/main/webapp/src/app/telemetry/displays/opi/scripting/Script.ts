import { OpiDisplay } from '../OpiDisplay';
import { ConsoleUtil } from './ConsoleUtil';
import { MessageDialog } from './MessageDialog';
import { ScriptUtil } from './ScriptUtil';

interface Context { [key: string]: any; }

const iframe = document.createElement('iframe');
iframe.style.display = 'none';
document.body.appendChild(iframe);

const contentWindow = iframe.contentWindow as any;
let wEval = contentWindow.eval;
if (!wEval && contentWindow.execScript) { // IE
  contentWindow.execScript.call(contentWindow, 'null');
  wEval = contentWindow.eval;
}

export class Script {

  private scriptText: string;
  private context: Context;

  constructor(display: OpiDisplay, scriptText: string) {
    this.scriptText = scriptText
      .replace(/importClass\([^\)]*\)\s*\;?/gi, '')
      .replace(/importPackage\([^\)]*\)\s*\;?/gi, '')
      .trim();
    this.context = {
      widget: null, // Some scripts reference this. Ensure variable exists.
      ConsoleUtil: new ConsoleUtil(),
      MessageDialog: new MessageDialog(),
      ScriptUtil: new ScriptUtil(display),
    };
  }

  run() {
    this.context = this.runWithContext(this.scriptText, this.context);
  }

  private runWithContext(script: string, context: Context) {
    // Mark current globals
    const originalGlobals = [];
    // tslint:disable-next-line:forin
    for (const k in iframe.contentWindow) {
      originalGlobals.push(k);
    }

    // Add context to iframe globals
    for (const k in context) {
      if (context.hasOwnProperty(k)) {
        contentWindow[k] = context[k];
      }
    }
    wEval.call(contentWindow, script);

    // Reset iframe while extracting updated context
    const updatedContext: Context = {};
    for (const k in contentWindow) {
      if (originalGlobals.indexOf(k) === -1) {
        updatedContext[k] = contentWindow[k];
        delete contentWindow[k];
      }
    }
    return updatedContext;
  }
}
