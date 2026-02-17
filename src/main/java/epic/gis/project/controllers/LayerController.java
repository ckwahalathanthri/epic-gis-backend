package epic.gis.project.controllers;

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
    public ResponseEntity<LayerFeature> updateFeature(@RequestBody FeatureUpdateDTO updateDto) {
        try {
            LayerFeature updated = fileProcessingService.updateFeature(updateDto);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
             e.printStackTrace(); // Simple logging
            return ResponseEntity.internalServerError().build();
        }
    }
}