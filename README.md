# Geocode

Provide a reverse geocoding service, which is the process of back (reverse) coding of a point location (latitude,
longitude) to a known location. This service relies on a [PostGIS](http://postgis.net/) database to recognize a
coordinate in the boundaries of a several layers:
* political area and inside
* [exclusive economic zones](https://en.wikipedia.org/wiki/Exclusive_economic_zone).
The service exposed in this project is not intended to be used publicly, this service is used internally in GBIF
services to interpret countries boundaries from geographic coordinates. This project contains 3 modules:
  * geocode-api: contains the GeocodeService.get(lat,lon) service and the Location instance returned by it.
  * geocode-ws: RESTful API implementation of the GeocodeService.get(lat,lon) service.
    This service is accessible at the URL `http://{server}:{httpPort}/reverse`, and a debug interface is present at
    `http://{server}:{httpPort}/`.
  * geocode-ws-client: Java client to access the RESTful service.

There is a supporting module:
  * database: shell scripts to construct the required database, optionally run using Docker

## How to build this project

Execute the Maven command:
```
mvn clean package verify install -P{geocode}
```

A Maven profile containing the following settings is required:
```
<profile>
  <id>geocode</id>
  <properties>
    <geocode-ws.db.url>jdbc:postgresql://localhost/geocode</geocode-ws.db.url>
    <geocode-ws.db.username>eez</geocode-ws.db.username>
    <geocode-ws.db.password>password</geocode-ws.db.password>
  </properties>
</profile>
```
