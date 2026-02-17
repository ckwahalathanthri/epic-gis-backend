package epic.gis.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
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
}