package com.firstapi.backend.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.model.ModelPricingItem;
import com.firstapi.backend.repository.ModelPricingRepository;
import com.firstapi.backend.repository.RelayRecordRepository;
import com.firstapi.backend.service.AccountService;
import com.firstapi.backend.service.CostCalculationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/model-pricing")
public class ModelPricingController {

    private final ModelPricingRepository repository;
    private final RelayRecordRepository relayRecordRepository;
    private final AccountService accountService;
    private final CostCalculationService costService;

    public ModelPricingController(ModelPricingRepository repository,
                                  RelayRecordRepository relayRecordRepository,
                                  AccountService accountService,
                                  CostCalculationService costService) {
        this.repository = repository;
        this.relayRecordRepository = relayRecordRepository;
        this.accountService = accountService;
        this.costService = costService;
    }

    @GetMapping
    public ApiResponse<List<ModelPricingItem>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(repository.findAllForAdmin(keyword));
    }

    @GetMapping("/models")
    public ApiResponse<List<String>> availableModels() {
        Set<String> models = new LinkedHashSet<>(relayRecordRepository.distinctModels());
        for (ModelPricingItem item : repository.findAllForAdmin(null)) {
            if (!"default".equals(item.getMatchType()) && item.getModelName() != null) {
                models.add(item.getModelName());
            }
        }
        return ApiResponse.ok(List.copyOf(models));
    }

    @GetMapping("/models/upstream")
    public ApiResponse<List<String>> upstreamModels() {
        return ApiResponse.ok(accountService.fetchUpstreamModels());
    }

    @PostMapping
    public ApiResponse<ModelPricingItem> create(@RequestBody Request req) {
        ModelPricingItem item = buildItem(null, req);
        repository.save(item);
        costService.refreshCache();
        return ApiResponse.ok(item);
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelPricingItem> update(@PathVariable Long id, @RequestBody Request req) {
        ModelPricingItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        ModelPricingItem item = buildItem(id, req);
        item.setCreatedAt(existing.getCreatedAt());
        repository.update(id, item);
        costService.refreshCache();
        return ApiResponse.ok(item);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        ModelPricingItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        repository.deleteById(id);
        costService.refreshCache();
        return ApiResponse.ok(true);
    }

    private ModelPricingItem buildItem(Long id, Request req) {
        ModelPricingItem item = new ModelPricingItem();
        if (id != null) item.setId(id);
        item.setModelName(req.modelName);
        item.setMatchType(req.matchType != null ? req.matchType : "exact");
        item.setProvider(req.provider != null ? req.provider : inferProvider(req.modelName));
        item.setInputPrice(req.inputPrice);
        item.setOutputPrice(req.outputPrice);
        item.setCurrency(req.currency != null ? req.currency : "CNY");
        item.setEnabled(req.enabled != null ? req.enabled : true);
        item.setEffectiveFrom(req.effectiveFrom != null ? req.effectiveFrom
                : LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (id == null) {
            item.setCreatedAt(now);
        }
        item.setUpdatedAt(now);
        return item;
    }

    static String inferProvider(String modelName) {
        if (modelName == null) return "Other";
        String lower = modelName.toLowerCase();
        if (lower.startsWith("gpt-") || lower.startsWith("o1") || lower.startsWith("o3")
                || lower.startsWith("o4") || lower.startsWith("codex") || lower.startsWith("gpt-image")) {
            return "OpenAI";
        }
        if (lower.startsWith("claude-")) return "Anthropic";
        if (lower.startsWith("gemini-")) return "Google";
        if (lower.startsWith("deepseek-")) return "DeepSeek";
        if (lower.startsWith("grok-")) return "xAI";
        if (lower.startsWith("llama-")) return "Meta";
        return "Other";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        public String modelName;
        public String matchType;
        public String provider;
        public BigDecimal inputPrice;
        public BigDecimal outputPrice;
        public String currency;
        public Boolean enabled;
        public LocalDateTime effectiveFrom;
    }
}
