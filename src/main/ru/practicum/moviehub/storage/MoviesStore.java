package ru.practicum.moviehub.storage;

import ru.practicum.moviehub.model.Movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MoviesStore {
    /// Думал насчет реализации, нейронка предложила использовать 2 HashMap'ы для зранения ссылок
    /// byYear и byId, но решил взять более простое решение чтобы не морочить голову

    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();

    /// Здесь нужен try чтобы отлавливать возможные ошибки
    public Movie getById(int id) {
        return movies.get(id);
    }

    public List<Movie> getByYear(short year) {
        return movies.values().stream()
                .filter(m -> m.year() == year)
                .toList();
    }

    public List<Movie> getAll() {
        return List.copyOf(movies.values());
    }

    /// Метод для создания фильмов через хранилище.
    /// Создаем новый объект, задаём id, который будет использоваться в HashMap
    /// Возвращаем ссылку
    public Movie add(String name, short year) {
        Movie m = new Movie(idCounter.incrementAndGet(), name, year);
        movies.put(m.id(), m);
        return m;
    }
}