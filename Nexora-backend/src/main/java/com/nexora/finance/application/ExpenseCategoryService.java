package com.nexora.finance.application;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.ExpenseCategoryResponse;
import com.nexora.finance.domain.ExpenseCategoryEntity;
import com.nexora.finance.infrastructure.ExpenseCategoryRepository;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

@Service
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository repository;
    private final Clock clock;

    public ExpenseCategoryService(ExpenseCategoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .map(e -> new ExpenseCategoryResponse(e.getId(), e.getName(), e.isActive()))
                .toList();
    }

    @Transactional
    public ExpenseCategoryResponse create(String name) {
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (repository.existsByNameIgnoreCase(normalized)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Expense category " + normalized + " already exists.");
        }
        ExpenseCategoryEntity entity = repository.save(ExpenseCategoryEntity.of(normalized, clock.instant()));
        return new ExpenseCategoryResponse(entity.getId(), entity.getName(), entity.isActive());
    }

    @Transactional(readOnly = true)
    public boolean isValidActiveCategory(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return repository.findByNameIgnoreCase(name.trim())
                .map(ExpenseCategoryEntity::isActive)
                .orElse(false);
    }
}
