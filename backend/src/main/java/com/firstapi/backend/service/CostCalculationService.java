package com.firstapi.backend.service;

import com.firstapi.backend.model.ModelPricingItem;
import com.firstapi.backend.repository.ModelPricingRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static java.math.RoundingMode.HALF_UP;

@Service
public class CostCalculationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CostCalculationService.class);
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final ModelPricingRepository pricingRepository;
    private volatile List<ModelPricingItem> pricingCache = Collections.emptyList();

    public CostCalculationService(ModelPricingRepository pricingRepository) {
        this.pricingRepository = pricingRepository;
    }

    @PostConstruct
    public void refreshCache() {
        try {
            this.pricingCache = pricingRepository.findAllEnabledEffective();
        } catch (Exception e) {
            LOGGER.warn("Failed to load pricing cache", e);
        }
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void scheduledRefresh() {
        refreshCache();
    }

    /**
     * 计算费用（CNY）。先完成全部乘加运算，最后统一除以 1M，避免多次舍入误差。
     */
    public BigDecimal calculate(BigDecimal inputPrice, BigDecimal outputPrice,
                                Integer promptTokens, Integer completionTokens,
                                BigDecimal groupRatio) {
        if (inputPrice == null || outputPrice == null) {
            return null;
        }
        if (promptTokens == null || completionTokens == null) {
            return null;
        }

        BigDecimal raw = BigDecimal.valueOf(promptTokens).multiply(inputPrice)
                .add(BigDecimal.valueOf(completionTokens).multiply(outputPrice));

        BigDecimal cost = raw.divide(ONE_MILLION, 10, HALF_UP);

        if (groupRatio != null) {
            cost = cost.multiply(groupRatio).setScale(10, HALF_UP);
        }
        return cost;
    }

    /**
     * 根据模型名推断平台。
     */
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

    /**
     * 匹配定价规则（从内存缓存查找）。
     * 优先级：精确匹配 > 前缀匹配（最长前缀） > 同平台兜底规则。
     * 同级多条取 effective_from 最近的。
     */
    public ModelPricingItem matchPricing(String requestModel) {
        if (requestModel == null || pricingCache == null) return null;

        String requestProvider = inferProvider(requestModel);

        ModelPricingItem exactMatch = null;
        ModelPricingItem prefixMatch = null;
        ModelPricingItem defaultMatch = null;
        int longestPrefix = -1;

        for (ModelPricingItem item : pricingCache) {
            String type = item.getMatchType();
            String name = item.getModelName();

            if ("exact".equals(type) && name != null && name.equals(requestModel)) {
                if (exactMatch == null || item.getEffectiveFrom().isAfter(exactMatch.getEffectiveFrom())) {
                    exactMatch = item;
                }
            }

            if ("prefix".equals(type) && name != null && requestModel.startsWith(name)) {
                if (name.length() > longestPrefix
                        || (name.length() == longestPrefix
                        && prefixMatch != null
                        && item.getEffectiveFrom().isAfter(prefixMatch.getEffectiveFrom()))) {
                    longestPrefix = name.length();
                    prefixMatch = item;
                }
            }

            if ("default".equals(type) && requestProvider.equals(item.getProvider())) {
                if (defaultMatch == null || item.getEffectiveFrom().isAfter(defaultMatch.getEffectiveFrom())) {
                    defaultMatch = item;
                }
            }
        }

        if (exactMatch != null) return exactMatch;
        if (prefixMatch != null) return prefixMatch;
        return defaultMatch;
    }
}
