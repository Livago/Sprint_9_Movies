package ru.practicum.moviehub.storage;

import ru.practicum.moviehub.model.Movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();

    public Movie getById(int id) {
        return movies.get(id);
    }

    public List<Movie> getByYear(short year) {
        return movies.values().stream()
                .filter(movie -> movie.year() == year)
                .toList();
    }

    public List<Movie> getAll() {
        return List.copyOf(movies.values());
    }

    public Movie add(String name, short year) {
        Movie movie = new Movie(idCounter.incrementAndGet(), name, year);
        movies.put(movie.id(), movie);
        return movie;
    }

    public void deleteById(int id) {
        movies.remove(id);
    }
}
