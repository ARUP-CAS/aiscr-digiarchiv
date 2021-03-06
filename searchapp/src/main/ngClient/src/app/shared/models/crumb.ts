import { Filter, FilterValue } from './filter';
import { Condition } from './condition';

export class Crumb {
  constructor(
    public isFilter: boolean,
    public condition: Condition,
    public filter: Filter,
    public isMultiple: boolean,
    public displayValue: string,
    public filterValue: FilterValue){
  }
}
