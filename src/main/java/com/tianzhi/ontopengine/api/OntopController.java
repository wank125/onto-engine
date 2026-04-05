package com.tianzhi.ontopengine.api;

import com.tianzhi.ontopengine.model.BootstrapRequest;
import com.tianzhi.ontopengine.model.BootstrapResponse;
import com.tianzhi.ontopengine.model.ExtractMetadataRequest;
import com.tianzhi.ontopengine.model.ExtractMetadataResponse;
import com.tianzhi.ontopengine.model.ValidateRequest;
import com.tianzhi.ontopengine.model.ValidateResponse;
import com.tianzhi.ontopengine.service.OntopEngineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ontop")
public class OntopController {

    private final OntopEngineService ontopEngineService;

    public OntopController(OntopEngineService ontopEngineService) {
        this.ontopEngineService = ontopEngineService;
    }

    @PostMapping("/extract-metadata")
    public ExtractMetadataResponse extractMetadata(@RequestBody ExtractMetadataRequest request) {
        return ontopEngineService.extractMetadata(request);
    }

    @PostMapping("/bootstrap")
    public BootstrapResponse bootstrap(@RequestBody BootstrapRequest request) {
        return ontopEngineService.bootstrap(request);
    }

    @PostMapping("/validate")
    public ValidateResponse validate(@RequestBody ValidateRequest request) {
        return ontopEngineService.validate(request);
    }
}
