import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';

import { SolrService } from '../../solr.service';
import { Condition } from '../../shared/index';
import { MapaComponent } from '../+results/mapa';

declare var jQuery: any;

@Component({

  selector: 'app-search-form',
  templateUrl: 'search-form.component.html',
  styleUrls: ['search-form.component.css']
})
export class SearchFormComponent implements OnInit, OnDestroy {

  @ViewChild('mapaViewer') mapaViewer: MapaComponent;


  conditions: Condition[] = [];
  isMapaCollapsed: boolean;
	isAdvancedCollapsed: boolean = true; // pedro
  collSubs: Subscription;
  changeMapSubs: Subscription;

  constructor(
    public solrService: SolrService) {
  }

  ngOnInit() {
    setTimeout(() => {
      this.isMapaCollapsed = !this.solrService.mapOpen;
    }, 1000);

    this.changeMapSubs = this.solrService.mapOpenChanged.subscribe(val=> {
      this.isMapaCollapsed = !this.solrService.mapOpen;
    });

    this.collSubs = this.solrService.routeChanged.subscribe(val=> {
      this.isMapaCollapsed = !this.solrService.mapOpen;
      if(val === 'home'){
        this.isAdvancedCollapsed = true;
        jQuery('#q').focus();
      }
      //console.log(val);
    });
  }
  
  ngOnDestroy(){
    this.changeMapSubs.unsubscribe();
    this.collSubs.unsubscribe();
  }

  isIndexing() {
    return this.solrService.config ? this.solrService.config['indexing'] : false;
  }

  openAdvanced() {
    setTimeout(() => {
      this.isAdvancedCollapsed = !this.isAdvancedCollapsed; // pedro
    }, 100);
		this.conditions = [];
    if (this.solrService.conditions.length === 0) {
      this.conditions.push(new Condition());
    } else {
      this.conditions = this.solrService.conditions;
    }
  }

  search() {
    this.solrService.setQ();
  }

  doAdvSearch() {
    this.solrService.setAdvSearch(this.conditions);
  }

  addCondition() {
    this.conditions.push(new Condition());
  }

  removeCondition(idx: number) {
    this.conditions.splice(idx, 1);
    this.solrService.clearTooltips();
  }

  setOperator(condition: Condition, op: string) {
    condition.operator = op.toUpperCase();
  }

  setField(condition: Condition, f: any) {
    this.solrService.setConditionField(condition, f);
    
  }

  setFieldCond(condition: Condition, c: string) {
    condition.fieldCondition = c;
  }

  setFieldValue(condition: Condition, f: any) {
      let heslarValue = 'caption';
      let dispField = 'caption';
      
    if (f.hasOwnProperty('heslarDisplay')) {
        heslarValue = f['heslarDisplay'];
        dispField = f['heslarDisplay'];
      }
      if(f.hasOwnProperty('heslarValue')){
        heslarValue = f['heslarValue'];
      } 
      
    //this.solrService.config['advancedFields']
    let c = f[condition.heslarDisplay];
    condition.value = f[condition.heslarField];
    condition.dispValue = f[condition.heslarDisplay];
    
  }

  openMapa() {
    this.mapaViewer.show();
    this.solrService.changeMapOpen();
  }

}
