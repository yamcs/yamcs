export interface Instance {

  name: string;
  state: string;

  processor: Processor[];
}

export interface Processor {
  name: string;
}
