export class ConsoleUtil {

  writeInfo(message: string) {
    // tslint:disable-next-line:no-console
    console.log(message);
  }

  writeError(message: string) {
    // tslint:disable-next-line:no-console
    console.error(message);
  }

  writeWarning(message: string) {
    // tslint:disable-next-line:no-console
    console.warn(message);
  }
}
