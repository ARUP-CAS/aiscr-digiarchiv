import { Facet } from './facet'
export class FacetField {
  field: string;
  isMultiple: boolean;
  values: Facet[] = [];
}
