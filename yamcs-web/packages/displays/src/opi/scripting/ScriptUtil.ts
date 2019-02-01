import { OpiDisplay } from '../OpiDisplay';

export class ScriptUtil {

  constructor(private display: OpiDisplay) { }

  openOPI(widget: any, path: string, target: any, macrosInput: any) {
    this.display.navigationHandler.openDisplay({
      target: this.display.resolve(path),
      openInNewWindow: false,
    });
  }

  closeCurrentOPI() {
    this.display.navigationHandler.closeDisplay();
  }
}
