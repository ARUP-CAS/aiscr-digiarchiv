import { Component, OnInit } from '@angular/core';

import { SolrService } from '../../../solr.service';
import { Filter, Facet, FacetField } from '../../../shared/index';

@Component({

  selector: 'app-facets',
  templateUrl: 'facets.component.html',
  styleUrls: ['facets.component.css']
})
export class FacetsComponent implements OnInit {

  treewidth: string;

  constructor(public solrService: SolrService) {}

  ngOnInit() {
  }

  togglePivot(fp){
    this.treewidth = "1px";
    if(fp.hasOwnProperty('visible')){
      fp['visible'] = !fp['visible'];
    }else{
      fp['visible'] = true;
    }

    //Chceme animovat stromecek vanocni

    setTimeout(() => {
      this.changeWidth();
    }, 1);


  }

  changeWidth(){
//    console.log(this.treewidth);
      this.treewidth = "100%";

//    console.log(this.treewidth);
  }

  clickPivot(field: string, value: string){
    let f : Facet = new Facet();
    f.field = field;
    f.value = value;
    this.clickFacet(f, false);
  }

  clickFacet(facet: Facet, isMultiple: boolean){
    if(isMultiple){
      if(facet.isUsed){
        this.solrService.removeFacetValue(facet.field, facet.value);
      }else{
        this.solrService.addFilterValue(facet.field, facet.value);
      }

    }else{
      this.solrService.addFilter(facet.field, facet.value);
    }
  }

  clickRangeFacet(field: string, v1: string, v2: string){
    let val: string = '[' + v1 + ' TO ' + v2 + ']';
    this.solrService.addFilter(field, val);
  }

  icon(value: string){
    return this.solrService.icon(value);
  }

  asDate(s: string){
    return new Date(s);

  }

  shouldTranslate(field: string){
    return this.solrService.config['facets']['translate'].hasOwnProperty(field);
  }

  getTranslatePrefix(field: string){
    if(this.shouldTranslate(field)){
      return this.solrService.config['facets']['translate'][field]['heslar'];
    } else {
      return field;
    }
  }

  getTranslated(field: string, value: string){
    if (this.shouldTranslate(field)){
      let cf = this.solrService.config['facets']['translate'][field];
      let typ = cf['type'];
      if (typ === 'heslar'){
        return this.solrService.getFromHeslar(cf['heslar'], cf['lookupField'], value).subscribe(res => {
          if(res.length > 0){
            return res[0][cf['retField']];
          }else {
            return value;
          }
        });
      } else {
        return this.solrService.translateKey(field + '.' + value);
      }
    } else {
      return value;
    }
  }

}
