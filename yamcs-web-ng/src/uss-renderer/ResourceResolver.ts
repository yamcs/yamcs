export interface ResourceResolver {

  resolvePath(path: string): string;

  resolve(path: string): Promise<string>;
}
