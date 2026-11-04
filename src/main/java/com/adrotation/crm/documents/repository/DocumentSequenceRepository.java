package com.adrotation.crm.documents.repository;
import com.adrotation.crm.documents.entity.DocumentSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.code = :code")
    Optional<DocumentSequence> lockByCode(@Param("code") String code);
}
