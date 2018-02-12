import { Injectable, OnDestroy, } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/switchMap';

import {Jsonp, Http, Response, URLSearchParams} from '@angular/http';

import { Observable } from 'rxjs/Rx';
//import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';
import { Subject } from 'rxjs/Subject';

import { ActivatedRoute, Router, NavigationEnd, NavigationExtras, Params } from '@angular/router';
import { SlimLoadingBarService } from 'ng2-slim-loading-bar';
import { TranslateService } from '@ngx-translate/core';

import { Filter, FilterValue, FacetField, FacetRange, FacetPivot, Facet, Crumb, MapBounds, Condition } from './shared/index';


declare var jQuery: any;
declare let ga: Function;

@Injectable()
export class SolrService implements OnDestroy {

  //Holds configuration json
  public config: any;
  public _configSubject = new Subject();
  public configObservable: Observable<any> = this._configSubject.asObservable();
  
  public currentLang: string = 'cs';
  
  public _logginChanged = new Subject();
  public logginChanged: Observable<any> = this._logginChanged.asObservable();

  public _searchSubject = new Subject();
  public searchObservable: Observable<any> = this._searchSubject.asObservable();

  //Holds response documents
  public docs: any[] = [];
  public _docsSubject = new Subject();
  public docsSubject: Observable<any> = this._docsSubject.asObservable();
  public _rowsChanged = new Subject();
  public rowsChanged: Observable<any> = this._rowsChanged.asObservable();
  
  

  //Fire when route chnaged
  public _routeChanged = new Subject();
  public routeChanged: Observable<any> = this._routeChanged.asObservable();

  public _changeMapOpen = new Subject();
  public mapOpenChanged: Observable<any> = this._changeMapOpen.asObservable();


  public _docsInMapSubject = new Subject();
  public docsInMapSubject: Observable<any> = this._docsInMapSubject.asObservable();
  public numFound: number = 0;
  public docsFoundInMapa: number = 0;
  public mapOpen: boolean = false;
  mapBounds: MapBounds;

  //Holds highlighting of results
  public highlighting = {};

  //Holds selected filters
  public filters: Filter[] = [];

  //Holds adv params
  conditions: Condition[] = [];

  // holds filters in flat mode for breadcrumbs
  public breadcrumbs: Crumb[];

  //Holds facets in solr response
  public facets: FacetField[] = [];
  public facetRanges: FacetRange[] = [];
  public facetPivots: FacetPivot[] = [];
  public facetHeatmaps = {};

  public heslare = {};

  //Holds the query
  public q: string = '';

  docid: string = '';

  //Holds start query parameter
  start: number = 0;

  //Holds number of rows per page. Default value from configuration
  rows: number = 10;

  //Holds number of pages
  totalPages: number = 0;
  public _totalPagesSubject = new Subject();
  public totalPagesSubject: Observable<any> = this._totalPagesSubject.asObservable();

  //Holds current obdobi in results
  public _currentObdobiSubject = new Subject();
  public currentObdobiSubject: Observable<any> = this._currentObdobiSubject.asObservable();
  private currentObdobi: any;

  //Observable for track url changes
  urlObserver: Subscription;
  qObserver: Subscription;

  pathObserver: Subscription;
  paramsObserver: Subscription;
  sparamsObserver: Subscription;

  isHome: boolean = true;
  bodyClass: string = 'app-page-home';
  route: string = 'home';

  sorts = [
    //{ "label": "Dle relevance", "field": "score desc" },
    { "label": "Dle idetifikatoru", "field": "uniqueid asc", "dir": "asc" },
    { "label": "Dle idetifikatoru", "field": "uniqueid desc", "dir": "desc" },
    { "label": "Dle data", "field": "rok_vzniku asc", "dir": "asc" },
    { "label": "Dle data", "field": "rok_vzniku desc", "dir": "desc" },
    { "label": "Dle autora", "field": "autor_sort asc", "dir": "asc" },
    { "label": "Dle autora", "field": "autor_sort desc", "dir": "desc" }
  ];

  selRows = [10, 20, 50];
  currentSort: any;

  loginuser: string;
  loginpwd: string;
  loginError: boolean = false;

  itemView: string = 'full';

  obdobi: any = null;
  mapArea: any;
  shouldPrint: boolean = false;

  inFavorites: boolean = false;
  favIds: string[] = [];

  constructor(private jsonp: Jsonp,
    private http: Http,
    private decimalPipe: DecimalPipe,
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private slimLoader: SlimLoadingBarService,
    private translate: TranslateService) {

    this.startProgress();
    this.getConfig().subscribe(
      cfg => {
        this.config = cfg;
        this.rows = this.config['searchParams']['rows'];
        this.currentSort = this.sorts[0];

        this.currentLang = navigator.language.split('-')[0]; // use navigator lang if available

        this.currentLang = /(cs|en)/gi.test(this.currentLang) ? this.currentLang : 'cs';

        this.currentLang = this.config['defaultLang'];
        
        

        // this language will be used as a fallback when a translation isn't found in the current language
        this.translate.setDefaultLang(this.config['defaultLang']);

        // the lang to use, if the lang isn't available, it will use the current loader to get them
        this.translate.use(this.currentLang);

        this.getObdobi().subscribe(res => {
          this.obdobi = res;
          this.processUrl();
        });
        
        this.testIsLogged();

        this._configSubject.next(this.config);


      },
      error => this.logError = <any>error
    );
  }

  ngOnDestroy() {
    this.urlObserver.unsubscribe();
    this.qObserver.unsubscribe();
    this.pathObserver.unsubscribe();
    this.paramsObserver.unsubscribe();
    this.sparamsObserver.unsubscribe();
  }

  getConfig() {
    return this.http.get("assets/config.json").map(resp => {
      this.config = resp.json();
      return resp.json();
    });
  }
  
  getText(id: string): Observable<string> {
    var url = this.config['texts'] + '?id=' + id + '&lang=' + this.translate.currentLang;
    return this.http.get(url).map((response: Response) => {
      return response.text();
    }, error => {
      console.log('error : ' + error);
      return Observable.of('error gettting content: ' + error); 
    });
  }
  
  getFromHeslar(heslar: string, lookupField: string, value: string) {


    var url = this.config['searchParams']['host'] + 'heslar/select';
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1');
    params.set('q', lookupField + ':"' + value + '"');
    params.set('fq', 'heslar_name:"' + heslar + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }

  translateKey(hkey: any): any {

    return this.translate.instant(hkey);
  }

  getTranslation(value: string, heslar: string): any {
    if (this.config['poleToHeslar'].hasOwnProperty(heslar)){
      heslar = this.config['poleToHeslar'][heslar];
    }
    let hkey = heslar + "_" + value;
    let t = this.translate.instant(hkey);
    if (t === hkey) {
      return value;
    } else {
      return t;
    }

  }

  translateHeslar(value: any): any {

    let hkey = "heslar." + value;
    let t = this.translate.instant(hkey);
    if (t === hkey) {
      return value;
    } else {
      return t;
    }

  }

  startProgress() {
    // We can listen when loading will be completed
    this.slimLoader.start(() => {
      console.log('Loading complete');
    });
  }

  completeProgress() {
    this.slimLoader.complete();
  }

  logError(err) {
    console.error('There was an error getting docs from solr. Status code: ' + err.status);
    console.error(err);
  }

  
  processUrl() {
    this.docs = [];
    this.filters = [];
    this.conditions = [];
    this.breadcrumbs = [];
    this.mapArea = null;
    this.pathObserver = this.router.events.subscribe(val => {
      if (val instanceof NavigationEnd) {
//        this.docs = [];
//        this.filters = [];
//        this.conditions = [];
//        this.breadcrumbs = [];
        this.setRoute();
        ga('set', 'page', val.urlAfterRedirects);
        ga('send', 'pageview');
      }
    });
    this.setRoute();
    this.processUrlParams();
  }
  
  setRoute(){
    if(this.router.isActive('home', false)){
      this.filters = [];
      this.conditions = [];
      this.breadcrumbs = [];
      this.mapArea = null;
      this.mapOpen = false;
      this._changeMapOpen.next(this.mapOpen);
      this.isHome = true;
      this.bodyClass = 'app-page-home';
      this.route = 'home';
      this.shouldPrint = false;
      this.q = '';
      setTimeout(() => {
        this.search();
      }, 2);
    } else if(this.router.isActive('id', false)){

        this.isHome = false;
        this.bodyClass = 'app-page-document';
        this.route = 'document';
        this.itemView = 'default';
        this.mapOpen = false;
        this.q = '';
        
//        this.getDocument(this.docid).subscribe();
      
//      
//        this.docid = this.activatedRoute.params.map(val => {
//          console.log(val);
//          return val;
//          this.getDocument(this.docid).subscribe();
//        });
    
    } else if(this.router.isActive('results', false)){
      this.isHome = false;
      this.route = 'results';
      this.bodyClass = 'app-page-results';
      this.shouldPrint = false;
    } else if(this.router.isActive('export', false)){
        this.isHome = false;
        this.route = 'export';
        this.bodyClass = 'app-page-export';
        this.shouldPrint = false;
        setTimeout(() => {
          this.search();
        }, 2);
    } else {
      this.route = '';
    }
    this._routeChanged.next(this.route);
  }
  
  processUrlParams() {
//    this.paramsObserver = this.activatedRoute.params.subscribe((params: Params) => {
//        console.log(params['id']);
//        return this.getDocument(params['id']).subscribe();
//      });
    this.sparamsObserver = this.activatedRoute.queryParams.subscribe(searchParams => {
      this.inFavorites = false;
      if (this.route === 'results' || this.route === 'export') {
      }
      
        this.currentObdobi = "0," + this.obdobi['response']['docs'].length;
        this.filters = [];
        this.conditions = [];
        this.breadcrumbs = [];
        this.mapArea = null;
        for (let p in searchParams) {
          let param = searchParams[p];
          if (param !== '') {
            if (typeof param === "string") {
              if (p === 'q') {
                this.q = param;
              }
              if (p === 'lang') {
                this.translate.use(param);
              } else if (p === 'inFavorites') {
                    this.inFavorites = param === 'true';
              
              } else if (p === 'adv') {
                this.setConditionsFromUrl(param.split(','));
              } else {
              
                let f = new Filter();
                f.field = p;
                f.display = true;
                if (this.config['searchParams']['multipleFacets'].indexOf(p) < 0) {
                  f.isMultiple = false;
                  if (p === 'loc_rpt') {
                    let coords = param.split(',');


                    f.displayValue = 'mapa: (' + this.formatNumber(coords[0]) + " " +
                      this.formatNumber(coords[1]) +
                      ', ' + this.formatNumber(coords[2]) + " " +
                      this.formatNumber(coords[3]) + ')';

                    f.queryValue = p + ':' + '[' + coords[0] + "," + coords[1] +
                      ' TO ' + coords[2] + "," + coords[3] + ']';
                    this.mapArea = coords;
                    this.breadcrumbs.push(new Crumb(true, null, f, false, f.displayValue, null));
                  } else if (p === 'obdobi_poradi') {
                    let idx1 = param.split(',')[0];
                    let idx2 = param.split(',')[1];
                    let v1 = this.obdobi['response']['docs'][idx1]['poradi'];
                    let v2 = this.obdobi['response']['docs'][idx2]['poradi'];
                    let d1 = this.obdobi['response']['docs'][idx1]['nazev'];
                    let d2 = this.obdobi['response']['docs'][idx2]['nazev'];
                    let crumbDisplay = 'od ' + d1 + ' do ' + d2;
                    f.displayValue = param;
                    f.queryValue = p + ':' + '[' + v1 + ' TO ' + v2 + ']';
                    this.breadcrumbs.push(new Crumb(true, null, f, false, crumbDisplay, null));
                    this.currentObdobi = param;

                  } else {
                    f.displayValue = param;
                    f.queryValue = p + ':"' + param + '"';
                    this.breadcrumbs.push(new Crumb(true, null, f, false, f.displayValue, null));
                  }
                  this.filters.push(f);
                } else {
                  f.isMultiple = true;
                  let fv = new FilterValue();
                  fv.displayValue = param;

                  fv.queryValue = '"' + encodeURIComponent(param) + '"';
                  f.values.push(fv);
                  this.filters.push(f);
                  this.breadcrumbs.push(new Crumb(true, null, f, true, param, fv));
                }
              }
            } else {
              if (p === 'adv') {
                this.setConditionsFromUrl(param);
              } else {
                let f = new Filter();
                f.field = p;
                f.isMultiple = true;
                f.display = true;
                for (let k in param) {
                  let fv = new FilterValue();
                  fv.displayValue = param[k];
                  fv.queryValue = '"' + encodeURIComponent(param[k]) + '"';
                  f.values.push(fv);
                  this.breadcrumbs.push(new Crumb(true, null, f, true, param[k], fv));
                }
                this.filters.push(f);
              }
            }
          }
        }
        setTimeout(() => {
          this.search();
        }, 20);
    });

  }
  
  setConditionsFromUrl(adv : string[]){
    for(let i in adv){
      let c = new Condition();
      let cjson = adv[i].split(';');
      c.fieldtype = cjson[0];
      c.field = cjson[1];
      c.operator = cjson[2];
      
      let advConfig = this.getAdvancedFieldConfig(c.field);
      this.setConditionField(c, advConfig);
      if (c.fieldtype === 'date') {
        c.valueOd = cjson[3];
        c.valueDo = cjson[4];
        c.fieldCondition = cjson[5];
        let disp = '';
        if (c.valueDo === 'undefined'){
          disp = this.translateKey('from') +' ' + c.valueOd;
        } else {
          disp = c.valueOd + ' - ' + c.valueDo;
        }
        this.breadcrumbs.push(new Crumb(false, c, null, false, disp, null));
      } else {
        c.value = cjson[3];
        c.fieldCondition = cjson[4];
        if (c.fieldtype === 'heslar'){
          c.dispValue = cjson[5];
        } else {
          c.dispValue = cjson[3];
        }
        
        this.breadcrumbs.push(new Crumb(false, c, null, false, c.dispValue, null));
      }
      this.conditions.push(c);
    }
  }
  
  setConditionField(condition: Condition, f: any){
    condition.field = f['field'];
    condition.fieldtype = f['type'];
    condition.heslar = null;
    if(f.hasOwnProperty('searchField')){
      condition.searchField = f['searchField'];
    } else {
      condition.searchField = f['field'];
    }
    if (f['type'] === 'heslar') {
      let heslarValue = 'caption';
      let dispField = 'caption';
      if(f.hasOwnProperty('heslarValue')){
        heslarValue = f['heslarValue'];
      } else if (f.hasOwnProperty('heslarDisplay')) {
        heslarValue = f['heslarDisplay'];
        dispField = f['heslarDisplay'];
      }
      
      
      if (this.heslare.hasOwnProperty(f['heslar'])) {
        condition.heslar = f['heslar'];
        condition.value = this.heslare[f['heslar']][0][heslarValue];
        condition.dispValue = this.heslare[f['heslar']][0][dispField];
        condition.heslarDisplay = dispField;
        condition.heslarField = heslarValue;
    
      } else {
        this.getHeslar(f['heslar'], dispField).subscribe(res => {
          condition.heslar = f['heslar'];
          condition.value = res[0][heslarValue];
          condition.dispValue = res[0][dispField];
          condition.heslarDisplay = dispField;
          condition.heslarField = heslarValue;
    
        });
      }
    }
  }
  
  getAdvancedFieldConfig(field: string){
    for(let c in this.config['advancedFields']){
      if(this.config['advancedFields'][c]['field'] === field){
        return this.config['advancedFields'][c];
      }
    }
    return {};
  }

  closeMapa() {
    this.mapOpen = false;
    this._changeMapOpen.next(this.mapOpen);
  }
  

  changeMapOpen() {
    this.mapOpen = !this.mapOpen;
    this._changeMapOpen.next(this.mapOpen);
  }
  
  clearTooltips(){
    jQuery('[rel="tooltip"]').tooltip('hide');
    jQuery('[role="tooltip"]').tooltip('hide');
  }

  gotoDoc(id: string, print: boolean) {
    this.clearTooltips();
    this.route = 'document';
    this.itemView = 'default';
    this.mapOpen = true;
    this.shouldPrint = print;
    this.router.navigate(['/id', id]);
    this._routeChanged.next(this.route);
  }

  setUrl(route: string) {

    this.clearTooltips();
    let params = {};
    if (this.inFavorites) {
      params['inFavorites'] = 'true';
    }
    for (let f in this.filters) {
      let name = this.filters[f].field + '';
      if (!this.filters[f].isMultiple) {
        params[name] = this.filters[f].displayValue;
      } else {
        for (let fv in this.filters[f].values)
          if (params[name]) {
            params[name].push(this.filters[f].values[fv].displayValue);
          } else {
            params[name] = [this.filters[f].values[fv].displayValue];
          }
      }
    }


    for (let i = 0; i < this.conditions.length; i++) {
      let c: Condition = this.conditions[i];
      let val = c.fieldtype + ';';
      if (c.fieldtype === 'date') {
        val += c.field + ';' + c.operator + ';' + c.valueOd + ';' + c.valueDo + ';' + c.fieldCondition;
      } else {
        val += c.field + ';' + c.operator + ';' + c.value + ';' + c.fieldCondition + ';' + c.dispValue;
      }
      if (i === 0) {
        params['adv'] = [val];
      } else {
        params['adv'].push(val);
      }

    };
    //this.router.go(route, params);
    
    if(route === 'export'){
      var str = "";
      for (var key in params) {
          if (str != "") {
              str += "&";
          }
          str += key + "=" + encodeURIComponent(params[key]);
      }
      str += "&lang=" + this.translate.currentLang;
      window.open('export?' + str, '_blank');
    } else {
      let navigationExtras: NavigationExtras = {
        queryParams: params
      };
      this.router.navigate([route], navigationExtras);

      this._routeChanged.next(route);
    }
  }


  //called when changing sorting
  sortBy(s) {
    this.currentSort = s;
    this.search();
  }

  //called when number of rows per page change
  setRows(rows: number) {
    this.rows = rows;
    this.search();
    this._rowsChanged.next(rows);
  }

  //Called when changing q input
  setQ() {
    this.filters = [];
    let f = new Filter();
    f.field = 'q';
    f.displayValue = this.q;
    f.display = true;
    this.filters.push(f);
    this.start = 0;
    this.setUrl('results');
  }

  gotoExport() {

    this.setUrl('export');
  }

  setAdvSearch(conditions: Condition[]) {
    //console.log(conditions);
    this.conditions = conditions;
    this.setUrl('results');

  }

  getBoundsFilter(): Filter {
    for (let f = 0; f < this.filters.length; f++) {
      let name = this.filters[f].field + '';
      if (name === 'loc_rpt') {
        return this.filters[f];
      }
    }
    let f: Filter = new Filter();
    this.filters.push(f);
    return f;
  }

  /* Called in mapa component when area is selected */
  addBoundsFilter(bounds: any) {
    let f: Filter = this.getBoundsFilter();
    let value = bounds.getSouthWest().lat + "," + bounds.getSouthWest().lng +
      ',' + bounds.getNorthEast().lat + "," + bounds.getNorthEast().lng;

    f.displayValue = value;
    f.field = "loc_rpt";
    this.start = 0;
    this.setUrl('results');
  }


  /* Called in mapa component when area is removed */
  removeBoundsFilter() {
    for (let f = 0; f < this.filters.length; f++) {
      let name = this.filters[f].field + '';
      if (name === 'loc_rpt') {
        this.filters.splice(f, 1);
        this.mapArea = null;
      }
    }
    this.setUrl('results');
  }

  /* Called in facets component when filter is selected */
  addFilter(field: string, value: string) {
    let f: Filter = new Filter();
    f.displayValue = value;
    f.field = field;
    this.filters.push(f);
    this.start = 0;
    this.setUrl('results');
  }

  /* first check if filter for that field exists */

  setFilter(field: string, value: string) {
    for (let f = 0; f < this.filters.length; f++) {
      let name = this.filters[f].field + '';
      if (name === field) {
        this.filters[f].displayValue = value;
        this.setUrl('results');
        return;
      }
    }
    this.addFilter(field, value);
  }



  setPianFilter(pianId: string) {
    for (let f = 0; f < this.filters.length; f++) {
      let name = this.filters[f].field + '';
      if (name === 'pian_ident_cely') {
        this.filters[f].displayValue = pianId;
        this.setUrl('results');
        return;
      }
    }
    this.addFilter('pian_ident_cely', pianId);
  }

  /* first check if filter for that field exists */

  setObdobiFilter(field: string, value: string, displayValue: string) {
    for (let f = 0; f < this.filters.length; f++) {
      let name = this.filters[f].field + '';
      if (name === field) {
        this.filters[f].displayValue = displayValue;
        this.setUrl('results');
        return;
      }
    }
    this.addFilter(field, value);
  }

  /* Called in facets component when filter in multiple facet is selected */
  addFilterValue(field: string, value: string) {
    for (let f in this.filters) {
      if (this.filters[f].field === field) {
        let fv = new FilterValue();
        fv.displayValue = value;
        fv.queryValue = '"' + encodeURIComponent(value) + '"';
        this.filters[f].values.push(fv);
        this.setUrl('results');
        return;
      }
    }

    let f: Filter = new Filter();
    f.displayValue = value;
    f.field = field;
    this.filters.push(f);
    this.setUrl('results');
  }

  removeFacetValue(field: string, value: string) {
    for (let f = 0; f < this.filters.length; f++) {
      let name = this.filters[f].field + '';
      if (name === field) {
        if (this.filters[f].isMultiple) {
          for (let i = 0; i < this.filters[f].values.length; i++) {
            if (value === this.filters[f].values[i].displayValue) {
              if (this.filters[f].values.length === 1) {
                this.filters.splice(f, 1);
              } else {
                this.filters[f].values.splice(i, 1);
              }
              this.setUrl('results');
              return;
            }
          }
        }
      }
    }
  }

  /* Called in facets or filters component when filter should be removed */
  removeFilter(f: Filter) {
    if (f.field === 'loc_rpt') {
      this.mapArea = null;
    }
    var index = this.filters.indexOf(f, 0);
    if (index !== -1) {
      this.filters.splice(index, 1);
      this.setUrl('results');
    }
  }

  /* Called in facets or filters component when adv filter condition  should be removed */
  removeCondition(c: Condition) {
    var index = this.conditions.indexOf(c, 0);
    if (index != -1) {
      this.conditions.splice(index, 1);
      this.setUrl('results');
    }
  }

  /* Called in facets or filters component when multiple filter value should be removed */
  removeFilterValue(f: Filter, fv: FilterValue) {
    var idx = f.values.indexOf(fv, 0);
    if (idx != -1) {
      f.values.splice(idx, 1);
      this.setUrl('results');
    }
  }

  removeFilterFromCrumb(crumb: Crumb) {
    if (crumb.isFilter) {
      if (crumb.isMultiple) {
        this.removeFilterValue(crumb.filter, crumb.filterValue);
      } else {
        this.removeFilter(crumb.filter);
      }
      
      if (crumb.filter.field === 'q'){
        this.q = '';
      }
    } else {
      this.removeCondition(crumb.condition);
    }
  }

  /* Called in pagination when page changes */
  setStartPage(page: number) {
    this.start = (page - 1) * this.rows;
    this.search();
  }

  /* Search methods */

  baseUrl(core: string) {
    return this.config['searchParams']['host'] + this.config['searchParams'][core] + this.config['searchParams']['handler'];
  }

  addCommonParams(params: URLSearchParams) {
    params.set('wt', 'json');
    params.set('json.nl', 'map');
    params.set('json.wrf', 'JSONP_CALLBACK');
  }

  addAdvSearchParams(params: URLSearchParams) {

    //console.log(this.conditions);
    if (this.conditions.length > 0) {
      let adv = '';
      for (let i = 0; i < this.conditions.length; i++) {
        let c: Condition = this.conditions[i];
        if (c.field === 'komponenta_aktivita') {
          let fval = 'komponenta_aktivita_' + this.config['aktivity'][c.value];
          if (i === 0) {
            adv = fval + ':1';
          } else {
            adv += ' ' + c.operator.toUpperCase() + ' ' + fval + ':1';
          }

        } else {
          if (i === 0) {
            adv = c.searchField + ':';
          } else {
            adv += ' ' + c.operator.toUpperCase() + ' ' + c.searchField + ':';
          }
          if (c.fieldtype === 'heslar') {
            adv += '"' + c.value + '"';
          } else if (c.fieldtype === 'date') {
            adv += '[' + c.valueOd + ' TO ' + c.valueDo + ']';
          } else {
            if (c.fieldCondition === 'starts') {
              adv += this.escapeSolrChars(c.value) + '*';
            } else if (c.fieldCondition === 'equals') {
              adv += '"' + c.value + '"';
            } else {
              adv +=  '*' + c.value + '*';
            }
          }
        }
      };
      params.append('fq', adv);
    }
  }

  doSearchParams(): URLSearchParams {
    var params = new URLSearchParams();
    this.addCommonParams(params);
    if (this.route === 'results' || this.route === 'export') {
      this.addAdvSearchParams(params);
      if (this.route === 'export') {
        params.set('rows', this.config['exportRowsLimit'].toString());
      } else {
        params.set('rows', this.rows.toString());
      }
      params.set('start', this.start.toString());
      params.set('sort', this.currentSort['field']);


      if (this.inFavorites) {
        let favFq: string = 'uniqueid:(kkk';
        for (let i in this.favIds) {
          favFq += ' "' + this.favIds[i] + '"';
        }
        favFq += ')';

        params.append('q.op', 'OR');
        params.append('fq', favFq);
      }

      for (let f in this.filters) {
        let name = this.filters[f].field + '';
        //if(this.config['searchParams']['multipleFacets'].indexOf(name) > -1){
        if (this.filters[f].isMultiple) {
          // Filter are stacked with OR
          let fqValue = '{!tag=' + name + 'F}' + name + ':';
          for (let i = 0; i < this.filters[f].values.length; i++) {

            if (i > 0) {
              fqValue += ' OR ';
            }
            fqValue += this.filters[f].values[i].queryValue;
          }
          params.append('fq', fqValue);
        } else if (name.toString() === 'q') {
          params.set('q', this.q);
        } else {
          params.append('fq', this.filters[f].queryValue);
        }
      }
    } else if (this.route === 'document') {
      params.set('rows', '1');
      params.set('start', '0');

      params.set('q', 'ident_cely:"' + this.docid + '"');
    } else {
      params.set('rows', '0');
      params.set('start', '0');
    }


    return params;
  }

  search() {
    this.startProgress();
    this.docs = [];
    var url = this.baseUrl('core');
    var params = this.doSearchParams();

    if (this.config.isTest) {
      console.log(params);
      this.searchDummy();
      return;
    }

    this.doSearch(url, params).subscribe(res => {
      
      
      window.scrollTo(0, 0);
      this.processResponse(res);
      if (this.mapOpen) {
        this.searchMapa(this.mapBounds);
      } else {
        this.completeProgress();
      }
    });
  }

//  searchDokuments() {
//    this.startProgress();
//    var url = this.baseUrl('core');
//    var params = this.doSearchParams();
//
//    params.append('fq', 'doctype:dokument');
//
//    this.doSearch(url, params).subscribe(res => {
//
//      this.processResponse(res);
//      this.completeProgress();
//    });
//  }


  doSearch(url, params: URLSearchParams) {
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json();
    });
  }

  getDocument(id: string) {
    this.docid = id;
    this.startProgress();
    var url = this.baseUrl('core');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1');
    params.set('q', 'ident_cely:"' + id + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      this.docs = res.json()['response']['docs'];
      if (this.mapOpen) {
        this.searchMapa(this.mapBounds);
      }
      this.completeProgress();
    });

  }

  getAkce(id: string) {
    var url = this.baseUrl('exportCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1');
    params.set('q', 'ident_cely:"' + id + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'][0];
    });
  }

  getLokalita(id: string) {
    var url = this.baseUrl('exportCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1');
    params.set('q', 'ident_cely:"' + id + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'][0];
    });
  }

  getDokJednotky(id: string) {

    var url = this.baseUrl('exportCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '100');
    params.set('sort', 'uniqueid+asc');

    params.set('q', 'parent:"' + id + '"');
    params.set('fq', 'doctype:"dokumentacni_jednotka"');

    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }

  getExterniOdkaz(id: string) {

    var url = this.baseUrl('relationsCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '100');

    params.set('q', 'vazba:"' + id + '"');
    params.set('fq', 'doctype:"externi_odkaz"');

    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }

  getExterniZdroj(id: string) {

    var url = this.baseUrl('exportCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '100');

    params.set('q', 'ident_cely:"' + id + '"');
    params.set('fq', 'doctype:"externi_zdroj"');

    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }

  getKomponenty(id: string) {

    var url = this.baseUrl('exportCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '100');
    params.set('sort', 'uniqueid+asc');

    params.set('q', 'parent:"' + id + '"');
    params.set('fq', 'doctype:"komponenta"');

    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }

  getNalez(id: string) {
    var url = this.baseUrl('relationsCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '100');
    params.set('q', 'komponenta:"' + id + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }
  
  getNalezKomponentaDok(id: string) {
    var url = this.baseUrl('relationsCore');
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '100');
    params.set('q', 'komponenta_dokument:"' + id + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'];
    });
  }

  getPian(id: string) {
    var url = this.config['searchParams']['host'] + this.config['searchParams']['exportCore'] + this.config['searchParams']['handler'];
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1');
    params.set('q', 'ident_cely:"' + id + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['docs'][0];
    });
  }

  getObdobi() {
    var url = this.config['searchParams']['host'] + 'heslar/select';
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1000');
    params.set('q', '{!term f=heslar_name}obdobi_prvni');
    params.set('stats', 'true');
    params.set('stats.field', 'poradi');
    params.set('sort', 'poradi asc');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json();
    });
  }

  getHeslar(h: string, sort: string) {
    var url = this.config['searchParams']['host'] + 'heslar/select';
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('rows', '1000');
    params.set('q', '{!term f=heslar_name}' + h);
    params.set('sort', 'poradi asc, ' + sort + ' asc');
    return this.jsonp.get(url, { search: params }).map(res => {
      this.heslare[h] = res.json()['response']['docs'];
      
      return this.heslare[h];
    });
  }

  searchMapa(rect: MapBounds) {
    if (!rect) {
      return;
    }
    //this.startProgress();
    this.mapBounds = rect;
    this.doSearchMapa(rect).subscribe(res => {
      this.docsFoundInMapa = res['response']['numFound'];
      this.facetHeatmaps = res['facet_counts']['facet_heatmaps'];
      this._docsInMapSubject.next(res['response']['docs']);
      this.completeProgress();
    });
  }

  doSearchMapa(rect: MapBounds) {
    var url = this.config['searchParams']['host'] + this.config['searchParams']['core'] + this.config['searchParams']['handler'];
    var params = this.doSearchParams();
    params.set('start', '0');
    if (this.route !== 'document') {
      params.set('rows', '100');
      if (this.mapArea === null) {
        let rpt = '[' + rect.south + ',' + rect.west +
          ' TO ' + rect.north + ',' + rect.east + ']';
        params.append('fq', 'loc_rpt:' + rpt);
      }
    } else {

    }
    params.set("facet.heatmap", "loc_rpt");

    //     let latCenter = (rect.north + rect.south) * .5;
    //     let lngCenter = (rect.west + rect.east) * .5;
    let dist = Math.max((rect.east - rect.west) * .005, .02);
    params.set("facet.heatmap.distErr", dist + "");
    params.set("facet.heatmap.maxCells", '400000');
    params.set("facet.heatmap.maxLevel", '7');


    let heatGeom = '["' + rect.west + ' ' + rect.south +
      '" TO "' + rect.east + ' ' + rect.north + '"]';

    params.set("facet.heatmap.geom", heatGeom);


    return this.jsonp.get(url, { search: params }).map(res => {

      return res.json();
    });
  }

  searchDummy() {
    var url = 'assets/select.json';
    this.doSearchDummy(url).subscribe(res => {
      this.processResponse(res);
    });
  }

  doSearchDummy(url) {
    return this.http.get(url).map(res => res.json());
  }

  /* render methods */

  fillFacets(facet_fields: any) {
    for (let field in facet_fields) {
      if (Object.keys(facet_fields[field]).length > 1) {

        var facetField = new FacetField();
        facetField.field = field;
        facetField.isMultiple = this.config['searchParams']['multipleFacets'].indexOf(field) > -1;
        for (let f in facet_fields[field]) {
          if (f.trim() !== '') {
            let facet = new Facet();
            facet.field = field;
            facet.value = f;
            facet.count = facet_fields[field][f];
            facet.isUsed = this.facetUsed(field, f);
            facetField.values.push(facet);
            facetField.values.sort((a: Facet, b: Facet) =>{
              return b.count - a.count;
            });
          }
        }
        if(field !== 'pristupnost'){
          this.facets.push(facetField);
        }else{
          this.facets.unshift(facetField);
        }
      }
    }
  }

  fillFacetRanges(facet_ranges) {
    for (let field in facet_ranges) {
      let fr = facet_ranges[field];
      var facetRange = new FacetRange();
      facetRange.field = field;
      facetRange.gap = fr['gap'];
      facetRange.start = fr['start'];
      facetRange.end = fr['end'];
      facetRange.before = fr['before'];
      facetRange.after = fr['after'];
      for (let f in fr['counts']) {
        let facet = new Facet();
        facet.field = field;
        facet.value = f;
        facet.count = fr['counts'][f];
        facet.isUsed = this.facetUsed(field, f);
        facetRange.counts.push(facet);
      }
      this.facetRanges.push(facetRange);
    }
  }



  fillFacetPivots(facet_pivots) {
    for (let fp in facet_pivots) {
      let fr = facet_pivots[fp];
      if (fr.length > 0) {
        var facetPivot = new FacetPivot();
        facetPivot.field = fr[0].field;

        for (let v in fr) {
          let value = fr[v];
          facetPivot.values.push(value);
        }
        this.facetPivots.push(facetPivot);
      }
    }

  }

  processResponse(resp: any) {
    //Detect changes in numFound a emits changes to pages
    if (this.numFound !== resp['response']['numFound']) {
      this.numFound = resp['response']['numFound'];
    }
    this.totalPages = Math.ceil(this.numFound / this.rows);
    this._totalPagesSubject.next(this.totalPages);
    this._currentObdobiSubject.next(this.currentObdobi);
    this.docs = resp['response']['docs'];
    this.highlighting = resp['highlighting'];
    this.facets = [];
    this.facetRanges = [];
    this.facetPivots = [];

    if (resp.hasOwnProperty('facet_counts')) {
      this.fillFacets(resp['facet_counts']['facet_fields']);
      this.fillFacetRanges(resp['facet_counts']['facet_ranges']);
      this.fillFacetPivots(resp['facet_counts']['facet_pivot']);

    }

    this._searchSubject.next(1);
    this._docsSubject.next(this.docs);
  }

  facetUsed(field: string, value: string) {
    for (let f in this.filters) {
      let name = this.filters[f].field + '';
      if (name === field) {
        if (this.filters[f].isMultiple) {
          for (let i = 0; i < this.filters[f].values.length; i++) {
            if (value === this.filters[f].values[i].displayValue) {
              return true;
            }
          }
        } else {
          if (value === this.filters[f].displayValue) {
            return true;
          }
        }
      }
    }
    return false;
  }

  getIsFav(docid: string) {
    var url = this.config['searchParams']['host'] + 'favorites/select';
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('q', 'uniqueid:"' + this.config['user']['userid'] + '_' + docid + '"');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json()['response']['numFound'] > 0;
    });
  }
  
  gotoFav(){
    this.inFavorites = true;
    this.getFav().subscribe(() => {
      this.setUrl('results');
    });
    
  }
  
  removeInFav(){
    this.favIds = [];
    this.inFavorites = false;
    this.setUrl('results');
  }

  getFav() {
    var url = this.config['searchParams']['host'] + 'favorites/select';
    var params = new URLSearchParams();
    this.addCommonParams(params);
    params.set('q', 'username:"' + this.config['user']['userid'] + '"');
    params.set('fl', 'docid');
    params.set('rows', '100');
    return this.jsonp.get(url, { search: params }).map(res => {
      this.favIds = [];
      let docs = res.json()['response']['docs'];
      for (let i in docs) {
        this.favIds.push(docs[i]['docid']);
      }
    });

  }

  addFav(docid: string) {
    var url = this.config['fav'];
    var params = new URLSearchParams();
    params.set('action', 'ADD');

    params.set('json.wrf', 'JSONP_CALLBACK');
    params.set('docid', docid);
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json();
    });
  }


  removeFav(docid: string) {
    var url = this.config['fav'];
    var params = new URLSearchParams();
    params.set('action', 'REMOVE');

    params.set('json.wrf', 'JSONP_CALLBACK');
    params.set('docid', docid);
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json();
    });
  }

  /* Login methods */

  login() {
    this.loginError = false;
    return this.doLogin().subscribe(res => {
      if (res.hasOwnProperty('error')) {
        console.log(res['error']);
        this.loginError = true;
        this.config['logged'] = false;
      } else {
        this.config['logged'] = true;
        this.loginError = false;

        this.loginuser = '';
        this.loginpwd = '';
        for (let first in res) {
          this.config['user'] = res[first];
          this.config['user']['userid'] = first;
          break;
        }
      }
      this.search();
      this._logginChanged.next(this.config['logged']);
    });
  }
  
  hasRights(pristupnost: string){
    if(pristupnost === 'A'){
      return true;
    } else if(this.config['logged'] ){
      return this.config['user']['pristupnost'].localeCompare(pristupnost) > -1;
    } else {
      return false;
    }
  }
  
  
  testIsLogged(){
    this.doTestIsLogged().subscribe(res => {
      if (res.hasOwnProperty('error')) {
        console.log(res['error']);
        this.config['logged'] = false;
      } else {
        this.config['logged'] = true;
        this.loginuser = '';
        this.loginpwd = '';
        for (let first in res) {
          this.config['user'] = res[first];
          this.config['user']['userid'] = first;
          break;
        }
      }
      this.search();
    });
  }
  
  doTestIsLogged(){
    var url = this.config['loginPoint'];
    var params = new URLSearchParams();
    params.set('action', 'TESTLOGIN');
    params.set('json.wrf', 'JSONP_CALLBACK');
    return this.jsonp.get(url, { search: params }).map(resp => {
      return resp.json();
      
    }, error => {
      console.log('error : ' + error);
    });
  }

  doLogin() {
    var url = this.config['loginPoint'];
    var params = new URLSearchParams();
    params.set('user', this.loginuser);
    params.set('pwd', this.loginpwd);
    params.set('action', 'LOGIN');
    params.set('json.wrf', 'JSONP_CALLBACK');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json();
    }, error => {
      console.log('error : ' + error);
    });

  }

  logout() {
    this.doLogin().subscribe(res => {
      if (res.hasOwnProperty('error')) {
        console.log(res['error']);
      }
      this.config['logged'] = false;
      this.config['user'] = {};
      this.search();
      this._logginChanged.next(this.config['logged']);
    });
  }

  doLogout() {

    var url = this.config['loginPoint'];
    //console.log(this.loginuser, this.loginpwd, url);
    var params = new URLSearchParams();
    params.set('action', 'LOGOUT');
    params.set('json.wrf', 'JSONP_CALLBACK');
    return this.jsonp.get(url, { search: params }).map(res => {
      return res.json();
    });

  }

  imgPoint(doc: any) {
    if (doc.hasOwnProperty('mimetype')) {
      if (doc['mimetype'].indexOf('pdf') > 0) {
        return this.config['pdf'];
      } else {
        return this.config['img'];
      }
    } else {
      return this.config['img'];
    }
  }

  setItemView(state: string) {
    //$('.app-result-item-inner .media-left').removeAttr('style'); // clean style attr
    this.itemView = state;
  }


  /* utility functions
   *
   */
   
   escapeSolrChars(val: string){
     let ret = val.replace(/([\!\*\+\&\-\|\(\)\[\]\{\}\^\~\?\:\"])/g, "\\$1");
     return ret;
   }

  //return fontawesome class name by fileextension
  icon(value: string): string {
    return this.config['icons'][value];
  }

  formatNumber(n: string) {

    return this.decimalPipe.transform(parseFloat(n), '2.1-3')
  }

  hasValue(field: string, doc: any): boolean {
    if (doc.hasOwnProperty(field)) {
      if (typeof doc[field] === "string") {
        return doc[field].trim() !== '';
      } else {
        return doc[field][0].trim() !== '';
      }

    } else {
      return false;
    }
  }
}
