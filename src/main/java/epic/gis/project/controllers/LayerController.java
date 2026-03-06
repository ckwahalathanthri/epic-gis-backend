package epic.gis.project.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.PathVariable;

import org.geotools.geojson.geom.GeometryJSON;

import com.fasterxml.jackson.databind.ObjectMapper;

import epic.gis.project.DTOs.FeatureUpdateDTO;
import epic.gis.project.entity.LayerFeature;
import epic.gis.project.entity.UploadedLayer;
import epic.gis.project.repository.LayerRepository;
import epic.gis.project.services.FileProcessingService;

@RestController
@RequestMapping("/api/layers")
@CrossOrigin(origins = "http://localhost:4200")
public class LayerController {
    @Autowired
    private FileProcessingService fileProcessingService;

    @Autowired
    private LayerRepository layerRepository;
    
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

    @GetMapping
    public ResponseEntity<List<UploadedLayer>> getAllLayers() {
        return ResponseEntity.ok(layerRepository.findAll());
    }

    @GetMapping("/{id}/geojson")
    public ResponseEntity<Map<String, Object>> getLayerGeoJson(@PathVariable UUID id) {
        return ResponseEntity.ok(fileProcessingService.getLayerGeoJson(id));
    }

    @PutMapping("/{layerId}/features")
    public ResponseEntity<Map<String, Object>> updateFeature(
            @PathVariable UUID layerId,
            @RequestBody FeatureUpdateDTO updateDto) {
        try {
            LayerFeature updated = fileProcessingService.updateFeature(updateDto);

            // Validate that the updated feature actually belongs to the requested layer
            if (!updated.getLayerId().equals(layerId)) {
                return ResponseEntity.badRequest().body(null); // Or 403 Forbidden
            }
            
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