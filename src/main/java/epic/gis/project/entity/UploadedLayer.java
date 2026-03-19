package epic.gis.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "uploaded_layers")
@Data
public class UploadedLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String layerName;

    private String originalFormat; // SHP, KML, GPX

    private boolean visible = true;

    // Store simple style config like { "color": "#FF0000", "width": 2 }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> styleConfig;

//     @Autowired
//     private FeatureRepository featureRepository;

//     public String getLayerGeoJsonString(UUID layerId) {
//     List<String> rawFeatures = featureRepository.findGeoJsonStringsByLayerId(layerId);
//     String featuresArray = String.join(",", rawFeatures);
//     return "{\"type\":\"FeatureCollection\",\"features\":[" + featuresArray + "]}";
// }
}