package epic.gis.project.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.geotools.geojson.geom.GeometryJSON;

import com.fasterxml.jackson.databind.ObjectMapper;

import epic.gis.project.DTOs.FeatureUpdateDTO;
import epic.gis.project.entity.LayerFeature;
import epic.gis.project.entity.UploadedLayer;
import epic.gis.project.services.FileProcessingService;

@RestController
@RequestMapping("/api/layers")
@CrossOrigin(origins = "http://localhost:4200")
public class LayerController {
    @Autowired
    private FileProcessingService fileProcessingService;
    
    private final GeometryJSON geometryJSON = new GeometryJSON(15); 
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/upload")
    public ResponseEntity<UploadedLayer> uploadLayer(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name) {
        try {
            UploadedLayer layer = fileProcessingService.processFile(file, name);
            return ResponseEntity.ok(layer);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{id}/geojson")
    public ResponseEntity<Map<String, Object>> getLayerGeoJson(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return ResponseEntity.ok(fileProcessingService.getLayerGeoJson(id));
    }

    @PutMapping("/features")
    public ResponseEntity<Map<String, Object>> updateFeature(@RequestBody FeatureUpdateDTO updateDto) {
        try {
            LayerFeature updated = fileProcessingService.updateFeature(updateDto);
            
            // MANUAL CONVERSION: Entity -> Safe JSON Map
            Map<String, Object> response = new HashMap<>();
            response.put("id", updated.getId());
            response.put("layerId", updated.getLayerId());
            response.put("properties", updated.getProperties());
            
            // JTS Geometry -> GeoJSON Map
            try {
                String geomJsonString = geometryJSON.toString(updated.getGeom());
                Map<String, Object> geomMap = objectMapper.readValue(geomJsonString, Map.class);
                response.put("geometry", geomMap);
            } catch (Exception e) {
                // Return null if conversion fails, better than 500 error
                response.put("geometry", null);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
             e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}