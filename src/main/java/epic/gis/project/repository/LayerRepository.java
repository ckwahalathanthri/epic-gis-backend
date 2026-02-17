package epic.gis.project.repository;

import epic.gis.project.entity.UploadedLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LayerRepository extends JpaRepository<UploadedLayer, UUID> {
}