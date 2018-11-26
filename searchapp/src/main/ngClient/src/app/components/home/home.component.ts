import { Component, OnInit, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs/Subscription';

import { SolrService } from '../../solr.service';
import { Filter, Facet, FacetField } from '../../shared/index';

declare var jQuery: any;

@Component({

  selector: 'app-home',
  templateUrl: 'home.component.html',
  styleUrls: ['home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {

  boxes: any;
  docsChanged: Subscription;

  constructor(private titleService: Title, public solrService: SolrService) {}

  ngOnInit() {
    this.titleService.setTitle( 'Digitální archiv AMČR | Home' );

    this.docsChanged = this.solrService.docsSubject.subscribe(val=> {
      this.fillBoxes();
    });

    if(this.solrService.config){
      this.boxes = this.solrService.config['home']['boxes'];
    }else{
      this.solrService.configObservable.subscribe(val=>{
        this.boxes = val['home']['boxes'];
      });
    }


    if(this.solrService.facetPivots.length > 0){
      this.fillBoxes();
    }else{
      this.solrService.searchObservable.subscribe(val=>{
        this.fillBoxes();
      });
    }

  }
  
  ngOnDestroy(){
    this.docsChanged.unsubscribe();
  }


 ngAfterViewInit(){
   //console.log(this.content);
   jQuery('body').popover({
        html: true,
        content: function () {
          return jQuery(this).next('.popover-content').html();
        },
        selector: '[data-popover]',
        trigger: 'hover',
        placement: 'bottom',
        delay: {show: 50, hide: 400}
      });

//   $('#abc').popover({
//      placement: 'bottom',
//      toggle: 'popover',
//      title: 'Title Whatever you Need'
//      html: true,
//      content: this.content.nativeElement
//    });
 }

  translateType(tp) {
    return this.solrService.getTranslation(tp.value, 'typ_dokumentu');
  }

  clickPivot(value: string){
    let f : Facet = new Facet();
    f.field = this.solrService.facetPivots[0].field;
    f.value = value;
    this.clickFacet(f, false);
  }

  clickPivotSub(field: string, value: string){
    //console.log(field, value);
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

  fillBoxes(){
    
    if(this.solrService.facetPivots.length === 0){
      return;
    }
    let pivots = this.solrService.facetPivots[0].values;

    for(let box in this.boxes){
      let indexval = this.boxes[box]['index'];
      this.boxes[box]['count'] = 0;
      this.boxes[box]['typy'] = [];
      for(let f in pivots){
        if(pivots[f].value === indexval){
          this.boxes[box]['count'] = pivots[f].count;

          for(let i in pivots[f].pivot){
            this.boxes[box]['typy'].push(pivots[f].pivot[i]);
          }
        }
      }
    }


  }
}
