import { Component, OnInit, Renderer } from '@angular/core';

import { TranslateService, LangChangeEvent } from '@ngx-translate/core';
import { SolrService } from '../../../solr.service';
import { MapBounds } from '../../../shared'

declare var h337: any;
declare var L: any;
declare var HeatmapOverlay: any;

@Component({

  selector: 'app-mapa',
  templateUrl: 'mapa.component.html',
  styleUrls: ['mapa.component.css']
})
export class MapaComponent implements OnInit {

  docs: any = [];

  heatmapLayer: any;
  overlayMaps: any;
  map: any;
  markers: any;
  markersList: any = [];
  popup: any;
  mapData: any;
  locationFilter: any;
  pianFilter: any;
  heatView: boolean = false;
  prepared: boolean;
  conf: any = {
    "indexMapSources": [
      { "db": "AMCR", "file": "/home/alberto/Projects/ARUP/data/AMCR_151113.csv", "map": "/home/alberto/Projects/ARUP/data/maps.json" },
      { "db": "Atlas", "file": "/home/alberto/Projects/ARUP/data/Atlas_def_151113.csv", "map": "/home/alberto/Projects/ARUP/data/maps.json" }
    ],
    "dataDir": "/home/alberto/Projects/ARUP/data/",
    "imagesDir": { "Atlas": "images_atlas/" },
    "sourcesDir": "sources/",
    "practicesDir": "practices/",
    "maxZoom": 20,
    "markerZoomLevel": 20,
    "displayRows": 100,
    "gradient": {
      "0.25": "rgb(0,0,255)",
      "0.45": "rgb(0,255,255)",
      "0.65": "rgb(0,255,0)",
      "0.95": "rgb(255,255,0)",
      "1.0": "rgb(255,128,0)"
    },

    "heatmapRadius": 0.05,
    "heatmapMaxOpacity": 0.5,
    "heatmapMinOpacity": 0.1
  }

  subs: any;
  collSubs: any;
  openSubs: any;
  isCollapsed: boolean;
  dirty: boolean;
  style: string = 'app-search-collapse app-search-collapse-map collapse ';
  height: string;

  constructor(public solrService: SolrService,
    private renderer: Renderer,
    private translate: TranslateService) { }

  ngOnInit() {
    // this.solrService.mapOpen = true;
    this.openSubs = this.solrService.mapOpenChanged.subscribe(val => {
      setTimeout(() => {
        this.setIsCollapsed();
        if (!this.isCollapsed) {
          let oldPianFilter = this.pianFilter;
          this.pianFilter = this.solrService.getPianFilter();
          this.show();
          if (this.dirty || (this.pianFilter != oldPianFilter)) {
            this.search();
            this.dirty = false;
          }
        }
      }, 100);

    });
    this.collSubs = this.solrService.routeChanged.subscribe(val => {
      setTimeout(() => {
        // this.removeMarkers();
        this.dirty = true;
        this.setIsCollapsed();
        if (!this.isCollapsed) {
          this.pianFilter = this.solrService.getPianFilter();
          this.show();
        }
      }, 100);
    });
    setTimeout(() => {
      this.setIsCollapsed();
      if (!this.isCollapsed) {
        this.show();
      }
    }, 1000);
  }

  setIsCollapsed() {
    this.isCollapsed = !this.solrService.mapOpen;
    this.style = 'app-search-collapse app-search-collapse-map collapse ' + (this.isCollapsed ? '' : 'in');
    if (this.isCollapsed) {
      this.height = '30px';
    } else {
      this.height = '690px';
    }
  }

  show() {
    if (!this.prepared) {
      setTimeout(() => {
        if (!this.prepared) {
          this.prepareMap();
        }

        this.subs = this.solrService.docsInMapSubject.subscribe(val => {
          this.docs = val;
          if (this.solrService.mapArea !== null) {
            let southWest = L.latLng(this.solrService.mapArea[0], this.solrService.mapArea[1]);
            let northEast = L.latLng(this.solrService.mapArea[2], this.solrService.mapArea[3]);
            let bounds = L.latLngBounds(southWest, northEast);
            //let bounds =  new L.LatLngBounds(this._sw, this._ne);
            this.locationFilter.setBounds(bounds);
            this.locationFilter.enable();
          } else {
            // forget previous bounds
            this.locationFilter._nw = null;
            this.locationFilter._ne = null;
            this.locationFilter._sw = null;
            this.locationFilter._se = null;

            this.locationFilter.disable();
          }
          this.setData();
        });
        this.search();
      }, 500);
    }
  }

  search() {
    if (this.map) {
      this.solrService.searchMapa(this.getMapBounds());
    }
    
  }


  getMapBounds(): MapBounds {
    //    var b;
    //    if (this.solrService.mapArea !== null){
    //      b = this.locationFilter.getBounds();
    //    }else{
    //
    //      b = this.map.getBounds();
    //    }
    let b = this.map.getBounds();
    return new MapBounds(b._southWest.lat, b._southWest.lng,
      b._northEast.lat, b._northEast.lng);
  }


  prepareMap() {
    var baseLayer = L.tileLayer(
      'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> ',
      maxZoom: this.conf.maxZoom
    }
    );

    var cfg = {
      // radius should be small ONLY if scaleRadius is true (or small radius is intended)
      "radius": this.conf.heatmapRadius,
      "maxOpacity": this.conf.heatmapMaxOpacity,
      "minOpacity": this.conf.heatmapMinOpacity,
      // scales the radius based on map zoom
      "scaleRadius": true,
      "useLocalExtrema": false,
      valueField: 'count',
      gradient: this.conf.heatmapGradient
    };


    this.heatmapLayer = new HeatmapOverlay(cfg);


    this.map = new L.Map('map', {
      center: new L.LatLng(49.803, 15.496),
      zoom: 8,
      fullscreenControl: true,
      //layers: [baseLayer, this.heatmapLayer]
      layers: [baseLayer]
    });

    this.markers = new L.featureGroup();

    this.map.addLayer(this.heatmapLayer);

    this.popup = L.popup();

    this.locationFilter = new L.LocationFilter().addTo(this.map);

    this.setMapCtrlLang();
    this.translate.onLangChange.subscribe((event: LangChangeEvent) => {
      this.setMapCtrlLang();
    });

    //            this.map.on('click', _.bind(this.onMapClick, this));
    var mc = this;
    this.map.on('zoomend', function () {
      if (mc.solrService.route !== 'document') {
        mc.search();
      }
    });
    this.map.on('dragend', function () {
      if (mc.solrService.route !== 'document') {
        mc.search();
      }
    });
    this.map.on('fullscreenchange', function () {
      mc.onResize();
    });
    this.map.on('resize', function () {
      mc.onResize();
    });

    this.locationFilter.on("change", function (e) {
      // Do something when the bounds change.
      // Bounds are available in `e.bounds`.
      mc.updateBounds(e.bounds);
    });

    this.locationFilter.on("enabled", function () {
      // Do something when enabled.
      mc.updateBounds(mc.locationFilter.getBounds());
    });

    this.locationFilter.on("disabled", function () {
      // Do something when disabled.
      mc.removeArea();
    });



    if (this.solrService.mapArea !== null) {
      let southWest = L.latLng(this.solrService.mapArea[0], this.solrService.mapArea[1]);
      let northEast = L.latLng(this.solrService.mapArea[2], this.solrService.mapArea[3]);
      let bounds = L.latLngBounds(southWest, northEast);
      //let bounds =  new L.LatLngBounds(this._sw, this._ne);
      this.locationFilter.setBounds(bounds);
      this.locationFilter.enable();
    }

    this.prepared = true;
  }

  setMapCtrlLang() {
    L.setOptions(this.map.zoomControl, {
      zoomInTitle: this.solrService.translateKey('zoom in'),
      zoomOutTitle: this.solrService.translateKey('zoom out'),
    });
    this.map.zoomControl._zoomInButton.title = this.map.zoomControl.options.zoomInTitle;
    this.map.zoomControl._zoomOutButton.title = this.map.zoomControl.options.zoomOutTitle;

    // the two calls should probably implement L.Control.Fullscreen.setOptions...
    L.setOptions(this.map.fullscreenControl, {
      title: {
        'false': this.solrService.translateKey('view fullscreen'),
        'true': this.solrService.translateKey('exit fullscreen')
      }
    });
    this.map.fullscreenControl._toggleTitle();

    L.setOptions(this.locationFilter, {
      enableButton: {
        enableText: this.solrService.translateKey('select area'),
        disableText: this.solrService.translateKey('remove selection')
      }
    });
    let textKey = this.locationFilter.isEnabled() ? 'disableText' : 'enableText';
    this.locationFilter._enableButton.setText(
      this.locationFilter.options.enableButton[textKey]);
  }

  updateBounds(bounds) {
    this.solrService.addBoundsFilter(bounds);
  }

  removeArea() {
    this.solrService.removeBoundsFilter();
  }




  setData() {

    this.removeMarkers();
    this.markersList = [];

    this.renderSearchResults();
    
    if (this.heatView) {
      var mapdata = [];
      var facet = this.solrService.facetHeatmaps['loc_rpt'];
      var gridLevel = facet['gridLevel'];
      var columns = facet['columns'];
      var rows = facet['rows'];
      var minX = facet['minX'];
      var maxX = facet['maxX'];
      var minY = facet['minY'];
      var maxY = facet['maxY'];
      var distX = (maxX - minX) / columns;
      var distY = (maxY - minY) / rows;
      var counts_ints2D = facet['counts_ints2D'];
      //console.log(gridLevel, rows, columns);
      if (counts_ints2D !== null) {

        for (var i = 0; i < counts_ints2D.length; i++) {
          if (counts_ints2D[i] !== null && counts_ints2D[i] !== "null") {
            var row = counts_ints2D[i];
            var lat = maxY - i * distY;
            for (var j = 0; j < row.length; j++) {
              var count = row[j];
              if (count > 0) {
                var lng = minX + j * distX;
                var bounds = new L.latLngBounds([
                  [lat, lng],
                  [lat - distY, lng + distX]
                ]);
                mapdata.push({ lat: bounds.getCenter().lat, lng: bounds.getCenter().lng, count: count });

              }
            }
          }
        }
      }

      this.mapData = {
        max: 2,
        data: mapdata
      };
      this.heatmapLayer.setData(this.mapData);

    }

    this.overlayMaps = {
      "heat": this.heatmapLayer,
      "markers": this.markers
    };

  }

  getVisibleCount(): number {
    if (!this.solrService.facetHeatmaps.hasOwnProperty('loc_rpt')) {
      return 0;
    }

    var facet = this.solrService.facetHeatmaps['loc_rpt'];

    var counts_ints2D = facet['counts_ints2D'];
    var count = 0;
    if (counts_ints2D !== null) {

      for (var i = 0; i < counts_ints2D.length; i++) {

        if (counts_ints2D[i] !== null && counts_ints2D[i] !== "null") {
          var row = counts_ints2D[i];
          for (var j = 0; j < row.length; j++) {
            count += row[j];
          }
        }
      }
    }
    return count;
  }

  setView() {
    var count = this.getVisibleCount();

    if (count === 0) {
      this.heatView = false;
    } else {
      this.heatView = this.map.getZoom() < this.conf.markerZoomLevel &&
        this.solrService.docsFoundInMapa > this.conf.displayRows &&
        count > this.conf.displayRows;
    }
    
    if (this.heatView) {

      if (this.map.hasLayer(this.markers)) {
        this.map.removeLayer(this.markers);
      }
      if (!this.map.hasLayer(this.heatmapLayer)) {
        this.map.addLayer(this.heatmapLayer);
      }
    } else {

      if (!this.map.hasLayer(this.markers)) {
        this.map.addLayer(this.markers);
      }
      if (this.map.hasLayer(this.heatmapLayer)) {
        this.map.removeLayer(this.heatmapLayer);
      }
    }
    if (this.solrService.route === 'document' && this.docs.length === 1 && this.docs[0]['pian_centroid_n']) {
      var ll = new L.LatLng(this.docs[0]['pian_centroid_n'][0], this.docs[0]['pian_centroid_e'][0]);
      this.map.panTo(ll);
    // } else {
    //   this.solrService.changeMapOpen();
    }
  }

  onResize() {
    var size = this.map.getSize();
    this.heatmapLayer._width = size.x;
    this.heatmapLayer._height = size.y;
    this.heatmapLayer._el.style.width = size.x + 'px';
    this.heatmapLayer._el.style.height = size.y + 'px';
    this.heatmapLayer._heatmap._width = size.x;
    this.heatmapLayer._heatmap._height = size.y;
    this.heatmapLayer._heatmap._renderer.setDimensions(size.x, size.y);
    this.heatmapLayer._update();
    this.search();
  }

  renderSearchResults() {

    this.setView();
    for (var i = 0; i < this.docs.length; i++) {

      if (!this.heatView) {
        this.addMarker(this.docs[i], i);
      }
    }
  }

  removeMarkers() {
    for (var i = 0; i < this.markersList.length; i++) {
      this.markers.removeLayer(this.markersList[i]);
    }
    this.markersList = [];
  }

  markerExists(pianId: string) {
    for (var i = 0; i < this.markersList.length; i++) {
      if (pianId === this.markersList[i]['pianId']) {
        return true;
      }
    }
    return false;
  }

  addMarker(doc, idx) {

    // samostatne nalezy maji jinak
    if (doc.hasOwnProperty('centroid_n') && doc.hasOwnProperty('pian')) {
      if (this.solrService.hasRights(doc['pristupnost'])){
        var ngMapa = this;
        let pianId = (JSON.parse(doc['pian'][0]))[0]['ident_cely'][0];
        var marker = L.marker([doc['centroid_n'][0], doc['centroid_e'][0]], { pianId: pianId });
        this.markersList.push(marker);
        marker['pianId'] = pianId;

        marker.on("popupopen", function () {
          ngMapa.renderer.listen(document.getElementById(this['pianId']), 'click', (event) => {
            ngMapa.solrService.setPianFilter(pianId);
          });
        });

        marker.bindPopup(this.popUpHtml(pianId, doc['centroid_n'][0],
        doc['centroid_e'][0], '')).addTo(this.markers);
      }
    } else if (doc.hasOwnProperty('pian')) {
      for (let i in doc['pian']) {
        this.doAddMarker(doc, idx, doc['pian'][i]);
      }
    } else {
      //this.solrService.changeMapOpen();
    }
  }


  doAddMarker(doc, idx, serialized) {
    var ngMapa = this;
    var pians = JSON.parse(serialized);

    for (let i in pians) {
      var pian = pians[i];
      if (this.solrService.hasRights(pian['parent_pristupnost'])) {
        var pianId = pian['ident_cely'][0];
        if ((!this.pianFilter || (this.pianFilter === pianId)) &&
          !this.markerExists(pianId)) {
          var marker = L.marker([pian['centroid_n'][0], pian['centroid_e'][0]], { pianId: pianId });
          this.markersList.push(marker);
          marker['pianId'] = pianId;

          marker.on("popupopen", function () {
            ngMapa.renderer.listen(document.getElementById(this['pianId']), 'click', (event) => {
              ngMapa.solrService.setPianFilter(pianId);
            });
          });

          marker.bindPopup(this.popUpHtml(pianId, pian['centroid_n'][0],
            pian['centroid_e'][0], 'PIAN: ')).addTo(this.markers);
        }
      }
    }
  }

  popUpHtml(ident_cely, centroid_n, centroid_e, prefix) {
    return '<h4><a id="' + ident_cely + '">' + prefix +
      ident_cely + ' (' +
      this.solrService.formatNumber(centroid_n) + ' ' +
      this.solrService.formatNumber(centroid_e) + ')</span></h4>';
  }


}
