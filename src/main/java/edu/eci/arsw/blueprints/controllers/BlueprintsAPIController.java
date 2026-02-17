package edu.eci.arsw.blueprints.controllers;

import edu.eci.arsw.blueprints.api.ApiResponse;
import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.model.Point;
import edu.eci.arsw.blueprints.persistence.BlueprintNotFoundException;
import edu.eci.arsw.blueprints.persistence.BlueprintPersistenceException;
import edu.eci.arsw.blueprints.services.BlueprintsServices;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/blueprints")
public class BlueprintsAPIController {

    private final BlueprintsServices services;

    public BlueprintsAPIController(BlueprintsServices services) { this.services = services; }

    @Operation(summary = "List all blueprints")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Query successful")
    @GetMapping
    public ResponseEntity<ApiResponse<Set<Blueprint>>> getAll() {
        Set<Blueprint> blueprints = services.getAllBlueprints();
        return ResponseEntity.ok(new ApiResponse<>(200, "execute ok", blueprints));
    }

    @Operation(summary = "Get blueprints by author")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Query successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Author not found")
    @GetMapping("/{author}")
    public ResponseEntity<ApiResponse<Set<Blueprint>>> byAuthor(@PathVariable String author)
            throws BlueprintNotFoundException {
        Set<Blueprint> blueprints = services.getBlueprintsByAuthor(author);
        return ResponseEntity.ok(new ApiResponse<>(200, "execute ok", blueprints));
    }

    @Operation(summary = "Get one blueprint by author and name")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Query successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint not found")
    @GetMapping("/{author}/{bpname}")
    public ResponseEntity<ApiResponse<Blueprint>> byAuthorAndName(
            @PathVariable String author,
            @PathVariable String bpname
    ) throws BlueprintNotFoundException {
        Blueprint blueprint = services.getBlueprint(author, bpname);
        return ResponseEntity.ok(new ApiResponse<>(200, "execute ok", blueprint));
    }

    @Operation(summary = "Create a new blueprint")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Blueprint created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    @PostMapping
    public ResponseEntity<ApiResponse<Blueprint>> add(@Valid @RequestBody NewBlueprintRequest req)
            throws BlueprintPersistenceException {
        Blueprint bp = new Blueprint(req.author(), req.name(), req.points());
        services.addNewBlueprint(bp);
        return ResponseEntity.status(201).body(new ApiResponse<>(201, "created", bp));
    }

    @Operation(summary = "Add a point to an existing blueprint")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Blueprint updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    @PutMapping("/{author}/{bpname}/points")
    public ResponseEntity<ApiResponse<Blueprint>> addPoint(
            @PathVariable String author,
            @PathVariable String bpname,
            @Valid @RequestBody PointRequest p
    ) throws BlueprintNotFoundException {
        services.addPoint(author, bpname, p.x(), p.y());
        Blueprint updated = services.getBlueprint(author, bpname);
        return ResponseEntity.status(202).body(new ApiResponse<>(202, "accepted", updated));
    }

    public record NewBlueprintRequest(
            @NotBlank String author,
            @NotBlank String name,
            @NotNull @Valid List<Point> points
    ) { }

    public record PointRequest(@NotNull Integer x, @NotNull Integer y) { }
}
