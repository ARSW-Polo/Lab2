package edu.eci.arsw.blueprints.api;

public record ApiResponse<T>(int code, String message, T data) {
}
