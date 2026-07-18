package com.baseerah.application.expense;

import com.baseerah.application.infrastructure.gateway.forecast.ForecastEngine;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.client.ClientService;
import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpenseJpaEntity;
import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpensePersistenceMapper;
import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpenseRepository;
import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.kernel.Category;
import com.baseerah.shared.BadRequestException;
import com.baseerah.shared.ForbiddenException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for user-declared periodic expenses (Phase 11 / GitLab backend#1) — the backend's
 * <strong>first write service</strong>, hence class-level {@code @Transactional} (not {@code readOnly}); the
 * one read method narrows to {@code readOnly = true}. Owns the CRUD lifecycle over {@code declared_expenses}:
 * create, list-active, update-in-place, and soft-delete (deactivate). Mirrors the load-mutate-save shape of
 * the bank risk-policy write and the client-FK ownership of the rescue slice.
 *
 * <p><strong>Rule-bearing slice, not anemic CRUD.</strong> The service returns {@code domain.expense}
 * records (via {@link DeclaredExpensePersistenceMapper}), never the JPA entity — Step 11.3's calculators will
 * consume these, and the web layer maps them to DTOs. It never touches an api DTO (the dependency rule).
 *
 * <p><strong>Forecast-cache invalidation (Step 11.3).</strong> Declared expenses now feed the stress score,
 * the forecast, and the bank DTI. The score/DTI are computed on read, but the forecast is memoized by
 * {@code CachingForecastEngine}, whose cache assumed immutable telemetry. So every write here (create, update,
 * deactivate) calls {@link ForecastEngine#evict(UUID)} for the client, otherwise the user would add an
 * obligation and see no forecast change. The write path itself still only stores/retrieves; the calculators
 * read these rows in the stress, forecast, and underwriting slices.
 *
 * <p><strong>Ownership &amp; no existence leak.</strong> The controller asserts the caller owns the path
 * {@code {id}} (client) before calling in. A {@code {expenseId}} that is unknown <em>or</em> owned by another
 * client resolves to empty here and is refused with {@link ForbiddenException} (→ 403), never a 404 — so a
 * consumer cannot probe which expense ids exist.
 */
@Service
@Transactional
public class DeclaredExpenseService {

    private final DeclaredExpenseRepository repository;
    private final ClientService clientService;
    private final ForecastEngine forecastEngine;

    public DeclaredExpenseService(DeclaredExpenseRepository repository, ClientService clientService,
            ForecastEngine forecastEngine) {
        this.repository = repository;
        this.clientService = clientService;
        this.forecastEngine = forecastEngine;
    }

    /** Create a declared expense for {@code clientId}. Returns the persisted domain view. */
    public DeclaredExpense create(UUID clientId, DeclaredExpenseCommand command) {
        validate(command);
        ClientJpaEntity client = clientService.requireClient(clientId.toString());
        DeclaredExpenseJpaEntity saved =
                repository.save(DeclaredExpensePersistenceMapper.toJpaEntity(client, command));
        forecastEngine.evict(clientId);
        return DeclaredExpensePersistenceMapper.toDomain(saved);
    }

    /** All active declared expenses for {@code clientId}, oldest-first per the repository's natural order. */
    @Transactional(readOnly = true)
    public List<DeclaredExpense> listActive(UUID clientId) {
        return repository.findByClient_IdAndActiveTrue(clientId).stream()
                .map(DeclaredExpensePersistenceMapper::toDomain)
                .toList();
    }

    /** Update an existing declared expense in place (bumps {@code updated_at}). */
    public DeclaredExpense update(UUID clientId, UUID expenseId, DeclaredExpenseCommand command) {
        validate(command);
        DeclaredExpenseJpaEntity entity = requireOwned(clientId, expenseId);
        entity.setLabel(command.label());
        entity.setCategory(command.category());
        entity.setAmount(command.amount());
        entity.setDayOfMonth(command.dayOfMonth());
        DeclaredExpense updated = DeclaredExpensePersistenceMapper.toDomain(repository.save(entity));
        forecastEngine.evict(clientId);
        return updated;
    }

    /**
     * Soft-delete: flip {@code active} to {@code false}. Idempotent — deactivating an already-inactive
     * expense is a no-op (still resolves, no error), so a repeat {@code DELETE} still returns 204.
     */
    public void deactivate(UUID clientId, UUID expenseId) {
        DeclaredExpenseJpaEntity entity = requireOwned(clientId, expenseId);
        if (entity.isActive()) {
            entity.setActive(false);
            repository.save(entity);
            // Only a real state change alters telemetry; a no-op deactivate leaves the cache untouched.
            forecastEngine.evict(clientId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Resolve an expense scoped to its owning client, or refuse with a 403. Empty means the id is unknown or
     * belongs to another client; both are forbidden and indistinguishable (no existence probing).
     */
    private DeclaredExpenseJpaEntity requireOwned(UUID clientId, UUID expenseId) {
        return repository.findByIdAndClient_Id(expenseId, clientId)
                .orElseThrow(() -> new ForbiddenException(
                        "You do not have permission to access this resource."));
    }

    /**
     * Business validation the web {@code @Valid} layer either backs up or cannot express. Field constraints
     * (non-blank label, positive amount, day 1..31) are defended here too so the service is correct
     * independent of its caller; the category rule is service-only — a declared expense must name an
     * <em>expense</em> category ({@link Category#isDeclarableExpense(String)}), so an unknown string and an
     * income category (e.g. {@code SALARY}) both fail loudly with 400, while {@code OTHER} is accepted
     * (Step 11.2 / locked decision #3).
     */
    private static void validate(DeclaredExpenseCommand command) {
        if (command.label() == null || command.label().isBlank()) {
            throw new BadRequestException("label must not be blank");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("amount must be greater than 0");
        }
        if (command.dayOfMonth() < 1 || command.dayOfMonth() > 31) {
            throw new BadRequestException("dayOfMonth must be between 1 and 31");
        }
        if (!Category.isDeclarableExpense(command.category())) {
            throw new BadRequestException("Not a declared-expense category: " + command.category());
        }
    }
}
