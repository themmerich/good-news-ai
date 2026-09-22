package de.prime_ux.goodnews.costs;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of the list, plus what the table needs to draw its pager.
 *
 * <p>Shaped here rather than handing out Spring Data's own {@code Page}: that one's JSON is an
 * implementation detail of the library and has changed between versions. This is the contract the
 * page reads.
 *
 * @param totalCalls how many rows the tenant has in all, not how many are in this page
 */
public record AiCallPageResponse(List<AiCallResponse> calls, int page, int size, long totalCalls, int totalPages) {

	static AiCallPageResponse from(Page<AiCall> page) {
		return new AiCallPageResponse(page.getContent().stream().map(AiCallResponse::from).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
