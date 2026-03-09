package epic.gis.project.repository;

import epic.gis.project.entity.LayerFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface FeatureRepository extends JpaRepository<LayerFeature, Long> {
    List<LayerFeature> findByLayerId(UUID layerId);

    /**
     * Uses PostGIS ST_AsGeoJSON() to convert geometry inside the DB.
     * Returns each feature as a raw JSON string - extremely fast.
     */
    @Query(value = """
        SELECT json_build_object(
            'type', 'Feature',
            'id', f.id,
            'geometry', ST_AsGeoJSON(f.geom)::json,
            'properties', f.properties
        )::text
        FROM layer_features f
        WHERE f.layer_id = :layerId
    """, nativeQuery = true)
    List<String> findGeoJsonStringsByLayerId(@Param("layerId") UUID layerId);
}