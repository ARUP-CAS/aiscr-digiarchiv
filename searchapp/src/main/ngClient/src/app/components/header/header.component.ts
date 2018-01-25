import { Component, OnInit, ViewChild } from '@angular/core';
import {TranslateService, LangChangeEvent} from '@ngx-translate/core';
import { SolrService } from '../../solr.service';

declare var jQuery: any;

@Component({

  selector: 'app-header',
  templateUrl: 'header.component.html',
  styleUrls: ['header.component.css']
})
export class HeaderComponent implements OnInit {

  @ViewChild('loginuser') loginuser: any;
  currentLang: string;

  constructor(public solrService: SolrService, private translate: TranslateService) {

  }

  ngOnInit() {
    this.currentLang = this.translate.currentLang;

    this.translate.onLangChange.subscribe((event: LangChangeEvent) => {
      this.currentLang = event.lang;
      setTimeout(() => {
        jQuery('[rel="tooltip"]').tooltip('fixTitle');
      }, 100);

    });

    this.solrService.logginChanged.subscribe((logged: boolean) => {
      if(logged){
        console.log('tady');
        jQuery('.dropdown-toggle').dropdown("toggle");
      }
    });
  }

  changeLang(lang: string){
    this.solrService.currentLang = lang;
    this.translate.use(lang);
  }

  showFav(){
    this.solrService.gotoFav();
  }

  focusu(){
    setTimeout(() => {
        this.loginuser.nativeElement.focus();
      }, 100);

  }

  focusp(e, el){
      el.focus();
  }
  
  login(){
    this.solrService.login();
  }
  
  logoClicked(){
    this.solrService.closeMapa();
  }

}