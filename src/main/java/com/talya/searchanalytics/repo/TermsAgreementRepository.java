package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.TermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {
    Optional<TermsAgreement> findByShopId(String shopId);
}
