-- B 模块 · 暂时隐藏「外部视频管理」菜单
--
-- 背景：外部视频模块暂缓开发，内容管理下的入口需从侧边栏整体收起。
-- 范围：menu_id 2012「外部视频管理」目录，及其子页 2018「渠道管理」、2019「视频内容管理」；
--       2027/2028/2029 是早先应并入 2019 的残留菜单，一并隐藏。
-- 方式：visible = '1'（隐藏但保留菜单、路由组件与按钮权限），后续恢复时改回 '0' 即可，无需重建菜单。
-- 幂等：重复执行无副作用。

UPDATE sys_menu
   SET visible = '1',
       update_by = 'b-migration',
       update_time = now()
 WHERE menu_id IN (2012, 2018, 2019, 2027, 2028, 2029);

-- 闸门：三个菜单均已隐藏，且 2012 下不存在仍可见的子菜单
SET @fail := (
  SELECT COUNT(*) FROM sys_menu
   WHERE menu_id IN (2012, 2018, 2019, 2027, 2028, 2029)
     AND visible <> '1'
);
-- 子项只校验目录/菜单（menu_type M/C），按钮权限（F）的 visible 恒为 '0'，不参与判断
SET @fail := @fail + (
  SELECT COUNT(*) FROM sys_menu
   WHERE parent_id = 2012
     AND menu_type IN ('M', 'C')
     AND visible = '0'
);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
