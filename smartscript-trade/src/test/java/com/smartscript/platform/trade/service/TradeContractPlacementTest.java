package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 上传件签章定位 JSON 契约单测：前端摆放层与后端落章共用同一份字段表，
 * rot 一旦被白名单漏掉，表现是「预览里转了角度、签完的 PDF 又是正的」，
 * 而且只在落章阶段暴露，很难归因，所以在这里把字段与越界口径钉住。
 */
class TradeContractPlacementTest
{
    @Test
    void keepsRotationWhenProvided()
    {
        String json = "[{\"kind\":\"seal\",\"page\":0,\"x\":0.2,\"y\":0.3,\"w\":0.4,\"rot\":-35.5}]";
        assertTrue(TradeContractService.normalizePlacements(json, 3).contains("\"rot\":-35.5"));
    }

    @Test
    void defaultsLegacyPlacementWithoutRotationToZero()
    {
        String json = "[{\"kind\":\"signature\",\"page\":1,\"x\":0.1,\"y\":0.5,\"w\":0.2}]";
        assertTrue(TradeContractService.normalizePlacements(json, 3).contains("\"rot\":0.0"));
    }

    @Test
    void rejectsRotationBeyondHalfTurn()
    {
        String json = "[{\"kind\":\"seal\",\"page\":0,\"x\":0.2,\"y\":0.3,\"w\":0.4,\"rot\":181}]";
        assertThrows(IllegalArgumentException.class,
                () -> TradeContractService.normalizePlacements(json, 3));
    }

    @Test
    void stillRejectsPlacementWiderThanPage()
    {
        String json = "[{\"kind\":\"seal\",\"page\":0,\"x\":0.8,\"y\":0.3,\"w\":0.4,\"rot\":10}]";
        assertThrows(IllegalArgumentException.class,
                () -> TradeContractService.normalizePlacements(json, 3));
    }
}
