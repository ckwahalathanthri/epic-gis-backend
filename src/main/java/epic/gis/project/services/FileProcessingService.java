package epic.gis.project.services;

import java.io.IOException;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.geotools.geojson.geom.GeometryJSON;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final GeometryJSON geometryJSON = new GeometryJSON(15); 
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private void processShapefile(MultipartFile zipFile, UploadedLayer layer) throws IOException {
        // create temp directory
        Path tempDir = Files.createTempDirectory("gis_upload_");
        File shpFile = null;

        // Unzip
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(tempDir.toFile(), zipEntry.getName());
                // Security check for Zip Slip would go here
                
                // Ensure parent directory exists
                new File(newFile.getParent()).mkdirs();
                
                // Write file
                Files.copy(zis, newFile.toPath());
                
                if (newFile.getName().endsWith(".shp")) {
                    shpFile = newFile;
                }
                zipEntry = zis.getNextEntry();
            }
        }

        if(shpFile == null){
            throw new IOException("No .shp file found in the uploaded ZIP.");
        }

        //read Shapefile with GeoTools
        Map<String, Object> map = new HashMap<>();
        map.put("url", shpFile.toURI().toURL());

        DataStore dataStore = DataStoreFinder.getDataStore(map);
        String typeName = dataStore.getTypeNames()[0];

        FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
        FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures();

        System.out.println("DEBUG: Found " + collection.size() + " features in shapefile: " + typeName);

        try(FeatureIterator<SimpleFeature> features = collection.features()){
            List<LayerFeature> featureList = new ArrayList<>();
            int count = 0;
            int BATCH_SIZE = 500;

            while(features.hasNext()){
                SimpleFeature feature = features.next();
                
                LayerFeature layerFeature = new LayerFeature();
                layerFeature.setLayerId(layer.getId());
                
                // Get Geometry
                // Check if geometry is null to avoid errors
                Object defaultGeom = feature.getDefaultGeometry();

                // DEBUG: Print info for first feature
                if (count == 0) {
                     System.out.println("DEBUG: First feature Geometry class: " + (defaultGeom != null ? defaultGeom.getClass().getName() : "NULL"));
                }

                if (defaultGeom != null) {
                    layerFeature.setGeom((Geometry) defaultGeom);
                } else {
                    continue; // Skip features without geometry
                }

                // Get Attributes (converting to Map for JSONB)
                Map<String, Object> props = new HashMap<>();
                for (Property prop : feature.getProperties()) {
                    if (!prop.getName().toString().equals("the_geom")) {
                        props.put(prop.getName().toString(), prop.getValue());
                    }
                }
                layerFeature.setProperties(props);
                
                featureList.add(layerFeature);
                count++;

                // Batch Save
                if (count % BATCH_SIZE == 0) {
                    featureRepository.saveAll(featureList);
                    featureList.clear();
                    System.out.println("Saved batch: " + count);
                }
            }
            
            // Save remaining
            if (!featureList.isEmpty()) {
                featureRepository.saveAll(featureList);
            }
        } finally {
            dataStore.dispose();
            // Cleanup tempDir logic here
        }

    }

    // public Map<String, Object> getLayerGeoJson(UUID layerId) {
    //     List<LayerFeature> features = featureRepository.findByLayerId(layerId);
    //     Map<String, Object> geoJson = new HashMap<>();
    //     geoJson.put("type", "FeatureCollection");
    //     List<Map<String, Object>> featureList = new ArrayList<>();

    //     for (LayerFeature lf : features) {
    //         Map<String, Object> feature = new HashMap<>();
    //         feature.put("type", "Feature");
    //         feature.put("geometry", lf.getGeom()); // Jackson-datatype-jts will handle this
    //         feature.put("properties", lf.getProperties());
    //         featureList.add(feature);
    //     }
    //     geoJson.put("features", featureList);
    //     return geoJson;

    // }

    public Map<String, Object> getLayerGeoJson(UUID layerId) {
        List<LayerFeature> features = featureRepository.findByLayerId(layerId);
        Map<String, Object> geoJson = new HashMap<>();
        geoJson.put("type", "FeatureCollection");
        List<Map<String, Object>> featureList = new ArrayList<>();

        for (LayerFeature lf : features) {
            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", lf.getProperties());
            
            // MANUAL CONVERSION: JTS Geometry -> GeoJSON Map
            try {
                // GeoTools writes Geometry to a String like '{"type":"Point",...}'
                String geomJsonString = geometryJSON.toString(lf.getGeom());
                // Jackson parses that String into a real Java Map
                Map<String, Object> geomMap = objectMapper.readValue(geomJsonString, Map.class);
                feature.put("geometry", geomMap);
            } catch (Exception e) {
                e.printStackTrace(); // Log error but don't crash
            }
            
            featureList.add(feature);
        }
        geoJson.put("features", featureList);
        return geoJson;
    }

    private String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
    }

}
