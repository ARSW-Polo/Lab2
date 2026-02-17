package edu.eci.arsw.blueprints.persistence.impl;

import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.model.Point;
import edu.eci.arsw.blueprints.persistence.BlueprintNotFoundException;
import edu.eci.arsw.blueprints.persistence.BlueprintPersistence;
import edu.eci.arsw.blueprints.persistence.BlueprintPersistenceException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Profile("postgres")
public class PostgresBlueprintPersistence implements BlueprintPersistence {

    private final JdbcTemplate jdbcTemplate;

    public PostgresBlueprintPersistence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        createTablesIfNeeded();
    }

    private void createTablesIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS blueprints (
                  author VARCHAR(120) NOT NULL,
                  name VARCHAR(120) NOT NULL,
                  PRIMARY KEY (author, name)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS blueprint_points (
                  id BIGSERIAL PRIMARY KEY,
                  author VARCHAR(120) NOT NULL,
                  blueprint_name VARCHAR(120) NOT NULL,
                  x INT NOT NULL,
                  y INT NOT NULL,
                  position_index INT NOT NULL,
                  CONSTRAINT fk_blueprint
                    FOREIGN KEY (author, blueprint_name)
                    REFERENCES blueprints(author, name)
                    ON DELETE CASCADE
                )
                """);
    }

    @Override
    public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        try {
            jdbcTemplate.update("INSERT INTO blueprints(author, name) VALUES (?, ?)", bp.getAuthor(), bp.getName());
            for (int i = 0; i < bp.getPoints().size(); i++) {
                Point point = bp.getPoints().get(i);
                jdbcTemplate.update(
                        "INSERT INTO blueprint_points(author, blueprint_name, x, y, position_index) VALUES (?, ?, ?, ?, ?)",
                        bp.getAuthor(), bp.getName(), point.x(), point.y(), i
                );
            }
        } catch (DuplicateKeyException ex) {
            throw new BlueprintPersistenceException("Blueprint already exists: " + bp.getAuthor() + "/" + bp.getName());
        }
    }

    @Override
    public Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException {
        List<Point> points = jdbcTemplate.query(
                "SELECT x, y FROM blueprint_points WHERE author = ? AND blueprint_name = ? ORDER BY position_index",
                pointRowMapper(), author, name
        );

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blueprints WHERE author = ? AND name = ?",
                Integer.class, author, name
        );

        if (exists == null || exists == 0) {
            throw new BlueprintNotFoundException("Blueprint not found: " + author + "/" + name);
        }

        return new Blueprint(author, name, points);
    }

    @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT b.name, p.x, p.y, p.position_index " +
                        "FROM blueprints b LEFT JOIN blueprint_points p ON b.author = p.author AND b.name = p.blueprint_name " +
                        "WHERE b.author = ? ORDER BY b.name, p.position_index",
                author
        );

        if (rows.isEmpty()) {
            throw new BlueprintNotFoundException("No blueprints for author: " + author);
        }

        Map<String, List<Point>> groupedPoints = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String bpName = (String) row.get("name");
            groupedPoints.putIfAbsent(bpName, new ArrayList<>());
            Object xObj = row.get("x");
            Object yObj = row.get("y");
            if (xObj != null && yObj != null) {
                groupedPoints.get(bpName).add(new Point(((Number) xObj).intValue(), ((Number) yObj).intValue()));
            }
        }

        return groupedPoints.entrySet().stream()
                .map(entry -> new Blueprint(author, entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Blueprint> getAllBlueprints() {
        List<Map<String, Object>> headers = jdbcTemplate.queryForList("SELECT author, name FROM blueprints ORDER BY author, name");
        return headers.stream()
                .map(header -> {
                    String author = (String) header.get("author");
                    String name = (String) header.get("name");
                    List<Point> points = jdbcTemplate.query(
                            "SELECT x, y FROM blueprint_points WHERE author = ? AND blueprint_name = ? ORDER BY position_index",
                            pointRowMapper(), author, name
                    );
                    return new Blueprint(author, name, points);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blueprints WHERE author = ? AND name = ?",
                Integer.class, author, name
        );

        if (exists == null || exists == 0) {
            throw new BlueprintNotFoundException("Blueprint not found: " + author + "/" + name);
        }

        Integer nextIndex = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(position_index), -1) + 1 FROM blueprint_points WHERE author = ? AND blueprint_name = ?",
                Integer.class, author, name
        );

        jdbcTemplate.update(
                "INSERT INTO blueprint_points(author, blueprint_name, x, y, position_index) VALUES (?, ?, ?, ?, ?)",
                author, name, x, y, nextIndex == null ? 0 : nextIndex
        );
    }

    private RowMapper<Point> pointRowMapper() {
        return (rs, rowNum) -> new Point(rs.getInt("x"), rs.getInt("y"));
    }
}
