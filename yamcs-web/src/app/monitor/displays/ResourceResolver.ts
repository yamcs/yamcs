export interface ResourceResolver {

  resolvePath(path: string): string;

  retrieveText(path: string): Promise<string>;

  retrieveXML(path: string): Promise<XMLDocument>;

  retrieveXMLDisplayResource(path: string): Promise<XMLDocument>;
}
