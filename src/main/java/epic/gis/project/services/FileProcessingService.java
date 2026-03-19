package epic.gis.project.services;

import java.io.IOException;
import java.io.StringReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.geotools.api.feature.Property;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Geometry;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.FeatureCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.geotools.geojson.geom.GeometryJSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.io.WKTWriter;

import epic.gis.project.DTOs.FeatureUpdateDTO;
import epic.gis.project.entity.LayerFeature;
import epic.gis.project.entity.UploadedLayer;
import epic.gis.project.repository.FeatureRepository;
import epic.gis.project.repository.LayerRepository;

@Service
public class FileProcessingService {
    
    @Autowired
    private LayerRepository layerRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private final GeometryJSON geometryJSON = new GeometryJSON(15); 
    private final ObjectMapper objectMapper = new ObjectMapper();
     private final WKTWriter wktWriter = new WKTWriter();

    @org.springframework.transaction.annotation.Transactional
    public UploadedLayer processFile(MultipartFile file, String name) throws IOException{
        String filename = file.getOriginalFilename();

        //save metadata
        UploadedLayer layer = new UploadedLayer();
        layer.setLayerName(name);
        layer.setOriginalFormat(getFileExtension(filename));  
        layer = layerRepository.save(layer);
        
        // 2. Determine Strategy based on Extension
        if (filename.toLowerCase().endsWith(".zip")) {
            // Assume zipped Shapefile for now
            processShapefile(file, layer);
        } else if (filename.toLowerCase().endsWith(".json") || filename.toLowerCase().endsWith(".geojson")) {
            // processGeoJson(file, layer); // To implement
        }
        
        return layer;
    }

    @org.springframework.transaction.annotation.Transactional
    private void processShapefile(MultipartFile zipFile, UploadedLayer layer) throws IOException {
        Path tempDir = Files.createTempDirectory("gis_upload_");
        File shpFile = null;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(tempDir.toFile(), zipEntry.getName());
                new File(newFile.getParent()).mkdirs();
                Files.copy(zis, newFile.toPath());
                if (newFile.getName().endsWith(".shp")) {
                    shpFile = newFile;
                }
                zipEntry = zis.getNextEntry();
            }
        }

        if (shpFile == null) {
            throw new IOException("No .shp file found in the uploaded ZIP.");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("url", shpFile.toURI().toURL());

         DataStore dataStore = DataStoreFinder.getDataStore(map);
        String typeName = dataStore.getTypeNames()[0];
        FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
        FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures();

        System.out.println("DEBUG: Found " + collection.size() + " features in shapefile: " + typeName);

        // RAW SQL for maximum speed - bypasses Hibernate entirely
        final String SQL = """
            INSERT INTO layer_features (layer_id, geom, properties)
            VALUES (?::uuid, ST_SetSRID(ST_GeomFromText(?), 4326), ?::jsonb)
        """;

        final int BATCH_SIZE = 500;
        final UUID layerId = layer.getId();

        try (FeatureIterator<SimpleFeature> features = collection.features()) {
            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            int count = 0;

            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                Object defaultGeom = feature.getDefaultGeometry();
                if (defaultGeom == null) continue;

                Geometry geom = (Geometry) defaultGeom;

                // Convert geometry to WKT string (fast)
                String wkt = wktWriter.write(geom);

                // Convert properties to JSON string
                Map<String, Object> props = new HashMap<>();
                for (Property prop : feature.getProperties()) {
                    String propName = prop.getName().toString();
                    if (!propName.equals("the_geom") && prop.getValue() != null) {
                        props.put(propName, prop.getValue().toString());
                    }
                }
                String propsJson = objectMapper.writeValueAsString(props);

                batch.add(new Object[]{layerId, wkt, propsJson});
                count++;

                if (batch.size() == BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(SQL, batch);
                    batch.clear();
                    System.out.println("Inserted batch: " + count);
                }
            }

            // Insert remaining rows
            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(SQL, batch);
                System.out.println("Inserted final batch. Total: " + count);
            }

        } catch (Exception e) {
            throw new IOException("Failed during feature insert: " + e.getMessage(), e);
        } finally {
            dataStore.dispose();
            // Cleanup temp directory
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    // public Map<String, Object> getLayerGeoJson(UUID layerId) {
    //     // Let PostGIS convert geometry to GeoJSON inside the database (very fast)
    //     List<String> rawFeatures = featureRepository.findGeoJsonStringsByLayerId(layerId);

    //     // Build the FeatureCollection wrapper
    //     // Join all pre-built feature JSON strings with commas
    //     String featuresArray = String.join(",", rawFeatures);
    //     String fullGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[" + featuresArray + "]}";

    //     try {
    //         // Parse once into a Map and return
    //         return objectMapper.readValue(fullGeoJson, Map.class);
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         // Return empty collection on error
    //         Map<String, Object> empty = new HashMap<>();
    //         empty.put("type", "FeatureCollection");
    //         empty.put("features", new ArrayList<>());
    //         return empty;
    //     }
    // }

        public String getLayerGeoJsonString(UUID layerId) {
        // Let PostGIS convert geometry to GeoJSON inside the database 
        List<String> rawFeatures = featureRepository.findGeoJsonStringsByLayerId(layerId);

        // If no features, return an empty valid GeoJSON
        if (rawFeatures == null || rawFeatures.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }

        // Build the FeatureCollection wrapper efficiently
        String featuresArray = String.join(",", rawFeatures);
        return "{\"type\":\"FeatureCollection\",\"features\":[" + featuresArray + "]}";
    }

        public byte[] getVectorTile(UUID layerId, int z, int x, int y) {
        // High-performance Mapbox Vector Tile (MVT) generation directly via PostGIS
        String sql = """
            SELECT ST_AsMVT(q, 'default')
            FROM (
                SELECT ST_AsMVTGeom(
                    ST_Transform(f.geom, 3857),
                    ST_TileEnvelope(?, ?, ?),
                    4096, 256, true
                ) AS geom, f.properties
                FROM layer_features f
                WHERE f.layer_id = ?
                  AND f.geom && ST_Transform(ST_TileEnvelope(?, ?, ?), 4326)
            ) AS q
        """;

        return jdbcTemplate.queryForObject(sql, byte[].class, z, x, y, layerId, z, x, y);
    }

    public LayerFeature updateFeature(FeatureUpdateDTO updateDto) throws Exception {
        // 1. Find existing feature
        LayerFeature feature = featureRepository.findById(updateDto.getId())
                .orElseThrow(() -> new RuntimeException("Feature not found"));

        // 2. Update Geometry if present
        if (updateDto.getGeometry() != null) {
            // Convert Map -> JSON String -> JTS Geometry
            String geoJsonString = objectMapper.writeValueAsString(updateDto.getGeometry());
            Geometry geom = geometryJSON.read(new StringReader(geoJsonString));
            // Ensure SRID is 4326
            geom.setSRID(4326);
            feature.setGeom(geom);
        }

        // 3. Update Properties if present
        if (updateDto.getProperties() != null) {
            feature.setProperties(updateDto.getProperties());
        }

        // 4. Save
        return featureRepository.save(feature);
    }

    private String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
    }

}
