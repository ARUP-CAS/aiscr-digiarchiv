import { Component, OnInit } from '@angular/core';

import { SolrService } from '../../../solr.service';

@Component({

  selector: 'app-pagination',
  templateUrl: 'pagination.component.html',
  styleUrls: ['pagination.component.css']
})
export class PaginationComponent implements OnInit {

  currentPage : number = 1;
  first: number = 1;
  numPages: number = 5;
  range:Array<number> = [1];
  hasPagination: boolean = false;
  prevDisabled : boolean;
  nextDisabled : boolean;

  constructor(public solrService: SolrService) {

  }

  ngOnInit() {
    this.solrService.totalPagesSubject.subscribe(val=>{
      this.setRange();
    });
//    this.solrService.rowsChanged.subscribe(val=>{
//      this.setRange();
//    });
    this.setRange();
  }

  setRange(){
    this.hasPagination = this.solrService.totalPages > 1;
    if (this.hasPagination && (this.currentPage > this.solrService.totalPages)) {
      this.currentPage = 1;
    }

    this.first = this.currentPage - 2 < 1 ? 1 : this.currentPage - 2;
    this.range = [];
    let last = this.numPages;
    let delta = this.first - 1 + last - this.solrService.totalPages;
    if (delta > 0) {
        last -= delta;
    }

    for(let i = 0; i<last; i++){
      this.range.push(i+this.first);
    }
    this.prevDisabled = this.currentPage < 2;
    this.nextDisabled = this.currentPage > this.solrService.totalPages -1;
  }

  next(){
    if(!this.nextDisabled){
      this.currentPage++;
      this.setRange();
      this.solrService.setStartPage(this.currentPage);
    }
  }

  prev(){
    if(!this.prevDisabled){
      this.currentPage--;
      this.setRange();
      this.solrService.setStartPage(this.currentPage);
    }
  }

  goTo(page: number){
    this.currentPage = page;
    this.setRange();
    this.solrService.setStartPage(this.currentPage);
  }

  isActive(page: number){
    return page === this.currentPage;
  }

}
