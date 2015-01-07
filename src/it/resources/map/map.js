vehicles = {}
missions = {}
guideds = {}
fences = []

vehicleColor = ['orange', 'yellow', 'lightblue', 'green', 'red'];

vehicleTypes = {
  QUADROTOR: {
    url: 'quadcopter-transparent-44.png',
    anchor: new google.maps.Point(22,22),
    rotation: 25
  },
  GROUND_ROVER: {
    url: 'rover-transparent-small.png',
  },
  FIXED_WING: {
    url: 'plane-transparent.png',
    anchor: new google.maps.Point(22,22)
  },
  SURFACE_BOAT: {
    url: 'speedboat-transparent.png'
  }
}

mapZero = new google.maps.LatLng(0, 0)

function Vehicle(name, type) {
  this.name = name;
  this.marker = new MarkerWithLabel({
    title: this.name,
    icon: vehicleTypes[type],
    map: map,
    position: mapZero,
    labelContent: this.name,
    labelAnchor: new google.maps.Point(-30,25),
    labelClass: "vehicle"
  });

  this.altitude = 0;
  this.heading = 0;
  this.cog = 0;
  this.groundspeed = 0;
  this.airspeed = 0;
  this.batteryLevel = 0;
  this.batteryImage = batteryUrl(100);
  this.mode = '';
  this.throttle = 0;

  google.maps.event.addListener(this.marker, 'click', function(event) {
    map.setCenter(this.marker.getPosition());
    if (map.getZoom() < 16) {
      map.setZoom(16)
    }
  })
}

Vehicle.prototype.updateLabel = function() {
  var gs = this.groundspeed.round(2)
  var hdg = this.heading.round(0)
  var hdg2 = this.heading.round(2)
  var alt = this.altitude.round(2)
  var thr = this.throttle.round(0)
  var thr2 = this.throttle.round(2)

  this.marker.set('labelContent', '<table border=0 cellpadding=0><tr><td colspan=4>' + this.name + '</td></tr>'
    + '<tr><td>alt:</td><td>' + alt + 'm</td><td>batt:</td><td>' + this.batteryLevel + '% <img src="' + this.batteryImage + '" align=top width=21 height=10></td></tr>'
    + '<tr><td>sp:</td><td>' + gs + 'm/s</td><td>mode:</td><td>' + this.mode + '</td></tr>'
    + '<tr><td>hd:</td><td>' + hdg + '&deg;</td><td>thr:</td><td>' + thr + '%</td></tr></table>');

  this.marker.set('title', this.name + '\n'
    + 'location: ' + this.marker.position.toUrlValue(6) + '\n'
    + 'altitude: ' + alt + 'm\n'
    + 'groundspeed: ' + gs + 'm/s\n'
    + 'airspeed: ' + this.airspeed.round(2) + 'm/s\n'
    + 'heading: ' + hdg2 + '\u00b0\n'
    + 'course: ' + this.cog.round(2) + '\u00b0\n'
    + 'battery: ' + this.batteryLevel + '%\n'
    + 'throttle: ' + thr2 + '%');
}

Vehicle.prototype.setMap = function(map) {
  this.marker.setMap(map);
}

Vehicle.prototype.setPosition = function(lat, lng, alt) {
  if (lat != 0 && lng != 0) {
    var oldPos = this.marker.getPosition();
    var newPos = new google.maps.LatLng(lat, lng);
    this.marker.setPosition(newPos);
    this.altitude = alt;
    this.updateLabel();

    if (oldPos == mapZero && newPos != mapZero) {
      map.setCenter(newPos);
    }
  }
}

Vehicle.prototype.setBatteryLevel = function(level) {
  this.batteryLevel = level;
  this.batteryImage = batteryUrl(level);
  this.updateLabel();
}

Vehicle.prototype.setSpeeds = function(groundspeed, airspeed) {
  this.groundspeed = groundspeed;
  this.airspeed = airspeed;
  this.updateLabel();
}

Vehicle.prototype.set = function(prop, value) {
  this[prop] = value;
  this.updateLabel();
}

function vehicleUp(name, type) {
  vehicles[name] = new Vehicle(name, type);
}

function vehicleDown(name) {
  vehicles[name].setMap(null);
  vehicles[name] = null;
  clearMission(name);
  clearGuided(name);
}

function positionUpdate(name, lat, lng, alt) {
  vehicles[name].setPosition(lat, lng, alt);
}

function batteryUpdate(name, level) {
  vehicles[name].setBatteryLevel(level);
}

function headingUpdate(name, heading) {
  vehicles[name].set('heading', heading);
}

function cogUpdate(name, cog) {
  vehicles[name].set('cog', cog);
}

function speedUpdate(name, groundspeed, airspeed) {
  vehicles[name].setSpeeds(groundspeed, airspeed);
}

function throttleUpdate(name, throttle) {
  vehicles[name].set('throttle', throttle)
}

function batteryUrl(level) {
  var l = Math.round(level / 10) * 10;
  return 'battery/' + l + '.png';
}

function modeUpdate(name, mode) {
  vehicles[name].set('mode', mode);
  if (mode == "AUTO") {
    clearGuided(name);
  }
}

function drawMission(name, num, items) {
  var pin = pinUrl(num);
  var mission = { pins: [], path: null };

  var positions = items.filter(function (latlng) {
    return !(latlng.lat == 0 && latlng.lng == 0);
  }).map(function(item) {
    return new google.maps.LatLng(item.lat, item.lng);
  });

  for (i = 0; i < positions.length; i++) {
    mission.pins[i] = new MarkerWithLabel({
      icon: { url: pin },
      position: positions[i],
      map: map,
      labelContent: (i + 1).toString(),
      labelAnchor: new google.maps.Point(4, 29),
      labelClass: "waypoint",
      labelInBackground: false
    });
  }

  mission.path = new google.maps.Polyline({
    path: positions,
    geodesic: true,
    strokeColor: getColor(num),
    strokeOpacity: 1.0,
    strokeWeight: 2,
    zIndex: num
  });

  mission.path.setMap(map);
  missions[name] = mission;
}

function clearMission(name) {
  var mission = missions[name];
  if (mission !== undefined && mission != null) {
    var pins = mission.pins
    for (i = 0; i < pins.length; i++) {
      pins[i].setMap(null);
      pins[i] = null;
    }

    mission.path.setMap(null);
    mission.path = null;
    missions[name] = null;
  }
}

function placeGuided(name, num, lat, lng) {
  var pos = new google.maps.LatLng(lat, lng);
  if (guideds[name] != null) {
    guideds[name].setPosition(pos);
  } else {
    guideds[name] = new MarkerWithLabel({
      icon: { url: pinUrl(num) },
      position: pos,
      map: map,
      labelContent: "G",
      labelAnchor: new google.maps.Point(4, 29),
      labelClass: "waypoint",
      labelInBackground: false
    })
  }
}

function clearGuided(name) {
  var guided = guideds[name];
  if (guided !== undefined && guided != null) {
    guided.setMap(null);
    guideds[name] = null;
  }
}

function drawPolygon(color, width, lats, lngs) {
  var positions = [];
  for (i = 0; i < lats.length; i++) {
    positions[i] = new google.maps.LatLng(lats[i], lngs[i]);
  }

  var fence = new google.maps.Polyline({
    path: positions,
    map: map,
    geodesic: true,
    strokeColor: color,
    strokeOpacity: 1.0,
    strokeWeight: width
  });

  fences.push(fence);
}

function drawCircle(color, width, lat, lng, radius) {
  var fence = new google.maps.Circle({
    strokeColor: color,
    strokeOpacity: 1.0,
    strokeWeight: width,
    fillOpacity: 0,
    map: map,
    center: new google.maps.LatLng(lat, lng),
    radius: radius
  });

  fences.push(fence);
}

function pinUrl(num) {
  return 'http://google.com/mapfiles/ms/micons/' + getColor(num) + '.png';
}

function getColor(num) {
  var i = num % vehicleColor.length;
  return vehicleColor[i];
}

Number.prototype.round = function(places) {
  return +(Math.round(this + "e+" + places)  + "e-" + places);
}

/* if the map doesn't initialize on startup, there must be a parse error in this javascript file */
function initialize() {
  var mapOptions = {
    center: { lat: 37.411761, lng: -121.994161 },
    zoom: 19,
    tilt: 0,
    mapTypeId: google.maps.MapTypeId.ROADMAP
  };

  map = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);

  google.maps.event.addListener(map, 'click', function(event) {
    map.setCenter(event.latLng);
    if (map.getZoom() < 16) {
      map.setZoom(16)
    }
  })
}

google.maps.event.addDomListener(window, 'load', initialize)
