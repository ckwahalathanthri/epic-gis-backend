package epic.gis.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Geometry; // Standard Java Topology Suite
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "layer_features")
@Data
public class LayerFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Link back to the parent layer
    @Column(name = "layer_id")
    private UUID layerId;

    @Column(name = "geom", columnDefinition = "geometry")
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    private Geometry geom;

    @Column(name = "properties", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> properties;
}