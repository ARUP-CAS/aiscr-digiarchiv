import {Component, OnInit, Output, EventEmitter} from '@angular/core';

import {SolrService} from '../../../solr.service';
import {Crumb} from '../../../shared/index';

@Component({

  selector: 'app-breadcrumbs',
  templateUrl: 'breadcrumbs.component.html',
  styleUrls: ['breadcrumbs.component.css']
})
export class BreadcrumbsComponent implements OnInit {

  @Output('onOpenTimeline') onOpenTimeline = new EventEmitter();
//  breadcrumbs: Crumb[] = [];

  constructor(public solrService: SolrService) {}

  ngOnInit() {
//    this.solrService.breadcrumbs.forEach(c => {
//       this.breadcrumbs.push(Object.assign({}, c));
//    });
   
  }

  openTimeline() {
    this.onOpenTimeline.emit(null);
  }

  formatObdobi(param: string) {
    let idx1 = param.split(',')[0];
    let idx2 = param.split(',')[1];
    let v1 = this.solrService.obdobi['response']['docs'][idx1]['poradi'];
    let v2 = this.solrService.obdobi['response']['docs'][idx2]['poradi'];
    let d1 = this.solrService.obdobi['response']['docs'][idx1]['nazev'];
    let d2 = this.solrService.obdobi['response']['docs'][idx2]['nazev'];
    return 'od ' + d1 + ' do ' + d2;
  }


  shouldTranslate(field: string) {
    return this.solrService.config['facets']['translate'].hasOwnProperty(field);
  }

  getTranslated(crumb: Crumb) {
    if (!crumb.isFilter) {
      if (crumb.condition) {
        let condFieldMap = {
          'druh_nalezu_objekt': 'objekt_druh',
          'druh_nalezu_predmet': 'predmet_druh',
          'komponenta_aktivita': 'aktivity',
          'komponenta_areal': 'areal_druha',
          'komponenta_dokumentu_obdobi': 'obdobi_druha',
          'lokalita_druh': 'druh_lokality_druha',
          'lokalita_typ_lokality': 'typ_lokality',
          'nalez_specifikace_objektu': 'specifikace_objektu_druha',
          'nalez_specifikace_predmetu': 'specifikace_predmetu',
          'objekt_kategorie': 'objekt_kategorie',
          'organizace': 'organizace',
          'predmet_kategorie': 'predmet_kategorie',
          'tvar_tvar': 'tvar',
          'zachovalost': 'zachovalost'
        };

        let field: string = crumb.condition.field;
        if (condFieldMap.hasOwnProperty(field)) {
          return this.solrService.translateKey(condFieldMap[field] + '_' + crumb.condition.dispValue);
        }
      }

      return crumb.displayValue;
    }

    let field: string = crumb.filter.field;
    let value: string = crumb.displayValue;
    if (this.shouldTranslate(field)) {
      if (field === 'obdobi_poradi') {
        let idx1 = crumb.filter.displayValue.split(',')[0];
        let idx2 = crumb.filter.displayValue.split(',')[1];
        let v1 = this.solrService.obdobi['response']['docs'][idx1]['poradi'];
        let v2 = this.solrService.obdobi['response']['docs'][idx2]['poradi'];
        let d1 = this.solrService.obdobi['response']['docs'][idx1]['nazev'];
        let d2 = this.solrService.obdobi['response']['docs'][idx2]['nazev'];
        let crumbDisplay = this.solrService.translateKey('from') + ' ' +
          this.solrService.translateKey(d1) + ' ' +
          this.solrService.translateKey('to') + ' ' +
          this.solrService.translateKey(d2);
        return crumbDisplay;
      } else {
        let cf = this.solrService.config['facets']['translate'][field];
        let typ = cf['type'];
        if (typ === 'l10n') {
          if (cf.hasOwnProperty('heslar')) {
            field = cf['heslar'];
          }

          return this.solrService.translateKey(field + '_' + value);
        } else if (typ === 'heslar') {
          return this.solrService.getFromHeslar(cf['heslar'], cf['lookupField'], value).subscribe(res => {
            if (res.length > 0) {
              return res[0][cf['retField']];
            } else {
              return value;
            }
          });
        } else {
          return this.solrService.translateKey(field + '.' + value);
        }
      }
    } else {
      return value;
    }
  }

}
