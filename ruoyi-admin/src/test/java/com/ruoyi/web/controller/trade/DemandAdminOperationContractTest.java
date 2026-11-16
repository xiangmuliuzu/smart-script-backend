package com.ruoyi.web.controller.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 任务发布/验收收归管理端后的接口契约回归。
 *
 * <p>用户侧发布与买方动作已全部下线（见 AppTradeDemandController 的 403 响应），管理端成为唯一入口，
 * 因此这里锁两条最容易在后续迭代里松掉的口径：</p>
 * <ul>
 *   <li>任务域每个管理端端点都必须带 {@code trade:demand:*} 权限位——漏一个就等于把运营动作开放给任意登录账号；</li>
 *   <li>发布请求体 {@code {demand:{...}, stages:[...]}} 能被直接绑定——轮次与任务同体提交，
 *       一旦绑定方式改动而前端未跟，运营建单会静默丢掉轮次。</li>
 * </ul>
 */
class DemandAdminOperationContractTest
{
    /** 任务域管理端端点（/trade/demand/** 与 /trade/demand/{id}/... 系列）。 */
    private List<Method> demandHandlers()
    {
        List<Method> methods = new ArrayList<>();
        for (Method method : TradeController.class.getDeclaredMethods())
        {
            String path = mappedDemandPath(method);
            if (path != null)
            {
                methods.add(method);
            }
        }
        return methods;
    }

    private String mappedDemandPath(Method method)
    {
        GetMapping get = method.getAnnotation(GetMapping.class);
        PostMapping post = method.getAnnotation(PostMapping.class);
        PutMapping put = method.getAnnotation(PutMapping.class);
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        String[] paths = get != null ? get.value() : post != null ? post.value()
                : put != null ? put.value() : delete != null ? delete.value() : null;
        if (paths == null)
        {
            return null;
        }
        for (String path : paths)
        {
            if (path.startsWith("/trade/demand"))
            {
                return path;
            }
        }
        return null;
    }

    @Test
    void everyDemandEndpointCarriesADemandPermission()
    {
        List<Method> handlers = demandHandlers();
        assertTrue(handlers.size() >= 10,
                "任务域管理端端点数量异常（发布/编辑/关闭/验收/延时/只读视图都在其中），实际=" + handlers.size());

        for (Method method : handlers)
        {
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            assertNotNull(preAuthorize, method.getName() + " 缺少 @PreAuthorize，任务域不允许裸端点");
            assertTrue(preAuthorize.value().contains("trade:demand:"),
                    method.getName() + " 权限位必须属于 trade:demand:*，实际=" + preAuthorize.value());
        }
    }

    /** 逐轮验收是放款触发点，权限位必须与编辑/关闭分开，便于只给运营岗开审批不给改单。 */
    @Test
    void stageReviewUsesItsOwnPermission()
    {
        List<String> reviewPerms = new ArrayList<>();
        for (Method method : demandHandlers())
        {
            if ("/trade/demand/{demandId}/stage/{stageId}/review".equals(mappedDemandPath(method)))
            {
                reviewPerms.add(method.getAnnotation(PreAuthorize.class).value());
            }
        }
        assertEquals(List.of("@ss.hasPermi('trade:demand:review')"), reviewPerms);
    }

    @Test
    void publishFormBindsDemandAndRoundsTogether() throws Exception
    {
        String json = "{\"demand\":{\"title\":\"30 集分镜\",\"budget\":6000.00,\"totalEpisodes\":30,"
                + "\"taskType\":\"storyboard\",\"durationHours\":720},"
                + "\"stages\":[{\"stageOrder\":1,\"episodeTo\":10,\"amount\":2000.00,\"durationHours\":240},"
                + "{\"stageOrder\":2,\"episodeTo\":30,\"amount\":4000.00,\"durationHours\":480}]}";

        TradeController.DemandForm form = new ObjectMapper().readValue(json, TradeController.DemandForm.class);

        assertNotNull(form.demand);
        assertEquals("30 集分镜", form.demand.getTitle());
        assertEquals(0, form.demand.getBudget().compareTo(new java.math.BigDecimal("6000.00")));
        assertNotNull(form.stages);
        assertEquals(2, form.stages.size());
        assertEquals(Integer.valueOf(30), form.stages.get(1).getEpisodeTo());
        assertEquals(0, form.stages.get(1).getAmount().compareTo(new java.math.BigDecimal("4000.00")));
    }
}
