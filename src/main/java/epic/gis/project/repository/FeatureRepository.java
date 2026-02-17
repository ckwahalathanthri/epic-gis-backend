package epic.gis.project.repository;

import epic.gis.project.entity.LayerFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FeatureRepository extends JpaRepository<LayerFeature, Long> {
    List<LayerFeature> findByLayerId(UUID layerId);
}