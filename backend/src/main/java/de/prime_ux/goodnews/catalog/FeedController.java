package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.reading.FeedFinder;
import de.prime_ux.goodnews.reading.FeedReader;
import de.prime_ux.goodnews.reading.SourceReadException;
import de.prime_ux.goodnews.tenants.ConflictResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The catalog's feeds, for the tenant's admins — the role requirement is enforced in the
 * SecurityConfig. A feed carries no category: it brings several subjects through one address, and
 * which one a story belongs to is decided per story.
 */
@RestController
@RequestMapping("/api/feeds")
class FeedController {

	private final CurrentSession currentSession;
	private final FeedRepository feedRepository;
	private final FeedFinder feedFinder;
	private final FeedReader feedReader;

	FeedController(CurrentSession currentSession, FeedRepository feedRepository, FeedFinder feedFinder,
			FeedReader feedReader) {
		this.currentSession = currentSession;
		this.feedRepository = feedRepository;
		this.feedFinder = feedFinder;
		this.feedReader = feedReader;
	}

	/**
	 * What feeds there are behind an ordinary web address. Reaches out to the open internet, which
	 * is why it is a POST rather than a GET: it is not free, and nothing about it should be cached.
	 *
	 * <p>An empty list is a perfectly good answer — the site has no feed, and the page then offers
	 * to read the site itself.
	 */
	@PostMapping("/probe")
	ProbeResponse probe(@Valid @RequestBody ProbeRequest request) {
		try {
			return new ProbeResponse(this.feedFinder.find(request.trimmedUrl()));
		} catch (SourceReadException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@GetMapping
	@Transactional(readOnly = true)
	List<FeedResponse> listFeeds() {
		return feedRepository.findAllOfTenant(currentSession.tenant().getId()).stream()
				.map(FeedResponse::from).toList();
	}

	@PostMapping
	@Transactional
	ResponseEntity<?> createFeed(@Valid @RequestBody FeedRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		String url = request.trimmedUrl();
		if (feedRepository.existsByTenantIdAndUrlIgnoreCase(tenantId, url)) {
			return urlConflict();
		}
		requireReadable(url, request.type());
		Feed feed = feedRepository
				.save(new Feed(currentSession.tenant(), request.trimmedName(), url, request.type()));
		return ResponseEntity.status(HttpStatus.CREATED).body(FeedResponse.from(feed));
	}

	/** Feeds of other tenants answer 404 as if they did not exist. */
	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<?> updateFeed(@PathVariable UUID id, @Valid @RequestBody FeedRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		Feed feed = ownFeed(id);
		String url = request.trimmedUrl();
		boolean urlTaken = !url.equalsIgnoreCase(feed.getUrl())
				&& feedRepository.existsByTenantIdAndUrlIgnoreCase(tenantId, url);
		if (urlTaken) {
			return urlConflict();
		}
		if (urlOrTypeChanged(feed, url, request.type())) {
			requireReadable(url, request.type());
		}
		feed.update(request.trimmedName(), url, request.type());
		return ResponseEntity.ok(FeedResponse.from(feedRepository.save(feed)));
	}

	/** The articles fetched from this feed go with it, through the database cascade. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteFeed(@PathVariable UUID id) {
		feedRepository.delete(ownFeed(id));
	}

	/**
	 * A feed is read once before it is stored, so a wrong address is caught where it was typed
	 * rather than hours later in a run that quietly brought nothing back. A page is taken as it
	 * comes: whether articles can be pulled out of it only shows when the reader tries, and that
	 * belongs to the run, not to the form.
	 */
	private void requireReadable(String url, SourceType type) {
		if (type != SourceType.FEED) {
			return;
		}
		try {
			this.feedReader.read(url);
		} catch (SourceReadException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no feed: " + e.getMessage());
		}
	}

	private static boolean urlOrTypeChanged(Feed feed, String url, SourceType type) {
		return !url.equalsIgnoreCase(feed.getUrl()) || type != feed.getType();
	}

	private Feed ownFeed(UUID id) {
		return feedRepository.findByIdAndTenantId(id, currentSession.tenant().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static ResponseEntity<ConflictResponse> urlConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConflictResponse("url"));
	}
}
