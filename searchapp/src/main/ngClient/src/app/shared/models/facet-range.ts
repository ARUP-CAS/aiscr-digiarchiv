import { Facet } from './facet';

export class FacetRange {
  field: string;
  
  counts: Facet[] = [];
  gap: string;
  start: string;
  end: string;
  before: number;
  after: number;
        
}
