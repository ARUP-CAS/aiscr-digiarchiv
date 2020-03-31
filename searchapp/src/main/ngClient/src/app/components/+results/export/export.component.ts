import { Component, OnInit, Input } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { SolrService } from '../../../solr.service';


@Component({

  selector: 'app-export',
  templateUrl: 'export.component.html',
  styleUrls: ['export.component.css']
})
export class ExportComponent implements OnInit {

  docs: any[] = [];
  pas: any[] = [];

  constructor(private titleService: Title, public solrService: SolrService) { }

  ngOnInit() {
    this.titleService.setTitle('Digitální archiv AMČR | Export');
    this.solrService.docsSubject.subscribe(resp => {
      this.setData();
    });
    this.setData();
  }

  hasRights(result) {
    return this.solrService.hasRights(result['pristupnost']);
  }

  setData() {
    if (this.solrService.docs.length > 0) {
      this.docs = this.solrService.docs.filter(doc => doc.doctype !== 'pas');
      this.pas = this.solrService.docs.filter(doc => doc.doctype === 'pas');
    }
    //console.log(this.docs, this.pas)
  }

  numFiles(result) {
    if (result.hasOwnProperty('soubor')) {
      return JSON.parse(result.soubor[0]).length;
    } else {
      return 0;
    }
  }

  okres(result) {
    if (result.hasOwnProperty('f_okres')) {
      let okresy = [];
      let katastry = [];
      let ret = "";
      for (let idx = 0; idx < result['f_okres'].length; idx++) {
        let okres = result['f_okres'][idx];
        let katastr = result['f_katastr'][idx];

        if (katastry.indexOf(katastr) < 0) {
          okresy.push(okres);
          katastry.push(katastr);
          if (idx > 0) {
            ret += ', ';
          }
          ret += katastr + ' (' + okres + ')';
        }
      }
      return ret;
    } else {
      return "";
    }
  }

  organizace(result) {
    if (result.hasOwnProperty('organizace')) {
      let os = [];
      let ret = "";
      for (let idx = 0; idx < result['organizace'].length; idx++) {
        let o = result['organizace'][idx];
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
}
