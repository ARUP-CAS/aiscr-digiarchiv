import { Component, OnInit, OnDestroy, AfterViewInit, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Params } from '@angular/router';
import 'rxjs/add/operator/map';
import { Subscription } from 'rxjs/Subscription';

import { SolrService } from '../../solr.service';
import { FileViewerComponent } from '../+results/file-viewer/';


@Component({

  selector: 'app-doc',
  templateUrl: 'document.component.html',
  styleUrls: ['document.component.css']
})
export class DocumentComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('fileViewer') fileViewer: FileViewerComponent;
  paramsObserver: Subscription;
  
  hasConfig : boolean = false;
  
  docid : string;
  link: string;

  constructor(private titleService: Title, 
   private activatedRoute: ActivatedRoute,
   public solrService: SolrService) { }
   
  ngOnInit() {
    this.docid = this.activatedRoute.snapshot.params.id;
    
    this.titleService.setTitle('Digitální archiv AMČR | Dokument');
    this.solrService.configObservable.subscribe(val => {
      this.hasConfig =  true; 
      this.getData();
    });
    
    this.paramsObserver = this.activatedRoute.params.subscribe((params: Params) => {
      this.docid = params['id'];
      if (this.hasConfig){
        this.getData();
      }
      
    });

  }

  getData() {
    this.link = this.solrService.config['serverUrl'] + '/id/' + this.docid;
    return this.solrService.getDocument(this.docid).subscribe();

  }
  
  
  ngOnDestroy(){
    this.paramsObserver.unsubscribe();
  }

  
  
  loadDoc(){
    
  }

  ngAfterViewInit() {
    if (this.solrService.shouldPrint) {
      setTimeout(() => {
        window.print();
      }, 1000);

    }
  }

  onViewFile(doc) {
    // console.log(this.fileViewer);
    this.fileViewer.openModal(doc);
  }

}
