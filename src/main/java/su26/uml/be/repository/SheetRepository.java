package su26.uml.be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26.uml.be.entity.Project;
import su26.uml.be.entity.Sheet;

import java.util.List;
import java.util.UUID;

@Repository
public interface SheetRepository extends JpaRepository<Sheet, UUID> {
    List<Sheet> findAllByProjectOrderByOrderIndexAsc(Project project);
}
