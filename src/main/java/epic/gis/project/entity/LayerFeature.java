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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "layer_features_seq")
    @SequenceGenerator(name = "layer_features_seq", sequenceName = "layer_features_id_seq", allocationSize = 50)
    private Long id;

    // Link back to the parent layer
    private UUID layerId;

    // The actual spatial data. SRID 4326 = WGS84 (Lat/Lon)
    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry geom;

    // Dynamic attributes (Name, Population, etc.)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties;
}