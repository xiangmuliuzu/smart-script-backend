package com.smartscript.platform.content.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;
import org.springframework.stereotype.Component;

/**
 * 版权登记费计价：按中心 config_json。
 * national_word 按作品字数分档；local_flat 一口价（默认 50）。
 */
@Component
public class CopyrightFeeCalculator
{
    private static final BigDecimal DEFAULT_LOCAL_FLAT = new BigDecimal("50.00");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String FEE_TYPE_NATIONAL_WORD = "national_word";
    public static final String FEE_TYPE_LOCAL_FLAT = "local_flat";

    public FeeQuote quote(SysCopyrightCenterConfig center, int wordCount)
    {
        if (center == null)
        {
            throw new IllegalArgumentException("版权中心不可用");
        }
        if (wordCount < 0)
        {
            throw new IllegalArgumentException("作品字数无效");
        }

        FeeConfig config = parseConfig(center);
        BigDecimal fee;
        if (FEE_TYPE_LOCAL_FLAT.equals(config.feeType))
        {
            fee = config.flatFee;
        }
        else
        {
            fee = resolveNationalWordFee(wordCount, config);
        }
        fee = fee.setScale(2, RoundingMode.HALF_UP);
        if (fee.signum() <= 0)
        {
            throw new IllegalStateException("登记费配置无效");
        }
        return new FeeQuote(config.feeType, wordCount, fee);
    }

    private BigDecimal resolveNationalWordFee(int wordCount, FeeConfig config)
    {
        if (config.tiers != null && config.tiers.isArray() && config.tiers.size() > 0)
        {
            Iterator<JsonNode> it = config.tiers.elements();
            while (it.hasNext())
            {
                JsonNode tier = it.next();
                JsonNode maxNode = tier.get("max");
                BigDecimal tierFee = decimal(tier.get("fee"), null);
                if (tierFee == null)
                {
                    continue;
                }
                if (maxNode == null || maxNode.isNull())
                {
                    return tierFee;
                }
                int max = maxNode.asInt();
                if (wordCount <= max)
                {
                    return tierFee;
                }
            }
        }
        // 内置国版默认档
        if (wordCount <= 100)
        {
            return new BigDecimal("100.00");
        }
        if (wordCount <= 5000)
        {
            return new BigDecimal("150.00");
        }
        if (wordCount <= 10000)
        {
            return new BigDecimal("200.00");
        }
        return new BigDecimal("300.00");
    }

    private FeeConfig parseConfig(SysCopyrightCenterConfig center)
    {
        FeeConfig fallback = inferFallback(center);
        String json = center.getConfigJson();
        if (json == null || json.isBlank())
        {
            return fallback;
        }
        try
        {
            JsonNode root = MAPPER.readTree(json);
            String feeType = text(root.get("feeType"), fallback.feeType);
            BigDecimal flat = decimal(root.get("flatFee"), fallback.flatFee);
            JsonNode tiers = root.get("wordTiers");
            return new FeeConfig(feeType, flat, tiers);
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private FeeConfig inferFallback(SysCopyrightCenterConfig center)
    {
        String code = center.getCenterCode() == null ? "" : center.getCenterCode().toLowerCase();
        String name = center.getCenterName() == null ? "" : center.getCenterName();
        boolean local = code.contains("local")
                || name.contains("地方")
                || name.contains("贵州")
                || name.contains("穿山甲");
        if (local)
        {
            return new FeeConfig(FEE_TYPE_LOCAL_FLAT, DEFAULT_LOCAL_FLAT, null);
        }
        return new FeeConfig(FEE_TYPE_NATIONAL_WORD, DEFAULT_LOCAL_FLAT, null);
    }

    private static String text(JsonNode node, String defaultValue)
    {
        if (node == null || node.isNull())
        {
            return defaultValue;
        }
        String v = node.asText();
        return v == null || v.isBlank() ? defaultValue : v;
    }

    private static BigDecimal decimal(JsonNode node, BigDecimal defaultValue)
    {
        if (node == null || node.isNull())
        {
            return defaultValue;
        }
        try
        {
            return new BigDecimal(node.asText()).setScale(2, RoundingMode.HALF_UP);
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }

    public static final class FeeQuote
    {
        private final String feeType;
        private final int wordCount;
        private final BigDecimal feeAmount;

        public FeeQuote(String feeType, int wordCount, BigDecimal feeAmount)
        {
            this.feeType = feeType;
            this.wordCount = wordCount;
            this.feeAmount = feeAmount;
        }

        public String getFeeType()
        {
            return feeType;
        }

        public int getWordCount()
        {
            return wordCount;
        }

        public BigDecimal getFeeAmount()
        {
            return feeAmount;
        }
    }

    private static final class FeeConfig
    {
        private final String feeType;
        private final BigDecimal flatFee;
        private final JsonNode tiers;

        private FeeConfig(String feeType, BigDecimal flatFee, JsonNode tiers)
        {
            this.feeType = feeType;
            this.flatFee = flatFee;
            this.tiers = tiers;
        }
    }
}
