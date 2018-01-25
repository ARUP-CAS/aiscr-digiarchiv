import { Component, OnInit, ViewChild } from '@angular/core';
import { ModalComponent } from 'ng2-bs3-modal/ng2-bs3-modal';

import { SolrService } from '../../../solr.service';
import { File } from '../../../shared/index';

declare var jQuery: any;

@Component({

  selector: 'app-file-viewer',
  templateUrl: 'file-viewer.component.html',
  styleUrls: ['file-viewer.component.css']
})
export class FileViewerComponent implements OnInit {

  @ViewChild('modal') modal: ModalComponent;

  showing: boolean = false;
  rolling: boolean = false;
  result: any;

  files: File[] = [];
  selectedFile: File = null;

  currentPage: number = 1;
  currentPageDisplayed: number = 1;
  fileid: number = 0;

  constructor(public solrService: SolrService) { }

  ngOnInit() {
    jQuery('.carousel').carousel({
      interval: 500
    })

  }
  selectFile(file: File, idx: number) {
    this.selectedFile = file;
    this.currentPage = 1;
    this.fileid = idx + new Date().getTime();
  }
  
  downloadUrl(){
    return this.solrService.imgPoint(this.selectedFile) + '?full=true&id=' + 
      this.selectedFile.nazev + '&filepath=' + this.selectedFile.filepath;
  }
  
  download(){
    
    window.open(this.downloadUrl());
  }

  nextPage() {
    if (!this.rolling){
      if (this.currentPage < this.selectedFile.rozsah) {
        this.rolling = true;
        this.currentPage++;
        setTimeout(() => {
          this.rolling = false;
        }, 700);
        //jQuery('#app-carousel-file-'+this.fileid).carousel('next');
      }
    }

  }

  prevPage() {
    if (!this.rolling){
      if (this.currentPage > 1) {
        this.rolling = true;
        this.currentPage--;
        setTimeout(() => {
          this.rolling = false;
        }, 700);
      }
    }
  }

  setPage() {
    if(this.currentPage > 0 || this.currentPage < this.selectedFile.rozsah){
      jQuery('#app-carousel-file-'+this.fileid).carousel(this.currentPage - 1);
    }
  }

  openModal(result) {
    this.selectedFile = null;
    this.files = [];
    this.showing = false;
    this.result = result;
    setTimeout(() => {
      this.rolling = false;
      let fs = JSON.parse(this.result.soubor[0]);
      
      for (let f in fs) {
        let file = new File();
        file.nazev = fs[f].nazev[0];
        file.mimetype = fs[f].mimetype[0];
        file.rozsah = parseInt(fs[f].rozsah[0]);
        file.size_bytes = parseInt(fs[f].size_bytes[0]);
        file.pages = new Array(file.rozsah);
        file.filepath = fs[f].filepath[0];
        file.setSize(true);
        this.files.push(file);
      }
      this.fileid = new Date().getTime();
      this.selectedFile = this.files[0];
      this.currentPage = 1;
      this.showing = true;
      this.modal.open();
    }, 10);

  }

  close() {
    this.modal.close();
    this.modal.instance.destroy();
      this.selectedFile = null;
      this.showing = false;
    setTimeout(() => {
    }, 10);

  }
  
  mimetype(){
    let s = this.selectedFile.mimetype;
    if(s.indexOf('/')>0){
      return s.split('/')[1].toUpperCase();
    } else {
      return s;
    }
    
  }


}