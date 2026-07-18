package com.baseerah.api.client;

import com.baseerah.api.client.dto.ClientDto;
import com.baseerah.application.client.ClientService;
import com.baseerah.shared.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client endpoints (DESIGN.md §6). Thin: delegates to {@link ClientService} and wraps the result in the
 * shared {@link ApiResponse} envelope — no business logic here.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** {@code GET /api/v1/clients} — the seeded personas. */
    @GetMapping
    public ApiResponse<List<ClientDto>> list() {
        return ApiResponse.ok(clientService.list());
    }
}
