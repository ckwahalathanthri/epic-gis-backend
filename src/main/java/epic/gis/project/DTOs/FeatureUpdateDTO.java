package epic.gis.project.DTOs;

import java.util.Map;

import lombok.Data;

@Data
public class FeatureUpdateDTO {
    private long id;
    private Map<String, Object> properties;//attributes
    private Map<String, Object> geometry; // GeoJSON geometry
}
