package com.lza.aethercare.tenant.repository;

import com.lza.aethercare.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Tenant repository：以 code 查詢（給 onboarding / admin 工具用）。 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCode(String code);
}
