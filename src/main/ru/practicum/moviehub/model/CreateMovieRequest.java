package ru.practicum.moviehub.model;

// Реализую что то на подобии DTO для удобного использования в тестах и запросах

public record CreateMovieRequest(String name, short year) {}