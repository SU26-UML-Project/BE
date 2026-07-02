package su26.uml.be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26.uml.be.entity.PaymentTransaction;
import su26.uml.be.enums.PaymentStatus;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByOrderCode(Long orderCode);
    Optional<PaymentTransaction> findByOrderCodeAndStatus(Long orderCode, PaymentStatus status);
}
