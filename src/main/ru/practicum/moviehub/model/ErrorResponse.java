package ru.practicum.moviehub.model;

import java.util.List;

public record ErrorResponse(String error, List<String> details) {
}
