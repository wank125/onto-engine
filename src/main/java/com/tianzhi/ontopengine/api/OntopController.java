package com.tianzhi.ontopengine.api;

import com.tianzhi.ontopengine.model.BootstrapRequest;
import com.tianzhi.ontopengine.model.BootstrapResponse;
import com.tianzhi.ontopengine.model.ExtractMetadataRequest;
import com.tianzhi.ontopengine.model.ExtractMetadataResponse;
import com.tianzhi.ontopengine.model.ParseMappingRequest;
import com.tianzhi.ontopengine.model.ParseMappingResponse;
import com.tianzhi.ontopengine.model.ValidateRequest;
import com.tianzhi.ontopengine.model.ValidateResponse;
import com.tianzhi.ontopengine.service.OntopEngineService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@Validated
@RequestMapping("/api/ontop")
public class OntopController {

    private final OntopEngineService ontopEngineService;

    public OntopController(OntopEngineService ontopEngineService) {
        this.ontopEngineService = ontopEngineService;
    }

    /**
     * 异步提取数据库元数据，防止耗尽 Web 容器主线程
     */
    @Async("ontopTaskExecutor")
    @PostMapping("/extract-metadata")
    public CompletableFuture<ExtractMetadataResponse> extractMetadata(@RequestBody ExtractMetadataRequest request) throws Exception {
        return CompletableFuture.completedFuture(ontopEngineService.extractMetadata(request));
    }

    /**
     * 异步 Bootstrap 操作
     */
    @Async("ontopTaskExecutor")
    @PostMapping("/bootstrap")
    public CompletableFuture<BootstrapResponse> bootstrap(@RequestBody BootstrapRequest request) throws Exception {
        return CompletableFuture.completedFuture(ontopEngineService.bootstrap(request));
    }

    /**
     * 快速的校验可以使用原生的同步机制
     */
    @PostMapping("/validate")
    public ValidateResponse validate(@RequestBody ValidateRequest request) throws Exception {
        return ontopEngineService.validate(request);
    }

    @PostMapping("/parse-mapping")
    public ParseMappingResponse parseMapping(@RequestBody ParseMappingRequest request) throws Exception {
        return ontopEngineService.parseMapping(request);
    }
}
