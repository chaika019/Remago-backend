package com.adrotation.crm.documents.service;

import com.adrotation.crm.documents.entity.DocumentSequence;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.documents.repository.DocumentSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentSequenceService {
    private final DocumentSequenceRepository repository;

    @Transactional
    public long nextSpecificationNumber() {
        DocumentSequence sequence = repository.lockByCode("SPECIFICATION")
                .orElseThrow(() -> new BusinessException("Не настроена последовательность SPECIFICATION"));
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        return value;
    }

    @Transactional
    public void ensureNextSpecificationAtLeast(long nextValue) {
        DocumentSequence sequence = repository.lockByCode("SPECIFICATION")
                .orElseThrow(() -> new BusinessException("Не настроена последовательность SPECIFICATION"));
        if (sequence.getNextValue() < nextValue) {
            sequence.setNextValue(nextValue);
            repository.save(sequence);
        }
    }
}
