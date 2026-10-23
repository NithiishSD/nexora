package com.nexora.shared.error;

// Thrown when an id or business number does not exist. Maps to 404 RESOURCE_NOT_FOUND.
// Usage: throw new ResourceNotFoundException("Party", id);
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + id);
    }
}
