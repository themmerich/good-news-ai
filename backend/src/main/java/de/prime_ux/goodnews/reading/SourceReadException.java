package de.prime_ux.goodnews.reading;

/**
 * A source could not be read. The message is written to be shown to an admin on the sources page,
 * so it names what went wrong in plain words rather than in stack-trace terms.
 */
public class SourceReadException extends RuntimeException {

	public SourceReadException(String message) {
		super(message);
	}

	public SourceReadException(String message, Throwable cause) {
		super(message, cause);
	}
}
