package com.firstapi.backend.service;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.ModelPricingItem;
import com.firstapi.backend.model.PricingStatus;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import com.firstapi.backend.repository.RelayRecordRepository;
import com.firstapi.backend.util.TimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class RelayRecordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelayRecordService.class);

    private final RelayRecordRepository relayRecordRepository;
    private final CostCalculationService costService;

    public RelayRecordService(RelayRecordRepository relayRecordRepository,
                              CostCalculationService costService) {
        this.relayRecordRepository = relayRecordRepository;
        this.costService = costService;
    }

    public void record(ApiKeyItem apiKey, RelayRoute route, RelayResult result,
                       String model, GroupItem group) {
        // 解析分组倍率
        BigDecimal groupRatio = null;
        if (group != null && group.getRate() != null && !group.getRate().isBlank()) {
            try {
                groupRatio = new BigDecimal(group.getRate().trim());
                if (groupRatio.compareTo(BigDecimal.ZERO) <= 0) {
                    LOGGER.warn("分组倍率非正数，视为无倍率: groupId={}, rate={}", group.getId(), group.getRate());
                    groupRatio = null;
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("分组倍率格式非法，视为无倍率: groupId={}, rate={}", group.getId(), group.getRate());
            }
        }

        ModelPricingItem pricing = costService.matchPricing(model);
        boolean usageMissing = result.getPromptTokens() == null || result.getCompletionTokens() == null;

        PricingStatus pricingStatus;
        if (usageMissing) {
            pricingStatus = PricingStatus.USAGE_MISSING;
        } else if (pricing == null) {
            pricingStatus = PricingStatus.NOT_FOUND;
        } else {
            pricingStatus = PricingStatus.MATCHED;
        }

        boolean pricingFound = pricing != null;

        RelayRecordItem item = new RelayRecordItem();
        item.setOwnerId(apiKey.getOwnerId());
        item.setApiKeyId(apiKey.getId());
        item.setProvider(route.getProvider());
        item.setAccountId(result.getAccountId());
        item.setModel(model);
        item.setRequestId(result.getRequestId());
        item.setSuccess(result.isSuccess());
        item.setStatusCode(result.getStatusCode());
        item.setLatencyMs(result.getLatencyMs());
        item.setPromptTokens(result.getPromptTokens());
        item.setCompletionTokens(result.getCompletionTokens());
        item.setTotalTokens(result.getTotalTokens());
        item.setCreatedAt(TimeSupport.nowDateTime());
        item.setCreatedAtTs(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        if (!result.isSuccess()) {
            item.setErrorText(result.getBody());
        }

        // 定价快照
        item.setInputPrice(pricing != null ? pricing.getInputPrice() : null);
        item.setOutputPrice(pricing != null ? pricing.getOutputPrice() : null);
        item.setPricingCurrency(pricing != null ? pricing.getCurrency() : null);
        item.setPricingRuleId(pricing != null ? pricing.getId() : null);
        item.setPricingRuleName(pricing != null ? pricing.getModelName() : null);
        item.setPricingStatus(pricingStatus.name());
        item.setPricingFound(pricingFound);
        item.setGroupRatio(groupRatio);
        item.setCost(pricingStatus == PricingStatus.MATCHED
                ? costService.calculate(pricing.getInputPrice(), pricing.getOutputPrice(),
                        result.getPromptTokens(), result.getCompletionTokens(), groupRatio)
                : null);

        // usage_json 快照
        item.setUsageJson(result.getUsageJson());

        relayRecordRepository.save(item);
    }
}
