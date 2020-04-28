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
  
  docid : string;
  link: string;

  constructor(private titleService: Title, 
   private activatedRoute: ActivatedRoute,
   public solrService: SolrService) { }
   
  ngOnInit() {
    this.docid = this.activatedRoute.snapshot.params.id;
    
    this.titleService.setTitle('Digitální archiv AMČR | Dokument');
    this.solrService.configObservable.subscribe(val => {
      this.getData();
    });
    
    this.paramsObserver = this.activatedRoute.params.subscribe((params: Params) => {
      this.docid = params['id'];
      
      if (this.solrService.config){
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
  
  organizace(result) {
    if (result.hasOwnProperty('organizace')) {
      let os = [];
      let ret = "";
      for (let idx = 0; idx < result['organizace'].length; idx++) {
        let org = result['organizace'][idx];
        let o = org ? this.solrService.getTranslation(org, 'organizace') : '';
        if (os.indexOf(o) < 0 && o.trim() !== '') {
          os.push(o);

          if (idx > 0) {
            ret += ', ';
          }
          ret += o;
        }

      }
      return ret;
    } else {
      return "";
    }

  }

  capitalFirst(heslo: string, heslar: string) {
    let ret = this.solrService.getTranslation(heslo, heslar);
    return ret[0].toUpperCase() + ret.slice(1);
  }

}
