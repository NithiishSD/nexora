package com.nexora.settings.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.settings.api.dto.InitializeSetupRequest;
import com.nexora.settings.api.dto.SetupStatusResponse;
import com.nexora.settings.application.SetupService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public SetupStatusResponse getStatus() {
        return new SetupStatusResponse(setupService.isInitialized(), setupService.getBusinessName());
    }

    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> initialize(@Valid @RequestBody InitializeSetupRequest request) {
        setupService.initializeSystem(
                request.username(),
                request.password(),
                request.businessName(),
                request.phone(),
                request.city()
        );
        return Map.of("success", true, "message", "System initialized successfully");
    }
}
