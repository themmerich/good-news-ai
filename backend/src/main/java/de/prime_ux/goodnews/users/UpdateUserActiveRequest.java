package de.prime_ux.goodnews.users;

import jakarta.validation.constraints.NotNull;

public record UpdateUserActiveRequest(@NotNull Boolean active) {
}
