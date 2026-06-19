package su26.uml.be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26.uml.be.entity.Project;
import su26.uml.be.entity.User;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByUserAndIsDeletedFalse(User user);
    List<Project> findAllByIdIn(List<UUID> ids);
}
