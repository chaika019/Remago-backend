package com.adrotation.crm.company.repository;

import com.adrotation.crm.company.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByCompany_IdOrderByFioAsc(Long companyId);
}
