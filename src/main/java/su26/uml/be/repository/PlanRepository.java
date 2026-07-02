package su26.uml.be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26.uml.be.entity.Plan;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
}
