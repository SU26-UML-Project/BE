package su26.uml.be.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26.uml.be.entity.Project;
import su26.uml.be.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    long countByUserAndIsDeletedFalse(User user);
    @EntityGraph(attributePaths = {"sheets"})
    List<Project> findAllByUserAndIsDeletedFalse(User user);

    @EntityGraph(attributePaths = {"sheets"})
    List<Project> findAllByUserAndIsDeletedFalseAndIsDraftFalse(User user);

    @EntityGraph(attributePaths = {"sheets"})
    List<Project> findAllByUserAndIsDeletedFalseAndIsDraftTrue(User user);

    List<Project> findAllByIdIn(List<UUID> ids);

    @EntityGraph(attributePaths = {"sheets"})
    List<Project> findAllByIsDeletedFalse();

    @EntityGraph(attributePaths = {"sheets"})
    List<Project> findAllByIsDeletedFalseAndIsDraftFalse();

    @EntityGraph(attributePaths = {"sheets"})
    List<Project> findAllByIsDeletedFalseAndIsDraftTrue();

    long countByCreatedAtBetweenAndIsDeletedFalse(LocalDateTime from, LocalDateTime to);
}
