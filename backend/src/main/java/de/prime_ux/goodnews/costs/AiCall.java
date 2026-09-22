package de.prime_ux.goodnews.costs;

import de.prime_ux.goodnews.tenants.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * One call to the AI and what it cost.
 *
 * <p>Written once and never touched again — hence no setters. What a call cost is a fact about a
 * moment, and a later price change must not reach back into it.
 */
@Entity
@Table(name = "ai_calls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCall {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Purpose purpose;

	@Column(nullable = false)
	private String model;

	@Column(name = "input_tokens", nullable = false)
	private int inputTokens;

	@Column(name = "output_tokens", nullable = false)
	private int outputTokens;

	/** Null where the model has no rate configured; the page shows a dash for it. */
	@Column(name = "cost_usd")
	private BigDecimal costUsd;

	@Column(name = "called_at", nullable = false)
	private Instant calledAt;

	public AiCall(Tenant tenant, Purpose purpose, String model, int inputTokens, int outputTokens,
			BigDecimal costUsd, Instant calledAt) {
		this.tenant = tenant;
		this.purpose = purpose;
		this.model = model;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.costUsd = costUsd;
		this.calledAt = calledAt;
	}
}
