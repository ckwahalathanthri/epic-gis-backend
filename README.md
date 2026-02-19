"# EPIC GIS Project - Backend

A Spring Boot application providing a REST API for managing geospatial data. This project supports uploading Shapefiles (as .zip), storing them in a PostGIS database, and serving them as GeoJSON for consumption by a CesiumJS frontend.

## 🚀 Features

- **Spatial Data Upload**: Upload zipped Shapefiles (`.shp`, `.shx`, `.dbf`, etc.).
- **Automatic Parsing**: Extracts geometry and attributes using GeoTools.
- **PostGIS Storage**: Stores data efficiently using Hibernate Spatial and PostGIS.
- **GeoJSON API**: Serves data in standard GeoJSON format for web mapping.
- **Editing API**: Supports updating feature properties and geometries.

## 🛠️ Tech Stack

- **Java 21**
- **Spring Boot 3.4.2**
- **PostgreSQL + PostGIS**
- **Hibernate Spatial**
- **GeoTools 30.1** (for Shapefile processing)
- **Jackson** (for JSON handling)

## 📋 API Endpoints

### 1. Upload Layer
**POST** `/api/layers/upload`
- **Consumes**: `multipart/form-data`
- **Param**: `file` (The .zip file containing the Shapefile)
- **Returns**: Layer ID (Long)

### 2. Get Layer as GeoJSON
**GET** `/api/layers/{id}/geojson`
- **Returns**: A standard GeoJSON `FeatureCollection` containing all features in the layer.

### 3. Update Feature
**PUT** `/api/layers/features`
- **Consumes**: `application/json`
- **Body**:
  ```json
  {
    "id": 1,
    "geometry": {
      "type": "Point",
      "coordinates": [102.0, 0.5]
    },
    "properties": {
      "name": "Updated Name",
      "status": "Active"
    }
  }
  ```

## ⚙️ Setup & Installation

1. **Database**:
   - Ensure PostgreSQL is running.
   - Create a database named `epic_gis`.
   - Enable PostGIS: `CREATE EXTENSION postgis;`

2. **Configuration**:
   - Update `src/main/resources/application.properties` with your DB credentials.

3. **Build & Run**:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
" 
