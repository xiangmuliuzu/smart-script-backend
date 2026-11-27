-- =====================================================================
-- smartscript_full_init.sql —— 平台数据库全量初始化/增量升级（唯一入口）
-- =====================================================================
-- 用途：一个文件同时支持两种场景，可重复执行（幂等）：
--   1) 全新空库：完整导入若依基线 + 平台全部结构/菜单/权限/字典/任务。
--   2) 已有库（含历史版本建的库）：自动补齐缺失的表/列/索引/菜单/字典，
--      补齐缺失结构并保留业务数据；按已定稿规则清理退役菜单、更新配置并归一历史枚举。
--      例：第 1/2/7 部分纠正已退役的授权类型 negotiable → non_exclusive。
-- 建议通过 scripts/db/init-database.sh / .ps1 执行（内部按本清单跑这一个文件）；
-- 也可直接： mysql -h<host> -u<user> -p <库名> < sql/smartscript_full_init.sql
--
-- 结构：
--   第 0 部分  若依基线（仅当 sys_dept 不存在时导入，直接使用平台最终结构/种子）
--   第 1 部分  sys_user 加固与数据归一（原 A2 迁移）
--   第 2 部分  App/用户域 11 张表（原 A2 迁移 + A4-001 增量折入）
--   第 3 部分  sys_user.bio（A5）、H12 覆盖索引、sys_role.app_grantable（A4-003）
--   第 4 部分  产品/用户中心/内容/交易菜单（A1+PC+A4-002+B1+C 最终形态，upsert）
--   第 5 部分  角色与授权（A1 受限运营角色、超管授权）
--   第 6 部分  C 交易域 14 张现役业务表（含列补齐与索引收口；sys_selection 已退役）
--   第 7 部分  交易字典（14 类型 / 50 项）+ 7.1 授权类型枚举收敛归一
--   第 8 部分  询盘过期自动关闭定时任务 + 8.1 存量菜单清理
--   第 9 部分  公告接收范围与消息/公告业务权限
--   第 10 部分 用户消息入口迁移与角色权限继承
--   第 11 部分 移除用户中心会话列表入口，保留用户沟通权限
--   第 12 部分 App 用户名称唯一索引
--   第 13 部分 恢复系统管理目录、页面及权限
--   第 14 部分 版权域菜单修复（编码损坏、ID 串位、合同按钮对齐后端权限）
--   第 15 部分 现役表全量补齐（对齐线上 143 张表）
--   第 16 部分 现役触发器（4 个，DROP + CREATE 幂等）
--   第 17 部分 字典与系统配置权威同步
--   第 18 部分 菜单与角色授权权威同步（205 菜单 / 372 授权）
--   收口自检  所有结构、种子及存量清理完成后统一输出 SUMMARY（PASS/FAIL）
-- 与历史迁移脚本的差异：
--   - 原迁移的控制表/快照表（a2_/a4_ 前缀）为升级回滚记账服务，此处不需要，已省略；
--     历史迁移/补丁统一归档在 sql/_archive/，不参与本流程。
--   - 原 C 迁移交易按钮 menu_id 5160-5174 与 B1 内容按钮 5160-5166 冲突（静默漏插），
--     已将交易按钮重编号为 5180-5196。
--   - 第 0 部分由若依基线适配为平台最终状态；上游原文归档于 sql/_archive/upstream/ry_20260320.sql。
--   - 新库不插入随后会被删除的目录/页面和普通角色绑定；存量库仍保留清理逻辑。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 第 0 部分：若依基线（仅空库导入；已有库整体跳过，绝不 drop/覆盖）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS _ss_baseline_import;

DELIMITER $$
CREATE PROCEDURE _ss_baseline_import()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept') THEN
-- | ----------------------------
-- | 1、部门表
-- | ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- | ----------------------------
-- | 初始化-部门表数据
-- | ----------------------------
insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


-- | ----------------------------
-- | 2、用户信息表
-- | ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(20)     default '00'               comment '用户类型00/01/02/03',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(255)    default ''                 comment '密码BCrypt',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  bio               varchar(200)   not null default ''         comment '个人简介（A5 用户中心，空串表示未填写）',
  primary key (user_id),
  unique key uk_sys_user_user_name (user_name),
  unique key uk_sys_user_phonenumber (phonenumber),
  key idx_user_type_del_flag (user_type, del_flag)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- | ----------------------------
-- | 初始化-用户信息表数据
-- | ----------------------------
insert into sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark) values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark) values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试员');


-- | ----------------------------
-- | 3、岗位信息表
-- | ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- | ----------------------------
-- | 初始化-岗位信息表数据
-- | ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


-- | ----------------------------
-- | 4、角色信息表
-- | ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  app_grantable        tinyint(1)      not null default 0         comment '是否可授予 App 用户（A4 权威来源；1=可授予，0=拒绝）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- | ----------------------------
-- | 初始化-角色信息表数据
-- | ----------------------------
insert into sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, update_by, update_time, remark) values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, update_by, update_time, remark) values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


-- | ----------------------------
-- | 5、菜单权限表
-- | ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- | ----------------------------
-- | 初始化-菜单信息表数据
-- | ----------------------------
-- | 平台若依菜单（系统管理保留七个管理页面；日志与公告使用当前平台目录）
-- | 一级菜单
insert into sys_menu values('1', '系统管理', '0', '90', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('2', '系统监控', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '1', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '1', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '4', 'http://ruoyi.vip', null, '', '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', sysdate(), '', null, '若依官网地址');
-- | 二级菜单
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '1', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '1', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '1', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '1', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '1', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '1', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '1', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '1', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '1', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- | 日志页面直接归属数据统计目录（第 4 部分创建 5002）
insert into sys_menu values('500',  '操作日志', '5002', '99', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '5002', '100', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- | 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- | 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- | 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- | 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- | 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- | 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- | 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- | 通知公告按钮




-- | 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- | 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- | 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- | 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- | 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');


-- | ----------------------------
-- | 6、用户和角色关联表  用户N-1角色
-- | ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- | ----------------------------
-- | 初始化-用户和角色关联表数据
-- | ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


-- | ----------------------------
-- | 7、角色和菜单关联表  角色1-N菜单
-- | ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- | ----------------------------
-- | 初始化-角色和菜单关联表数据（仅插入最终保留的普通角色授权）
-- | ----------------------------
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');
insert into sys_role_menu values ('2', '5002');

-- | ----------------------------
-- | 8、角色和部门关联表  角色1-N部门
-- | ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- | ----------------------------
-- | 初始化-角色和部门关联表数据
-- | ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');


-- | ----------------------------
-- | 9、用户与岗位关联表  用户1-N岗位
-- | ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- | ----------------------------
-- | 初始化-用户与岗位关联表数据
-- | ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


-- | ----------------------------
-- | 10、操作日志记录
-- | ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- | ----------------------------
-- | 11、字典类型表
-- | ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- | ----------------------------
-- | 12、字典数据表
-- | ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- | ----------------------------
-- | 13、参数配置表
-- | ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';



-- | ----------------------------
-- | 14、系统访问记录
-- | ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- | ----------------------------
-- | 15、定时任务调度表
-- | ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- | ----------------------------
-- | 16、定时任务调度日志表
-- | ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  start_time          datetime                                  comment '执行开始时间',
  end_time            datetime                                  comment '执行结束时间',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- | ----------------------------
-- | 17、通知公告表
-- | ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  audience          varchar(8)     not null default 'ADMIN'    comment 'USER普通用户/ADMIN后台管理员/ALL全部',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id),
  key idx_notice_status_audience (status, audience, notice_id),
  constraint ck_sys_notice_audience check (audience IN ('USER','ADMIN','ALL'))
) engine=innodb auto_increment=10 comment = '通知公告表';

-- | ----------------------------
-- | 初始化-公告信息表数据
-- | ----------------------------
insert into sys_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark) values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark) values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark) values('3', '若依开源框架介绍', '1', '<p><span style=\"color: rgb(230, 0, 0);\">项目介绍</span></p><p><font color=\"#333333\">RuoYi开源项目是为企业用户定制的后台脚手架框架，为企业打造的一站式解决方案，降低企业开发成本，提升开发效率。主要包括用户管理、角色管理、部门管理、菜单管理、参数管理、字典管理、</font><span style=\"color: rgb(51, 51, 51);\">岗位管理</span><span style=\"color: rgb(51, 51, 51);\">、定时任务</span><span style=\"color: rgb(51, 51, 51);\">、</span><span style=\"color: rgb(51, 51, 51);\">服务监控、登录日志、操作日志、代码生成等功能。其中，还支持多数据源、数据权限、国际化、Redis缓存、Docker部署、滑动验证码、第三方认证登录、分布式事务、</span><font color=\"#333333\">分布式文件存储</font><span style=\"color: rgb(51, 51, 51);\">、分库分表处理等技术特点。</span></p><p><img src=\"https://foruda.gitee.com/images/1773931848342439032/a4d22313_1815095.png\" style=\"width: 64px;\"><br></p><p><span style=\"color: rgb(230, 0, 0);\">官网及演示</span></p><p><span style=\"color: rgb(51, 51, 51);\">若依官网地址：&nbsp;</span><a href=\"http://ruoyi.vip\" target=\"_blank\">http://ruoyi.vip</a><a href=\"http://ruoyi.vip\" target=\"_blank\"></a></p><p><span style=\"color: rgb(51, 51, 51);\">若依文档地址：&nbsp;</span><a href=\"http://doc.ruoyi.vip\" target=\"_blank\">http://doc.ruoyi.vip</a><br></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【不分离版】：&nbsp;</span><a href=\"http://demo.ruoyi.vip\" target=\"_blank\">http://demo.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【分离版本】：&nbsp;</span><a href=\"http://vue.ruoyi.vip\" target=\"_blank\">http://vue.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【微服务版】：&nbsp;</span><a href=\"http://cloud.ruoyi.vip\" target=\"_blank\">http://cloud.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【移动端版】：&nbsp;</span><a href=\"http://h5.ruoyi.vip\" target=\"_blank\">http://h5.ruoyi.vip</a></p><p><br style=\"color: rgb(48, 49, 51); font-family: &quot;Helvetica Neue&quot;, Helvetica, Arial, sans-serif; font-size: 12px;\"></p>', '0', 'admin', sysdate(), '', null, '管理员');


-- | ----------------------------
-- | 18、公告已读记录表
-- | ----------------------------
drop table if exists sys_notice_read;
create table sys_notice_read (
  read_id          bigint(20)       not null auto_increment    comment '已读主键',
  notice_id        int(4)           not null                   comment '公告id',
  user_id          bigint(20)       not null                   comment '用户id',
  read_time        datetime         not null                   comment '阅读时间',
  primary key (read_id),
  unique key uk_user_notice (user_id, notice_id)   comment '同一用户同一公告只记录一次'
) engine=innodb auto_increment=1 comment='公告已读记录表';


-- | ----------------------------
-- | 19、代码生成业务表
-- | ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- | ----------------------------
-- | 20、代码生成业务表字段
-- | ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';  END IF;
END$$
DELIMITER ;

CALL _ss_baseline_import();
DROP PROCEDURE _ss_baseline_import;

-- =====================================================================
-- 第 1 部分：sys_user 兼容加固 + 数据归一（幂等，可重复执行）
-- =====================================================================

-- 1.1 存量数据归一（空手机号转 NULL 必须先于手机号唯一键）
UPDATE sys_user SET phonenumber = NULL WHERE phonenumber IS NOT NULL AND TRIM(phonenumber) = '';
UPDATE sys_user SET user_type = '00' WHERE user_type = 'admin';
UPDATE sys_user SET user_type = '01' WHERE user_type IN ('user','app');
UPDATE sys_user SET user_type = '02' WHERE user_type IN ('creator','author');
UPDATE sys_user SET user_type = '03' WHERE user_type IN ('client','customer');
UPDATE sys_user SET user_type = '00' WHERE user_type IS NULL OR TRIM(user_type) = '';
UPDATE sys_user SET status = '0' WHERE status IS NULL OR status NOT IN ('0','1');
UPDATE sys_user SET del_flag = '2' WHERE del_flag = '1';
UPDATE sys_user SET del_flag = '0' WHERE del_flag IS NULL OR TRIM(del_flag) = '';
UPDATE sys_user SET nick_name = user_name WHERE nick_name IS NULL OR TRIM(nick_name) = '';
UPDATE sys_user SET login_ip = '' WHERE login_ip IS NULL;
UPDATE sys_user SET email = '' WHERE email IS NULL;
UPDATE sys_user SET avatar = '' WHERE avatar IS NULL;
UPDATE sys_user SET remark = IFNULL(remark, '');

-- 1.2 列宽加固（探测后变更）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'password' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 255
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user MODIFY COLUMN password varchar(255) DEFAULT '''' COMMENT ''密码BCrypt''',
  'SELECT ''password col ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ut_len := (
  SELECT IFNULL(CHARACTER_MAXIMUM_LENGTH, 0) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'user_type'
);
SET @ddl := IF(@ut_len < 20,
  'ALTER TABLE sys_user MODIFY COLUMN user_type varchar(20) DEFAULT ''00'' COMMENT ''用户类型00/01/02/03''',
  'SELECT ''user_type col ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 唯一键（探测后新增）
SET @uk_user := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'uk_sys_user_user_name'
);
SET @ddl := IF(@uk_user = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_user_name (user_name)',
  'SELECT ''uk user_name ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @uk_phone := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'uk_sys_user_phonenumber'
);
SET @ddl := IF(@uk_phone = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_phonenumber (phonenumber)',
  'SELECT ''uk phonenumber ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 第 2 部分：App / 用户域 11 张表（含 A4 增量列，幂等建表）
-- =====================================================================
CREATE TABLE IF NOT EXISTS app_sms_code (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  phone           VARCHAR(64)   NOT NULL COMMENT '手机号或邮箱验证码标识',
  scene           VARCHAR(32)   NOT NULL,
  code_hash       VARCHAR(64)   NOT NULL,
  request_ip      VARCHAR(45)   NULL,
  failed_attempts INT           NOT NULL DEFAULT 0,
  used_at         DATETIME      NULL,
  expires_at      DATETIME      NOT NULL,
  create_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64)   NOT NULL DEFAULT '',
  update_time     DATETIME      NULL,
  remark          VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_app_sms_phone_scene_created (phone, scene, create_time),
  KEY idx_app_sms_ip_created (request_ip, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码';

CREATE TABLE IF NOT EXISTS app_user_email (
  user_id BIGINT NOT NULL,
  email VARCHAR(50) NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱注册账号唯一绑定';

CREATE TABLE IF NOT EXISTS app_refresh_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  family_id VARCHAR(64) NOT NULL,
  device_id VARCHAR(64) NULL,
  device_name VARCHAR(64) NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  revoked_reason VARCHAR(64) NULL,
  replaced_by_id BIGINT NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_refresh_token_hash (token_hash),
  KEY idx_app_refresh_user_revoked (user_id, revoked_at),
  KEY idx_app_refresh_family (family_id),
  KEY idx_app_refresh_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 刷新令牌会话';

CREATE TABLE IF NOT EXISTS app_user_consent (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  agreement_type VARCHAR(32) NOT NULL,
  agreement_version VARCHAR(32) NOT NULL,
  accepted_at DATETIME NOT NULL,
  ip VARCHAR(45) NULL,
  device_id VARCHAR(64) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_app_consent_user_type (user_id, agreement_type, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 用户协议同意记录';

CREATE TABLE IF NOT EXISTS app_user_oauth (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  provider VARCHAR(16) NOT NULL,
  open_id VARCHAR(64) NOT NULL,
  union_id VARCHAR(64) NULL,
  unbound_at DATETIME NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_oauth_provider_open (provider, open_id),
  KEY idx_app_oauth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 第三方登录绑定';

CREATE TABLE IF NOT EXISTS `user_real_name_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `real_name_mask` varchar(64) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `id_number_mask` varchar(64) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `material_ref` varchar(255) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `auditor_id` bigint DEFAULT NULL,
  `audited_at` datetime DEFAULT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'system',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `material_back_ref` varchar(255) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `pending_real_name` varchar(64) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_realname_user` (`user_id`,`status`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='实名认证申请';

CREATE TABLE IF NOT EXISTS user_phone_change_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  old_phone_mask VARCHAR(32) NULL,
  new_phone_mask VARCHAR(32) NULL,
  result VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  client_ip VARCHAR(45) NULL,
  device_id VARCHAR(64) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_phone_change_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换绑手机号日志';

CREATE TABLE IF NOT EXISTS user_author_capability (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  operator_id BIGINT NULL,
  reason VARCHAR(255) NULL,
  operated_at DATETIME NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_author_capability_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者能力开关';

-- A4 用户中心：创作者资料只读投影（开发文档 §4.4/§5.2）。
-- 按 user_id 一对一；本次仅读取，维护来源待项目协作确认后接入。
CREATE TABLE IF NOT EXISTS user_creator_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  pen_name VARCHAR(64) NULL COMMENT '笔名；NULL/空表示未填写',
  specialties VARCHAR(500) NULL COMMENT '擅长创作类型代码，逗号分隔；NULL/空表示未填写',
  profile_intro VARCHAR(1000) NULL COMMENT '创作者介绍；NULL/空表示未填写',
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_creator_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者资料';

CREATE TABLE IF NOT EXISTS `user_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'A4 创建幂等键；历史行为 NULL',
  `type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `body` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `biz_ref` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `business_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'A4 业务类型；历史行 NULL',
  `business_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'A4 业务标识；历史行 NULL',
  `audience` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '交易通知受众: BUYER买方/SELLER卖方/BOTH双方; NULL=通用全端可见',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_notification_request_id` (`request_id`) USING BTREE,
  KEY `idx_user_notification_type_time` (`type`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户通知';

CREATE TABLE IF NOT EXISTS user_notification_receiver (
  id BIGINT NOT NULL AUTO_INCREMENT,
  notification_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_at DATETIME NULL,
  deleted_flag TINYINT(1) NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_receiver (notification_id, user_id),
  KEY idx_user_notification_receiver_user (user_id, deleted_flag, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知接收人';

CREATE TABLE IF NOT EXISTS user_notification_preference (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  channel VARCHAR(32) NOT NULL,
  type VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_pref (user_id, channel, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知偏好';

-- A5+ 用户个人黑名单：用户拉黑其他用户后，被拉黑方无法对其发起询盘/会话/消息与关注。
-- 唯一键保证重复拉黑幂等；关系由拉黑方删除记录解除。
CREATE TABLE IF NOT EXISTS sys_user_block (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '拉黑发起人 user_id',
  blocked_user_id BIGINT NOT NULL COMMENT '被拉黑的用户 user_id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NULL,
  update_by VARCHAR(64) NULL,
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_block_user (user_id, blocked_user_id),
  KEY idx_user_block_blocked (blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户个人黑名单';

CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  category VARCHAR(32) NOT NULL DEFAULT 'OTHER',
  content VARCHAR(2000) NOT NULL,
  attachment_ref VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/PROCESSING/REPLIED/CLOSED（A4 状态机）',
  reply VARCHAR(2000) NULL,
  handler_id BIGINT NULL,
  handled_at DATETIME NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_feedback_user (user_id, create_time),
  KEY idx_user_feedback_status (status, create_time),
  CONSTRAINT ck_user_feedback_status
    CHECK (status IN ('SUBMITTED','PROCESSING','REPLIED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈';

-- 2.1 A4 反馈状态归一与默认值/约束收口（幂等；针对未执行过 A4-001 的存量库）
UPDATE user_feedback SET status = 'SUBMITTED' WHERE status = 'OPEN';

SET @default_now := (
  SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
    AND COLUMN_NAME = 'status'
);
SET @ddl := IF(@default_now IS NULL OR UPPER(@default_now) <> 'SUBMITTED',
  'ALTER TABLE user_feedback MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT ''SUBMITTED'' COMMENT ''SUBMITTED/PROCESSING/REPLIED/CLOSED（A4 状态机）''',
  'SELECT ''feedback status default ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @chk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
    AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'ck_user_feedback_status'
);
SET @ddl := IF(@chk_exists = 0,
  'ALTER TABLE user_feedback ADD CONSTRAINT ck_user_feedback_status CHECK (status IN (''SUBMITTED'',''PROCESSING'',''REPLIED'',''CLOSED''))',
  'SELECT ''feedback check ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 第 3 部分：sys_user.bio（A5）、覆盖索引（H12）、sys_role.app_grantable（A4）
-- =====================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'bio'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN bio VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''个人简介（A5 用户中心，空串表示未填写）'' AFTER remark',
  'SELECT ''bio col ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'idx_user_type_del_flag'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_user ADD INDEX idx_user_type_del_flag (user_type, del_flag)',
  'SELECT ''h12 index ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'app_grantable'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_role ADD COLUMN app_grantable TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否可授予 App 用户（A4 权威来源；1=可授予，0=拒绝）'' AFTER data_scope',
  'SELECT ''app_grantable col ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sys_role SET app_grantable = 0 WHERE (role_id = 1 OR role_key = 'admin') AND app_grantable <> 0;

-- =====================================================================
-- 第 4 部分：产品菜单种子（A1+PC+B1+C 最终形态；upsert 幂等）
-- =====================================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(5000, '工作台',       0, 1, 'workspace',   NULL, '', 1, 0, 'M', '0', '0', NULL, 'dashboard', 'A1', 'A1SEED'),
(2000, '内容管理',     0, 2, 'content',     NULL, '', 1, 0, 'M', '0', '0', NULL, 'Document',  'b-migration', 'B模块内容管理目录（替代原 A1 内容与作品）'),
(5003, '版权审核管理', 0, 3, 'copyright',   NULL, '', 1, 0, 'M', '0', '0', NULL, 'Stamp',     'A1', 'A1SEED'),
(5004, '交易商务管理', 0, 4, 'trade',       NULL, '', 1, 0, 'M', '0', '0', NULL, 'Sell',      'A1', 'A1SEED'),
(5005, '平台运维管理', 0, 5, 'operation',   NULL, '', 1, 0, 'M', '0', '0', NULL, 'Operation', 'A1', 'A1SEED'),
(5006, 'AI 创作与福利',0, 6, 'ai-group',    NULL, '', 1, 0, 'M', '0', '0', NULL, 'Magic',     'A1', 'A1SEED'),
(5002, '数据统计',     0, 7, 'statistics',  NULL, '', 1, 0, 'M', '0', '0', NULL, 'Chart',     'A1', 'A1SEED')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name), order_num=VALUES(order_num), path=VALUES(path),
  visible=VALUES(visible), status=VALUES(status), icon=VALUES(icon), remark=VALUES(remark);

-- 系统管理恢复后位于业务目录之后（order_num=90）。

-- 用户画像与推荐配置已下线：在产品菜单 upsert 前清理旧入口及授权，兼容旧 ID 5141/5400。
-- 按父目录和名称/组件识别页面，保留使用 5141 的版权申请按钮。
-- 旧风控页面 5143 仍迁至 5401，避免与版权申请删除按钮冲突。
START TRANSACTION;
DELETE rm FROM sys_role_menu rm
LEFT JOIN sys_menu menu ON menu.menu_id = rm.menu_id
LEFT JOIN sys_menu parent ON parent.menu_id = menu.parent_id
WHERE (menu.parent_id = 5005 AND menu.menu_type = 'C'
       AND (menu.menu_name = '用户画像与推荐配置' OR menu.component = 'operation/UserProfileRec'))
   OR (parent.parent_id = 5005 AND parent.menu_type = 'C'
       AND (parent.menu_name = '用户画像与推荐配置' OR parent.component = 'operation/UserProfileRec'));
DELETE child, retired FROM sys_menu retired
LEFT JOIN sys_menu child ON child.parent_id = retired.menu_id
WHERE retired.parent_id = 5005 AND retired.menu_type = 'C'
  AND (retired.menu_name = '用户画像与推荐配置' OR retired.component = 'operation/UserProfileRec');
-- 云端历史删除可能只移除了菜单行，清理其遗留的 5400 授权。
DELETE FROM sys_role_menu WHERE menu_id = 5400
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 5400);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 5401
FROM sys_role_menu rm
JOIN sys_menu old ON old.menu_id = rm.menu_id
WHERE old.menu_id = 5143 AND old.parent_id = 5005 AND old.menu_name = '全局风控管理' AND old.menu_type = 'C';
DELETE rm FROM sys_role_menu rm
JOIN sys_menu old ON old.menu_id = rm.menu_id
WHERE old.menu_id = 5143 AND old.parent_id = 5005 AND old.menu_name = '全局风控管理' AND old.menu_type = 'C';
DELETE FROM sys_menu
WHERE menu_id = 5143 AND parent_id = 5005 AND menu_name = '全局风控管理' AND menu_type = 'C';
COMMIT;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(5100, '数据总览',           5000, 1, '/dashboard', 'dashboard/Dashboard',            '', 1, 0, 'C', '0', '0', 'smartscript:dashboard:view',   'DataLine', 'A1', 'A1SEED'),
(2010, '分类管理',           2000, 1, 'category',       'content/category/index',     '', 1, 0, 'C', '0', '0', 'content:category:list',        'Collection', 'b-migration', 'B模块内容管理菜单'),
(2011, '标签管理',           2000, 2, 'tag',            'content/tag/index',          '', 1, 0, 'C', '0', '0', 'content:tag:list',             'Tag',        'b-migration', 'B模块内容管理菜单'),
(2013, '作品管理',           2000, 3, 'work',           'content/work/index',         '', 1, 0, 'C', '0', '0', 'content:work:list',            'Document',   'b-migration', 'B模块内容管理菜单'),
(2014, '书城作品管理',       2000, 4, 'bookstore',      'content/bookstore/index',    '', 1, 0, 'C', '0', '0', 'content:bookstore:list',       'ShoppingCart','b-migration', 'B模块内容管理菜单'),
(2015, '排行榜管理',         2000, 5, 'ranking',        'content/ranking/index',      '', 1, 0, 'C', '0', '0', 'content:ranking:list',         'Chart',      'b-migration', 'B模块内容管理菜单'),
(2017, '作品上传资料',       2000, 7, 'workfile',       'content/workfile/index',     '', 1, 0, 'C', '0', '0', 'content:workfile:list',        'Upload',     'b-migration', 'B模块内容管理菜单'),
(2012, '外部视频管理',       2000, 8, 'external-drama', NULL,                         '', 1, 0, 'M', '0', '0', NULL,                           'VideoCamera','b-migration', 'B模块外部视频管理目录'),
(2018, '渠道管理',           2012, 1, 'channel',        'content/external-drama/channel/index', '', 1, 0, 'C', '0', '0', 'content:channel:list',    'Guide',      'b-migration', 'B模块外部视频菜单'),
(2019, '视频内容管理',       2012, 2, 'drama',          'content/external-drama/drama/index',   '', 1, 0, 'C', '0', '0', 'content:drama:list',      'VideoPlay',  'b-migration', 'B模块外部视频菜单'),
(2051, '外部漫剧发行',       2000, 9, 'external-video', 'operation/ShortDrama',                 '', 1, 0, 'C', '0', '0', 'smartscript:content:video','VideoPlay',  'b-migration', 'B模块外部漫剧发行（原 A1 5104 迁移）'),
(5110, '运营数据总览',       5002, 1, 'overview', 'statistics/OperationOverview', '', 1, 0, 'C', '0', '0', 'smartscript:stats:overview', 'DataLine', 'A1', 'A1SEED'),
(5111, '明细数据查询',       5002, 2, 'detail',   'statistics/DetailQuery',       '', 1, 0, 'C', '0', '0', 'smartscript:stats:detail',   'Search',   'A1', 'A1SEED'),
(5125, 'AI初审+人工复核',    5003, 1, 'ai-review', NULL,                          '', 1, 0, 'M', '0', '0', NULL,                             'Stamp', 'A1', 'A1SEED'),
(5120, '作品审核工作台',     5125, 1, 'review',       'copyright/ReviewWorkbench',  '', 1, 0, 'C', '0', '0', 'smartscript:copyright:review',      'Stamp', 'A1', 'A1SEED'),
(5121, 'AI审核规则配置',     5125, 2, 'review-rules', 'copyright/AiReviewRules',    '', 1, 0, 'C', '0', '0', 'smartscript:copyright:reviewRules', 'Stamp', 'A1', 'A1SEED'),
(5122, '版权中心对接',       5003, 4, 'center', 'copyright/CopyrightCenter',          '', 1, 0, 'C', '0', '0', 'smartscript:copyright:center',  'Link',   'A1', 'A1SEED'),
(5123, '版权资产库管理',     5003, 5, 'assets', 'copyright/CopyrightAssets',          '', 1, 0, 'C', '0', '0', 'smartscript:copyright:assets',  'Folder', 'A1', 'A1SEED'),
(5124, '印章审核',           5003, 6, 'seals',  'copyright/SealReview',                '', 1, 0, 'C', '0', '0', 'smartscript:copyright:seals',   'Stamp',  'A1', 'A1SEED'),
(5130, '交易作品管理',       5004, 1, 'works',      'trade/TradeWorks',   '', 1, 0, 'C', '0', '0', 'trade:works:list',     'Sell',   'A1', 'A1SEED'),
(5133, '询盘管理',           5004, 2, 'inquiry',    'trade/Inquiry',      '', 1, 0, 'C', '0', '0', 'trade:inquiry:list',   'Chat',   'A1', 'A1SEED'),
(5131, '授权订单管理',       5004, 4, 'orders',     'trade/AuthOrders',   '', 1, 0, 'C', '0', '0', 'trade:orders:list',    'Sell',   'A1', 'A1SEED'),
(5132, '合作方管理',         5004, 5, 'partners',   'trade/Partners',     '', 1, 0, 'C', '0', '0', 'trade:partners:list',  'User',   'A1', 'A1SEED'),
(5136, '需求标签',           5004, 6, 'demand-tags','trade/DemandTags',   '', 1, 0, 'C', '0', '0', 'trade:tags:list',      'Tag',    'c-migration', 'C模块交易菜单补全'),
(5137, '商务跟进',           5004, 7, 'follow-up',  'trade/FollowUp',     '', 1, 0, 'C', '0', '0', 'trade:followups:list', 'Phone',  'c-migration', 'C模块交易菜单补全'),
(5138, '征集项目',           5004, 8, 'demand',     'trade/Demand',       '', 1, 0, 'C', '0', '0', 'trade:demand:list',    'List',   'c-migration', 'C模块交易菜单补全'),
(5134, '合同与结算',         5004, 9, 'contracts',  'common/ModuleScaffold', '', 1, 0, 'C', '0', '0', 'smartscript:trade:contracts', 'Document', 'A1', 'A1SEED'),
(5140, '广告运营配置',         5005, 1, 'ad-config',      'operation/AdConfig',      '', 1, 0, 'C', '0', '0', 'smartscript:ops:adConfig',   'Picture',   'A1', 'A1SEED'),
(5401, '全局风控管理',         5005, 4, '/risk',          'risk/RiskManage',         '', 1, 0, 'C', '0', '0', 'smartscript:ops:risk',       'Warning',   'A1', 'A1SEED'),
(5141, '版权申请查询',         5126, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'smartscript:copyright:application:query',  '#', 'copyright-migration', '版权申请查询按钮权限'),
(5142, '版权申请详情',         5126, 2, '', NULL, '', 1, 0, 'F', '0', '0', 'smartscript:copyright:application:detail', '#', 'copyright-migration', '版权申请详情按钮权限'),
(5143, '版权申请删除',         5126, 3, '', NULL, '', 1, 0, 'F', '0', '0', 'smartscript:copyright:application:remove', '#', 'copyright-migration', '版权申请删除按钮权限'),
(5144, '版权申请同步',         5126, 4, '', NULL, '', 1, 0, 'F', '0', '0', 'smartscript:copyright:application:sync',   '#', 'copyright-migration', '版权申请同步按钮权限'),
(5145, '版权申请导出',         5126, 5, '', NULL, '', 1, 0, 'F', '0', '0', 'smartscript:copyright:application:export', '#', 'copyright-migration', '版权申请导出按钮权限'),
(5150, 'AI 创作与次数',       5006, 1, '/ai/operations',    'system/AiOperations', '', 1, 0, 'C', '0', '0', 'smartscript:ai:operations',   'Magic',  'A1', 'A1SEED'),
(5151, '福利与积分配置',      5006, 2, '/support/welfare',  'welfare/WelfarePoints', '', 1, 0, 'C', '0', '0', 'smartscript:support:welfare', 'Present','A1', 'A1SEED'),
(5152, '消息与公告',          5005, 5, '/support/messages', 'support/MessagesAnnouncements', '', 1, 0, 'C', '0', '0', 'smartscript:support:messages','Bell',   'A1', 'A1SEED'),
(5201, '会话列表',           5152, 9, '#',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:session:list',  '#',     'A3', '消息与公告用户沟通查询权限'),
(5202, '会话详情',           3000, 7, 'chat-detail',   'chat/ChatDetail',   '', 1, 0, 'C', '1', '0', 'chat:session:query', '#',     'A3', 'A3 用户沟通会话详情（原 5200 目录迁入用户中心）'),
(5203, '分配管理员',         5152, 10, '',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:session:assign','#',     'A3', 'A3 分配管理员按钮'),
(5204, '变更状态',           5152, 11, '',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:session:status','#',     'A3', 'A3 变更状态按钮'),
(5205, '发送消息',           5152, 12, '',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:message:send',  '#',     'A3', 'A3 发送消息按钮'),
(5206, '查看消息',           5152, 13, '',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:message:list',  '#',     'A3', 'A3 查看消息按钮'),
(5207, '标记已读',           5152, 14, '',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:session:read',  '#',     'A3', 'A3 标记已读按钮'),
(5208, '创建会话',           5152, 15, '',             NULL,                '', 1, 0, 'F', '0', '0', 'chat:session:create','#',     'A3', 'A3 创建会话按钮')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
  path=VALUES(path), component=VALUES(component), menu_type=VALUES(menu_type),
  perms=VALUES(perms), icon=VALUES(icon), remark=VALUES(remark);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(3000, '用户中心',           0,    9, 'appuser',  NULL,                  '', NULL,        1, 0, 'M', '0', '0', NULL,                'peoples', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3001, 'App用户与创作者',    3000, 1, 'users',    'user/UserManage',     '', 'A4AppUser',  1, 0, 'C', '0', '0', 'user:app:list',     'user',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3005, '实名认证记录',           3000, 2, 'realname', 'user/realname/index', '', 'A4RealName', 1, 0, 'C', '0', '0', 'user:realname:list','form',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3013, '用户反馈',           3000, 5, 'feedback', 'user/feedback/index', '', 'A4Feedback', 1, 0, 'C', '0', '0', 'user:feedback:list','edit',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3002, 'App用户查询',     3001, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:app:query',      '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3003, 'App用户状态',     3001, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:app:status',     '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3004, '用户角色授权',    3001, 3, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:app:grant',      '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3006, '实名认证记录详情',    3005, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:realname:query', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3009, '作者能力变更',    3001, 4, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:creator:update', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3014, '反馈详情',        3013, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:feedback:query', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3015, '反馈处理',        3013, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:feedback:handle','#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
  path=VALUES(path), component=VALUES(component), route_name=VALUES(route_name),
  perms=VALUES(perms), icon=VALUES(icon), remark=VALUES(remark);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(2020, '分类查询', 2010, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:category:query',  '#', 'b-migration', 'B模块内容管理按钮权限'),
(2021, '分类新增', 2010, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:category:add',    '#', 'b-migration', 'B模块内容管理按钮权限'),
(2022, '分类编辑', 2010, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:category:edit',   '#', 'b-migration', 'B模块内容管理按钮权限'),
(2023, '标签查询', 2011, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:tag:query',       '#', 'b-migration', 'B模块内容管理按钮权限'),
(2024, '标签新增', 2011, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:tag:add',         '#', 'b-migration', 'B模块内容管理按钮权限'),
(2025, '标签编辑', 2011, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:tag:edit',        '#', 'b-migration', 'B模块内容管理按钮权限'),
(2026, '标签删除', 2011, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:tag:remove',      '#', 'b-migration', 'B模块内容管理按钮权限'),
(2030, '作品查询',   2013, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:work:query',      '#', 'b-migration', 'B模块内容管理按钮权限'),
(2031, '书城查询',   2014, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:bookstore:query', '#', 'b-migration', 'B模块内容管理按钮权限'),
(2032, '书城编辑',   2014, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:bookstore:edit',  '#', 'b-migration', 'B模块内容管理按钮权限'),
(2033, '排行榜查询', 2015, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:ranking:query',   '#', 'b-migration', 'B模块内容管理按钮权限'),
(2034, '排行榜编辑', 2015, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:ranking:edit',    '#', 'b-migration', 'B模块内容管理按钮权限'),
(2039, '上传资料查询', 2017, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:workfile:query', '#', 'b-migration', 'B模块内容管理按钮权限'),
(2040, '渠道查询',   2018, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:channel:query',     '#', 'b-migration', 'B模块外部视频按钮权限'),
(2041, '渠道新增',   2018, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:channel:add',       '#', 'b-migration', 'B模块外部视频按钮权限'),
(2042, '渠道编辑',   2018, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:channel:edit',      '#', 'b-migration', 'B模块外部视频按钮权限'),
(2043, '视频查询',   2019, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:drama:query',       '#', 'b-migration', 'B模块外部视频按钮权限'),
(2044, '视频新增',   2019, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:drama:add',         '#', 'b-migration', 'B模块外部视频按钮权限'),
(2045, '视频编辑',   2019, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:drama:edit',        '#', 'b-migration', 'B模块外部视频按钮权限'),
(2047, '关联编辑',     2019, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramabind:edit',    '#', 'b-migration', 'B模块外部视频按钮权限'),
(2049, '上下架编辑',   2019, 5, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastatus:edit',  '#', 'b-migration', 'B模块外部视频按钮权限'),
(2050, '播放数据查询', 2019, 6, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastats:query',  '#', 'b-migration', 'B模块外部视频按钮权限'),
(2052, '播放历史查询', 2019, 7, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastats:list',   '#', 'b-migration', 'B模块外部视频详情-播放历史'),
(5180, '交易作品新增',   5130, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:works:add',       '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5181, '交易作品编辑',   5130, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:works:edit',      '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5182, '授权订单查询',   5131, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:orders:query',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5183, '合作方新增',     5132, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:partners:add',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5184, '合作方编辑',     5132, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:partners:edit',   '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5185, '询盘查询',       5133, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:query',   '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5186, '询盘跟进',       5133, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:edit',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5187, '询盘转订单',     5133, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:convert', '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5188, '报价接受/拒绝',  5133, 6, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:quote:edit',      '#', 'c-perm-migration', 'C 模块交易按钮权限（报价已并入询盘）'),
(5189, '需求标签新增',   5136, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:tags:add',        '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5190, '需求标签编辑',   5136, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:tags:edit',       '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5191, '需求标签删除',   5136, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:tags:remove',     '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5192, '商务跟进新增',   5137, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:followups:add',   '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5193, '征集投稿查询',   5138, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:demand:query',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5194, '任务发布',       5138, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:demand:add',      '#', 'c-perm-migration', '平台代需求方发布任务（发布权已收归运营）'),
(5195, '询盘发起',       5133, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:add',     '#', 'c-perm-ops-migration', 'C 模块交易操作按钮权限'),
(5196, '报价提交/议价',  5133, 7, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:quote:add',       '#', 'c-perm-ops-migration', 'C 模块交易操作按钮权限（报价已并入询盘）'),
(5197, '任务运营编辑',   5138, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:demand:edit',     '#', 'c-perm-migration', '编辑/关闭任务与延时审批'),
(5198, '任务逐轮验收',   5138, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:demand:review',   '#', 'c-perm-migration', '逐轮交付验收与放款触发'),
(5424, '报价记录',       5133, 5, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:quote:list',      '#', 'c-migration', '报价管理并入询盘管理：行内查看该询盘全部报价')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num), perms=VALUES(perms);

-- =====================================================================
-- 第 5 部分：角色与授权（幂等）
-- =====================================================================
INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, remark)
SELECT 'A1受限运营', 'a1_operator', 50, '2', 1, 1, '0', '0', 'A1', 'A1SEED-CREATED'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='a1_operator' AND del_flag='0');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 5000 AS menu_id UNION ALL SELECT 5002 UNION ALL SELECT 5100 UNION ALL SELECT 5110
) m
WHERE r.role_key = 'a1_operator' AND r.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 3000 AS menu_id UNION ALL SELECT 3001 UNION ALL SELECT 3002 UNION ALL SELECT 3003
  UNION ALL SELECT 3004 UNION ALL SELECT 3005 UNION ALL SELECT 3006
  UNION ALL SELECT 3009
  UNION ALL SELECT 3013 UNION ALL SELECT 3014 UNION ALL SELECT 3015
  UNION ALL SELECT 2000 UNION ALL SELECT 2010 UNION ALL SELECT 2011 UNION ALL SELECT 2012
  UNION ALL SELECT 2013 UNION ALL SELECT 2014 UNION ALL SELECT 2015 UNION ALL SELECT 2016 UNION ALL SELECT 2017
  UNION ALL SELECT 2018 UNION ALL SELECT 2019
  UNION ALL SELECT 2020 UNION ALL SELECT 2021 UNION ALL SELECT 2022
  UNION ALL SELECT 2023 UNION ALL SELECT 2024 UNION ALL SELECT 2025 UNION ALL SELECT 2026
  UNION ALL SELECT 2030 UNION ALL SELECT 2031 UNION ALL SELECT 2032 UNION ALL SELECT 2033 UNION ALL SELECT 2034
  UNION ALL SELECT 2035 UNION ALL SELECT 2036 UNION ALL SELECT 2037 UNION ALL SELECT 2039
  UNION ALL SELECT 2040 UNION ALL SELECT 2041 UNION ALL SELECT 2042 UNION ALL SELECT 2043 UNION ALL SELECT 2044
  UNION ALL SELECT 2045 UNION ALL SELECT 2047 UNION ALL SELECT 2049
  UNION ALL SELECT 2050 UNION ALL SELECT 2051 UNION ALL SELECT 2052
  UNION ALL SELECT 5130 UNION ALL SELECT 5131 UNION ALL SELECT 5132 UNION ALL SELECT 5133
  UNION ALL SELECT 5424 UNION ALL SELECT 5136 UNION ALL SELECT 5137 UNION ALL SELECT 5138
  UNION ALL SELECT 5180 UNION ALL SELECT 5181 UNION ALL SELECT 5182 UNION ALL SELECT 5183
  UNION ALL SELECT 5184 UNION ALL SELECT 5185 UNION ALL SELECT 5186 UNION ALL SELECT 5187
  UNION ALL SELECT 5188 UNION ALL SELECT 5189 UNION ALL SELECT 5190 UNION ALL SELECT 5191
  UNION ALL SELECT 5192 UNION ALL SELECT 5193 UNION ALL SELECT 5194 UNION ALL SELECT 5195
  UNION ALL SELECT 5196 UNION ALL SELECT 5197 UNION ALL SELECT 5198
  UNION ALL SELECT 5141 UNION ALL SELECT 5142 UNION ALL SELECT 5143
  UNION ALL SELECT 5144 UNION ALL SELECT 5145 UNION ALL SELECT 5401
) m
WHERE r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- =====================================================================
-- 第 6 部分：C 交易域现役业务表（14 张，幂等建表 + 存量列补齐；sys_selection 已退役）
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_business_follow (
  `follow_id` bigint NOT NULL AUTO_INCREMENT,
  `partner_id` bigint NOT NULL,
  `follower_id` bigint NOT NULL,
  `follow_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `content` text COLLATE utf8mb4_general_ci NOT NULL,
  `next_follow_date` date DEFAULT NULL,
  `next_follow_content` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `follow_time` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`follow_id`),
  KEY `idx_partner_id` (`partner_id`) USING BTREE,
  KEY `idx_follower_id` (`follower_id`) USING BTREE,
  KEY `idx_follow_time` (`follow_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商务跟进记录';

CREATE TABLE IF NOT EXISTS sys_chat_message (
  `message_id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `msg_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `content` text COLLATE utf8mb4_general_ci NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_read` tinyint NOT NULL,
  `read_time` datetime DEFAULT NULL,
  `recalled` tinyint NOT NULL DEFAULT 0 COMMENT '0=正常 1=已撤回',
  `recalled_time` datetime DEFAULT NULL COMMENT '撤回时间',
  `quote_message_id` bigint DEFAULT NULL COMMENT '被引用消息ID（NULL=普通消息）',
  `quote_sender` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '被引消息发送者昵称快照',
  `quote_text` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '被引消息内容快照（原文撤回后不再下发）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`message_id`),
  KEY `idx_session_id` (`session_id`) USING BTREE,
  KEY `idx_sender_id` (`sender_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息';

CREATE TABLE IF NOT EXISTS `sys_chat_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `session_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `inquiry_id` bigint DEFAULT NULL,
  `business_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `business_id` bigint DEFAULT NULL,
  `business_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `assigned_admin_id` bigint DEFAULT NULL,
  `user1_id` bigint NOT NULL,
  `user2_id` bigint NOT NULL,
  `last_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `last_message_time` datetime DEFAULT NULL,
  `user1_unread` int NOT NULL,
  `user2_unread` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`session_id`) USING BTREE,
  UNIQUE KEY `uk_session_biz` (`user1_id`,`user2_id`,`business_type`,`business_id`) USING BTREE,
  KEY `idx_last_message_time` (`last_message_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='会话表';

CREATE TABLE IF NOT EXISTS sys_contact_profile (
  `contact_id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `phone_cipher` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `email_cipher` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `wechat_cipher` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `display_scope` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `display_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `verify_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`contact_id`),
  UNIQUE KEY `uk_owner_id` (`owner_id`) USING BTREE,
  KEY `idx_display_scope` (`display_scope`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='联系方式加密档案';

CREATE TABLE IF NOT EXISTS `sys_demand` (
  `demand_id` bigint NOT NULL AUTO_INCREMENT,
  `demand_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `client_id` bigint NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `genre_id` int DEFAULT NULL,
  `tags` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `budget` decimal(12,2) NOT NULL,
  `deadline` date DEFAULT NULL COMMENT '征集截止日期（当天整天有效；NULL=不设时限，过期由定时任务关闭）',
  `requirement` text COLLATE utf8mb4_general_ci NOT NULL,
  `contact_info` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `submission_count` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source` varchar(32) COLLATE utf8mb4_general_ci DEFAULT 'manual' COMMENT '来源(manual/auto)',
  `task_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'script' COMMENT '任务类型: script=只征集剧本, script_storyboard=剧本+分镜, storyboard=提供剧本做分镜',
  `source_work_id` bigint DEFAULT NULL COMMENT '源剧本作品ID（storyboard 类型，发布者选本人作品，与 source_script_url 至少其一）',
  `source_script_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源剧本文件相对路径（storyboard 类型，上传的剧本文档）',
  `source_script_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源剧本文件原始文件名（展示用）',
  `audit_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布审核意见（驳回时给需求方看，重新提交后清空）',
  `audit_at` datetime DEFAULT NULL COMMENT '发布审核时间',
  `auditor_id` bigint DEFAULT NULL COMMENT '发布审核人（后台账号ID）',
  `total_episodes` int DEFAULT NULL COMMENT '总集数(如24)',
  `accepted_creator_id` bigint DEFAULT NULL COMMENT '接单人用户ID',
  `accepted_at` datetime DEFAULT NULL COMMENT '接单时间',
  `duration_hours` int DEFAULT NULL COMMENT '接单后完成时限(小时)',
  `due_at` datetime DEFAULT NULL COMMENT '应完成时间=accepted_at+duration_hours',
  `is_overdue` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否逾期:0否1是',
  `escrow_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '托管金额(元)=预算',
  `escrow_status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'none' COMMENT '托管状态',
  `deposit_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '定金金额(元)=floor(预算/10)',
  `deposit_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'none' COMMENT '定金状态',
  `deposit_paid_at` datetime DEFAULT NULL COMMENT '定金入账时间',
  `deposit_settled_at` datetime DEFAULT NULL COMMENT '定金结算时间',
  `fail_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因',
  `fail_at` datetime DEFAULT NULL COMMENT '失败终止时间',
  PRIMARY KEY (`demand_id`) USING BTREE,
  UNIQUE KEY `uk_demand_no` (`demand_no`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_square` (`status`,`escrow_status`,`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='甲方需求表';

CREATE TABLE IF NOT EXISTS sys_demand_submission (
  `submission_id` bigint NOT NULL AUTO_INCREMENT,
  `demand_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `submitter_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `submit_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `review_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`submission_id`),
  UNIQUE KEY `uk_demand_work` (`demand_id`,`work_id`) USING BTREE,
  KEY `idx_demand` (`demand_id`) USING BTREE,
  KEY `idx_submitter` (`submitter_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='征集投稿';

CREATE TABLE IF NOT EXISTS `sys_inquiry` (
  `inquiry_id` bigint NOT NULL AUTO_INCREMENT,
  `inquiry_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '授权类型；无版权作品为空',
  `intended_use` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `budget` decimal(12,2) DEFAULT NULL,
  `message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `expire_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`inquiry_id`) USING BTREE,
  UNIQUE KEY `uk_inquiry_no` (`inquiry_no`) USING BTREE,
  KEY `idx_buyer_id` (`buyer_id`) USING BTREE,
  KEY `idx_seller_id` (`seller_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='询盘表';

CREATE TABLE IF NOT EXISTS sys_offline_cooperation (
  `cooperation_id` bigint NOT NULL AUTO_INCREMENT,
  `cooperation_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `creator_id` bigint NOT NULL,
  `partner_id` bigint DEFAULT NULL,
  `contact_id` bigint DEFAULT NULL,
  `source` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `expected_amount` decimal(14,2) DEFAULT NULL,
  `next_follow_at` datetime DEFAULT NULL,
  `negotiation_place` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '线下谈判地点（分工16）',
  `contact_person` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系人姓名（分工16，明文快照）',
  `contact_value` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系方式（分工16，明文电话/微信/邮箱）',
  `last_follow_at` datetime DEFAULT NULL,
  `remark` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`cooperation_id`),
  UNIQUE KEY `uk_cooperation_no` (`cooperation_no`) USING BTREE,
  KEY `idx_work_status` (`work_id`) USING BTREE,
  KEY `idx_creator_id` (`creator_id`) USING BTREE,
  KEY `idx_next_follow` (`next_follow_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合作记录（线上意向/线下谈判）';

CREATE TABLE IF NOT EXISTS sys_order (
  `order_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_id` bigint DEFAULT NULL,
  `escrow_id` bigint DEFAULT NULL,
  `inquiry_id` bigint DEFAULT NULL,
  `quote_id` bigint DEFAULT NULL,
  `work_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `order_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '授权类型；无版权作品为空',
  `total_amount` decimal(12,2) NOT NULL,
  `platform_fee` decimal(12,2) NOT NULL,
  `split_rate` decimal(5,4) NOT NULL,
  `seller_amount` decimal(12,2) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pay_time` datetime DEFAULT NULL,
  `complete_time` datetime DEFAULT NULL,
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`) USING BTREE,
  UNIQUE KEY `uk_inquiry_id` (`inquiry_id`) USING BTREE,
  KEY `idx_buyer_id` (`buyer_id`) USING BTREE,
  KEY `idx_seller_id` (`seller_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_pay_time` (`pay_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权订单';

CREATE TABLE IF NOT EXISTS sys_order_status_log (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `from_status` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `to_status` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_role` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_order` (`order_id`) USING BTREE,
  KEY `idx_to_status` (`to_status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志';

CREATE TABLE IF NOT EXISTS sys_partner (
  `partner_id` bigint NOT NULL AUTO_INCREMENT,
  `partner_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `partner_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `partner_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `contact_person` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `contact_phone` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `contact_email` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `demand_tags` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cooperation_count` int NOT NULL,
  `total_amount` decimal(14,2) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `remark` text COLLATE utf8mb4_general_ci,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`partner_id`),
  UNIQUE KEY `uk_partner_no` (`partner_no`) USING BTREE,
  KEY `idx_partner_type` (`partner_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合作方';

CREATE TABLE IF NOT EXISTS `sys_quote` (
  `quote_id` bigint NOT NULL AUTO_INCREMENT,
  `quote_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `inquiry_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `quoter_id` bigint NOT NULL,
  `quoter_role` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '授权类型；无版权作品为空',
  `valid_days` int NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `expire_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`quote_id`) USING BTREE,
  UNIQUE KEY `uk_quote_no` (`quote_no`) USING BTREE,
  KEY `idx_inquiry_id` (`inquiry_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='报价表';

CREATE TABLE IF NOT EXISTS `sys_work` (
  `work_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `cover` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `author_id` bigint NOT NULL,
  `genre_id` int DEFAULT NULL,
  `channel_id` int DEFAULT NULL COMMENT '所属频道：146男频 147女频',
  `work_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `upload_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `length_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `summary` text COLLATE utf8mb4_general_ci,
  `price` decimal(12,2) NOT NULL,
  `trade_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `trade_enabled` tinyint NOT NULL,
  `negotiable_min` decimal(12,2) DEFAULT NULL,
  `negotiable_max` decimal(12,2) DEFAULT NULL,
  `quote_valid_days` int NOT NULL,
  `word_count` int NOT NULL,
  `episode_count` int DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `is_free` tinyint NOT NULL,
  `preview_enabled` tinyint DEFAULT NULL,
  `preview_episodes` int DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `is_copyrighted` tinyint NOT NULL,
  `view_count` int NOT NULL,
  `favorite_count` int NOT NULL,
  `sale_count` int NOT NULL,
  `rating` decimal(3,1) NOT NULL,
  `quality_level` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ext_json` json DEFAULT NULL,
  `is_deleted` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '交易设置：是否置顶 0否 1是',
  `is_recommend` tinyint NOT NULL DEFAULT '0' COMMENT '交易设置：是否推荐 0否 1是',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '交易设置：手动排序权重（升序）',
  PRIMARY KEY (`work_id`) USING BTREE,
  KEY `idx_author_id` (`author_id`) USING BTREE,
  KEY `idx_genre_id` (`genre_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_work_type` (`work_type`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_view_count` (`view_count`) USING BTREE,
  KEY `idx_price` (`price`) USING BTREE,
  KEY `idx_trade_enabled` (`trade_enabled`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品表';

CREATE TABLE IF NOT EXISTS sys_demand_tag (
  `tag_id` bigint NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(50) NOT NULL,
  `used_count` int NOT NULL DEFAULT 0,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='需求标签';

-- 作品标签关联表（依据 附件5.1 表3-13；云端 script_platform_dev 已存在该表，
-- 此前漏在初始化脚本外，新环境装库会缺表导致 App 书城标签筛选报 SQL 错。
-- 唯一键按索引名 uk_work_tag 补全为 (work_id, tag_id)：文档「表索引」只列了首列 work_id，
-- 若真按单列建，一个作品将永远只能关联一个标签，与作品标签业务不符）
CREATE TABLE IF NOT EXISTS sys_work_tag (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `tag_id` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_tag` (`work_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品标签关联表';

-- ---------------------------------------------------------------------
-- B 模块（内容与作品）内容域建表
--
-- 依据：附件5.1 数据库设计文档 —— 表3-9 sys_category、表3-12 sys_tag、
--       表3-37 sys_episode、表3-39 sys_play_history、表3-86 sys_ranking_snapshot、
--       表3-88 sys_banner；另与仓库内按云端库实现的 domain/Mapper
--       （SysCategory/SysTag/SysEpisode/SysPlayHistory/SysRankingSnapshot/SysBanner）
--       逐列核对；并已直连云端 script_platform_dev，用 information_schema 复核列名、类型、
--       可空性、默认值、EXTRA 与索引，逐项一致。
-- 此前这批表漏在初始化脚本外，新环境装库会缺表，导致 App 书城与剧集相关接口启动即报错。
--
-- 索引说明：文档「表索引」的「对应字段」列对复合索引只填了首列（已解压原始 docx
--   逐格核对，确系文档本身如此）。以下定义已与云端 script_platform_dev 实际结构比对一致：
--     uk_work_episode_no     -> (work_id, episode_no)                              复合，云端一致
--     uk_ranking_work_period -> (ranking_type, period_start, period_end, work_id)  复合，云端一致
--     idx_ranking_period     -> (ranking_type)                                     单列，与文档一致
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_category (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `category_name` varchar(30) NOT NULL,
  `category_type` varchar(20) NOT NULL,
  `parent_id` int NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_name` (`category_name`) USING BTREE,
  KEY `idx_category_type` (`category_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='剧本分类表（APP 分类不包含外部视频）';

CREATE TABLE IF NOT EXISTS `sys_tag` (
  `tag_id` int NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `tag_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `group_name` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标签分组',
  `channel_id` bigint DEFAULT NULL COMMENT '所属频道ID（NULL=通用）',
  `use_count` int NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`tag_id`) USING BTREE,
  UNIQUE KEY `uk_tag_name_type_channel` (`tag_name`,`tag_type`,`channel_id`) USING BTREE,
  KEY `idx_tag_type` (`tag_type`) USING BTREE,
  KEY `idx_tag_nav` (`status`,`channel_id`,`tag_type`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='标签表';

CREATE TABLE IF NOT EXISTS `sys_episode` (
  `episode_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `episode_no` int NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `video_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `cover_url` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `duration` int NOT NULL,
  `is_free` tinyint NOT NULL,
  `unlock_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `play_count` int NOT NULL,
  `like_count` int NOT NULL,
  `comment_count` int NOT NULL,
  `completion_rate` decimal(5,2) DEFAULT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`episode_id`) USING BTREE,
  UNIQUE KEY `uk_work_episode_no` (`work_id`,`episode_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='剧集表';

CREATE TABLE IF NOT EXISTS sys_play_history (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `play_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户播放历史表（用户观看剧集的记录，用于运营统计）';

CREATE TABLE IF NOT EXISTS sys_ranking_snapshot (
  `ranking_id` bigint NOT NULL AUTO_INCREMENT,
  `ranking_type` varchar(20) NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `work_id` bigint NOT NULL,
  `rank_no` int NOT NULL,
  `score` decimal(14,4) NOT NULL,
  `view_count` bigint NOT NULL,
  `bookshelf_count` bigint NOT NULL,
  `growth_score` decimal(14,4) NOT NULL,
  `snapshot_time` datetime NOT NULL,
  `status` tinyint NOT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`ranking_id`),
  UNIQUE KEY `uk_ranking_work_period` (`ranking_type`,`period_start`,`period_end`,`work_id`) USING BTREE,
  KEY `idx_ranking_period` (`ranking_type`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_banner (
  `banner_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL,
  `image_url` varchar(500) NOT NULL,
  `link_type` varchar(20) DEFAULT NULL,
  `link_id` bigint DEFAULT NULL,
  `link_url` varchar(500) DEFAULT NULL,
  `position` varchar(30) DEFAULT NULL,
  `sort_order` int DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`banner_id`),
  KEY `idx_position` (`position`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 6.1 存量库列补齐（若曾单独跑过旧 c 建表脚本）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work' AND COLUMN_NAME = 'is_top');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_work ADD COLUMN is_top TINYINT NOT NULL DEFAULT 0 COMMENT ''交易设置：是否置顶 0否 1是'', ADD COLUMN is_recommend TINYINT NOT NULL DEFAULT 0 COMMENT ''交易设置：是否推荐 0否 1是'', ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT ''交易设置：手动排序权重（升序）''',
  'SELECT ''sys_work trade cols ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_offline_cooperation' AND COLUMN_NAME = 'negotiation_place');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_offline_cooperation ADD COLUMN negotiation_place varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''线下谈判地点（分工16）'' AFTER next_follow_at, ADD COLUMN contact_person varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''联系人姓名（分工16，明文快照）'' AFTER negotiation_place, ADD COLUMN contact_value varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''联系方式（分工16，明文电话/微信/邮箱）'' AFTER contact_person',
  'SELECT ''cooperation negotiation cols ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_order' AND INDEX_NAME = 'uk_inquiry_id');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_order ADD UNIQUE KEY uk_inquiry_id (inquiry_id) USING BTREE',
  'SELECT ''uk_inquiry_id ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_work_tag 唯一索引纠正（存量库）：旧含义为单列 (work_id)，一个作品只能挂一个标签。
-- 仅当现存 uk_work_tag 存在且确实只有 work_id 一列时才重建，重复执行无副作用。
SET @uk_exists := (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work_tag' AND INDEX_NAME = 'uk_work_tag');
SET @uk_second := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work_tag' AND INDEX_NAME = 'uk_work_tag' AND SEQ_IN_INDEX = 2);
SET @ddl := IF(@uk_exists = 1 AND @uk_second = 0,
  'ALTER TABLE sys_work_tag DROP INDEX uk_work_tag, ADD UNIQUE KEY uk_work_tag (work_id, tag_id) USING BTREE',
  'SELECT ''uk_work_tag ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 第 7 部分：交易字典（幂等去重插入）
-- =====================================================================
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT t.dict_name, t.dict_type, '0', 'c-dict-migration', NOW(), t.remark
FROM (
  SELECT '交易-订单状态' AS dict_name, 'trade_order_status' AS dict_type, 'PRD 9.3；对应前端 order' AS remark
  UNION ALL SELECT '交易-询盘状态', 'trade_inquiry_status', '2026-09-28 定稿；对应前端 inquiry'
  UNION ALL SELECT '交易-报价状态', 'trade_quote_status', '2026-09-28 定稿；对应前端 quote'
  UNION ALL SELECT '交易-报价方角色', 'trade_quoter_role', 'sys_quote.quoter_role；对应前端 quoterRole'
  UNION ALL SELECT '交易-合作方类型', 'trade_partner_type', '接口 2.35；对应前端 partnerType'
  UNION ALL SELECT '交易-合作方状态', 'trade_partner_status', 'sys_partner.status；对应前端 partnerStatus'
  UNION ALL SELECT '交易-授权类型', 'trade_license_type', '接口 2.25；对应前端 license'
  UNION ALL SELECT '交易-作品上架状态', 'trade_work_listing_status', '接口 2.25 listingStatus；对应前端 tradeWork'
  UNION ALL SELECT '交易-商务跟进状态', 'trade_follow_status', '接口 2.38；对应前端 follow'
  UNION ALL SELECT '交易-商务跟进方式', 'trade_follow_method', '接口 2.38 method；对应前端 followMethod'
  UNION ALL SELECT '交易-征集项目状态', 'trade_demand_status', 'sys_demand.status；对应前端 demand'
  UNION ALL SELECT '交易-投稿状态', 'trade_submission_status', 'sys_demand_submission.status；对应前端 submission'
  UNION ALL SELECT '交易-合作记录来源', 'trade_cooperation_source', 'sys_offline_cooperation.source；对应前端 cooperationSource'
  UNION ALL SELECT '交易-合作记录状态', 'trade_cooperation_status', 'sys_offline_cooperation.status；对应前端 cooperationStatus'
) t
LEFT JOIN sys_dict_type d ON d.dict_type = t.dict_type
WHERE d.dict_id IS NULL;

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT t.dict_sort, t.dict_label, t.dict_value, t.dict_type, NULL, t.list_class, 'N', '0', 'c-dict-migration', NOW(), 'C 模块枚举'
FROM (
  SELECT 1 AS dict_sort,'询盘中' AS dict_label,'inquiry' AS dict_value,'trade_order_status' AS dict_type,'info' AS list_class
  UNION ALL SELECT 2,'已报价','quoted','trade_order_status','warning'
  UNION ALL SELECT 3,'已确认','confirmed','trade_order_status','primary'
  UNION ALL SELECT 4,'待签约','contract_pending','trade_order_status','warning'
  UNION ALL SELECT 5,'待托管','escrow_pending','trade_order_status','warning'
  UNION ALL SELECT 6,'交割中','delivering','trade_order_status','primary'
  UNION ALL SELECT 7,'已完成','completed','trade_order_status','success'
  UNION ALL SELECT 8,'已取消','cancelled','trade_order_status','info'
  UNION ALL SELECT 9,'已退款','refunded','trade_order_status','danger'
  UNION ALL SELECT 1,'待回复','pending','trade_inquiry_status','warning'
  -- 2026-09-29 用户决策：原 accepted（已接受）并入 quoted，统一叫「议价中」，故不再插入 accepted
  UNION ALL SELECT 2,'议价中','quoted','trade_inquiry_status','primary'
  UNION ALL SELECT 3,'已拒绝','rejected','trade_inquiry_status','danger'
  UNION ALL SELECT 4,'已关闭','closed','trade_inquiry_status','info'
  UNION ALL SELECT 5,'已达成','deal','trade_inquiry_status','success'
  UNION ALL SELECT 1,'待买方确认','pending','trade_quote_status','warning'
  UNION ALL SELECT 2,'已接受','accepted','trade_quote_status','success'
  UNION ALL SELECT 3,'已拒绝','rejected','trade_quote_status','danger'
  UNION ALL SELECT 4,'已过期','expired','trade_quote_status','info'
  -- 2026-09-29 甲方双端拆分：买方议价产生的报价落 pending_seller（待卖方确认），本端不可接受/拒绝
  UNION ALL SELECT 5,'待卖方确认','pending_seller','trade_quote_status','primary'
  UNION ALL SELECT 1,'卖方报价','seller','trade_quoter_role','primary'
  UNION ALL SELECT 2,'买方议价','buyer','trade_quoter_role','warning'
  UNION ALL SELECT 1,'投资方','investor','trade_partner_type','default'
  UNION ALL SELECT 2,'制作机构','studio','trade_partner_type','default'
  UNION ALL SELECT 3,'发行平台','platform','trade_partner_type','default'
  UNION ALL SELECT 1,'正常','active','trade_partner_status','success'
  UNION ALL SELECT 2,'停用','inactive','trade_partner_status','info'
  UNION ALL SELECT 3,'待审核','pending','trade_partner_status','warning'
  UNION ALL SELECT 1,'独家','exclusive','trade_license_type','default'
  UNION ALL SELECT 2,'非独家','non_exclusive','trade_license_type','default'
  UNION ALL SELECT 3,'改编','adaptation','trade_license_type','default'
  -- 2026-09-30 用户决策：原第 4 项 negotiable「可议价」退役，故不再插入。
  -- 议价不是一种授权类型，而是所有作品共有的能力，改由 sys_work.negotiable_min/max 表达区间
  -- （填了则双方出价须落在区间内，留空为不限）；存量脏值由下方 7.1 归一。
  UNION ALL SELECT 1,'已上架','listed','trade_work_listing_status','success'
  UNION ALL SELECT 2,'已下架','offline','trade_work_listing_status','info'
  UNION ALL SELECT 1,'进行中','ongoing','trade_follow_status','success'
  UNION ALL SELECT 2,'待跟进','pending','trade_follow_status','warning'
  UNION ALL SELECT 3,'已完成','completed','trade_follow_status','info'
  UNION ALL SELECT 1,'电话','phone','trade_follow_method','default'
  UNION ALL SELECT 2,'邮件','email','trade_follow_method','default'
  UNION ALL SELECT 3,'面谈','meeting','trade_follow_method','default'
  UNION ALL SELECT 1,'征集中','open','trade_demand_status','success'
  -- 需求方自助发布：提交即待审核，审核通过后才是广场上的征集中
  UNION ALL SELECT 4,'待审核','pending_review','trade_demand_status','warning'
  UNION ALL SELECT 5,'审核未通过','rejected','trade_demand_status','danger'
  UNION ALL SELECT 2,'已截止','closed','trade_demand_status','info'
  UNION ALL SELECT 3,'已选定','selected','trade_demand_status','primary'
  UNION ALL SELECT 1,'已投稿','submitted','trade_submission_status','warning'
  UNION ALL SELECT 2,'入围','shortlisted','trade_submission_status','primary'
  UNION ALL SELECT 3,'已选用','accepted','trade_submission_status','success'
  UNION ALL SELECT 4,'未选用','rejected','trade_submission_status','info'
  UNION ALL SELECT 1,'线上合作意向','online','trade_cooperation_source','primary'
  UNION ALL SELECT 2,'线下谈判','offline','trade_cooperation_source','warning'
  UNION ALL SELECT 1,'待跟进','pending','trade_cooperation_status','warning'
  UNION ALL SELECT 2,'洽谈中','ongoing','trade_cooperation_status','primary'
  UNION ALL SELECT 3,'已达成','completed','trade_cooperation_status','success'
  UNION ALL SELECT 4,'已终止','cancelled','trade_cooperation_status','info'
) t
LEFT JOIN sys_dict_data d ON d.dict_type = t.dict_type AND d.dict_value = t.dict_value
WHERE d.dict_code IS NULL;

-- ---------------------------------------------------------------------
-- 7.1 授权类型枚举收敛归一（2026-09-30 用户决策，幂等可重跑）
-- ---------------------------------------------------------------------
-- negotiable「可议价」从授权类型中退役：议价是所有作品共有的能力，由 negotiable_min/max
-- 表达区间，不再是一种授权类型。
-- 依据：分工第 4 条「完成授权类型、授权价格和可议价范围展示」；附件6.1 接口 2.25
-- 只给出示例值 exclusive，从未将 negotiable 列为授权类型取值。
-- 存量脏值统一归一为 non_exclusive（非独家，库中占比最高的取值）。
-- 字典项沿用既有惯例（2026-09-29 trade_inquiry_status.accepted 先例）停用而非物理删除，
-- 保留一行可回滚记录；停用后若依字典下拉与前端均不再出现该项。
-- 注：sys_copyright_authorization.license_type 属 D 版权财务域（当前 0 行），按分工边界不在此动。
UPDATE sys_work    SET trade_type   = 'non_exclusive' WHERE trade_type   = 'negotiable';
UPDATE sys_inquiry SET license_type = 'non_exclusive' WHERE license_type = 'negotiable';
UPDATE sys_quote   SET license_type = 'non_exclusive' WHERE license_type = 'negotiable';
UPDATE sys_order   SET license_type = 'non_exclusive' WHERE license_type = 'negotiable';
UPDATE sys_dict_data
   SET status = '1', update_by = 'c-dict-migration', update_time = NOW(),
       remark = '2026-09-30 退役：议价改为所有作品共有能力，由 negotiable_min/max 表达区间'
 WHERE dict_type = 'trade_license_type' AND dict_value = 'negotiable';

-- =====================================================================
-- 第 8 部分：询盘过期自动关闭定时任务（幂等）
-- =====================================================================
INSERT INTO sys_job
  (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT 'C-询盘过期自动关闭', 'DEFAULT', 'tradeInquiryTask.closeExpiredInquiries',
       '0 0/10 * * * ?', '3', '1', '0', 'c-quartz-migration', NOW(),
       'P1-08：每10分钟扫描 expire_at 过期且非终态的询盘置 closed'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries');

-- =====================================================================
-- 8.1 存量菜单清理（新库种子已是最终状态；保留旧库升级兼容，幂等）
-- ---------------------------------------------------------------------
-- 系统管理已恢复用户/角色/菜单/部门/岗位/字典/参数七个页面。
-- 本段只清理已迁移的旧公告、日志入口，不删除系统管理根目录和管理权限。
-- 日志保留在数据统计；公告使用平台运维管理的消息与公告。
UPDATE sys_menu SET parent_id = 5002, order_num = 99 WHERE menu_id = 500 AND menu_type = 'C';
UPDATE sys_menu SET parent_id = 5002, order_num = 100 WHERE menu_id = 501 AND menu_type = 'C';
DELETE FROM sys_role_menu WHERE menu_id IN (
  SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE menu_id IN (107,108) OR parent_id IN (107,108)) t
);
DELETE FROM sys_menu WHERE menu_id IN (107,108) OR parent_id IN (107,108);
DELETE FROM sys_role_menu WHERE role_id = 2 AND (menu_id IN (107,108) OR menu_id BETWEEN 1035 AND 1038);

-- 4) 删除 A1 种子遗留的 5001 内容与作品树（幂等）
--    第 4 部分只把 5001 那行改写为 2000，对从 A1 基线升级上来的库不会移除旧行，
--    于是 5001 与 2000 两个目录都写 path='content'。前端按 path 作 el-sub-menu 的
--    index，两个目录共用一个展开状态，点一个另一个跟着伸缩（子项 category/tag/ranking
--    同样冲突）。此树是第 4 部分不定义的树，新库本来就没有。
DELETE FROM sys_role_menu WHERE menu_id IN (
  SELECT menu_id FROM (
    SELECT menu_id FROM sys_menu
    WHERE menu_id = 5001 OR parent_id = 5001
       OR parent_id IN (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id = 5001) d)
  ) t
);
DELETE FROM sys_menu
WHERE menu_id = 5001 OR parent_id = 5001
   OR parent_id IN (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id = 5001) d);

-- 5) 删除 A3「用户沟通」目录（幂等）
--    会话列表/会话详情已迁入用户中心（第 4 部分改 parent_id=3000），顶层目录 5200 不再需要。
--    INSERT 是 upsert，对已存在的库不会移除该行，故此处显式删除（含其角色绑定）。
DELETE FROM sys_role_menu WHERE menu_id = 5200;
DELETE FROM sys_menu WHERE menu_id = 5200;

-- 6) 外部视频「关联剧本 / 上下架管理 / 播放数据」三页并入「视频内容管理」(2019)（幂等）
--    三页功能已分别并入 2019 的详情弹窗与列表操作：
--      关联剧本 → 详情「关联剧本」Tab（dramaBind/bind、dramaBind/unbind）
--      上下架   → 列表「上下架」开关 + 「同步」行操作 + 「批量同步」（dramaStatus/changeStatus、sync）
--      播放数据 → 详情「播放数据」Tab（dramaStats/{workId}、dramaStats/history）
--    第 4 部分不再生成这三页，也不再生成废弃按钮 2046/2048，并把 2047/2049/2050 改挂 2019。
--    旧库升级必须先把原持有者的权限平移到合并后的页面，否则受限角色会失去入口
--    （超管权限会掩盖这个问题）：页面入口 2027/2028/2029 → 2019，并补齐父目录 2000、2012
--    菜单才能渲染出来；详情按钮 2043（content:drama:query）是进入「关联剧本 / 播放数据」
--    Tab 的唯一入口，原持 2027/2029 的角色必须一并获得，否则保留的 2047/2050 也无法触达；
--    「播放历史」2052 只补给原持 2029 的角色，不扩大其他角色的操作范围。
--    后端 /dramaBind、/dramaStatus、/dramaStats 接口全部保留，仅收敛入口。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, t.target_id
FROM sys_role_menu rm
JOIN (
            SELECT 2027 AS legacy_id, 2000 AS target_id
  UNION ALL SELECT 2027, 2012 UNION ALL SELECT 2027, 2019 UNION ALL SELECT 2027, 2043
  UNION ALL SELECT 2028, 2000 UNION ALL SELECT 2028, 2012 UNION ALL SELECT 2028, 2019
  UNION ALL SELECT 2029, 2000 UNION ALL SELECT 2029, 2012 UNION ALL SELECT 2029, 2019
  UNION ALL SELECT 2029, 2043 UNION ALL SELECT 2029, 2052
) t ON t.legacy_id = rm.menu_id;
DELETE FROM sys_role_menu WHERE menu_id IN (2027, 2028, 2029, 2046, 2048);
DELETE FROM sys_menu WHERE menu_id IN (2027, 2028, 2029, 2046, 2048);

-- =====================================================================
-- 按目录和名称识别平台运维管理下重复的用户与创作者入口；不能按 5142 删除，
-- 因云端 5142 是版权申请详情按钮。用户中心作者能力页并入 3001，保留 3009 按钮。
-- 旧页面的角色只迁移页面入口及父目录授权；不新增用户查询/状态/角色授权按钮。
START TRANSACTION;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT old.role_id, target.menu_id
FROM sys_role_menu old
JOIN sys_menu legacy ON legacy.menu_id = old.menu_id
JOIN sys_menu target ON target.menu_id IN (3000,3001)
WHERE (legacy.parent_id = 5005 AND legacy.menu_name IN ('用户与创作者','用户与创作者管理') AND legacy.menu_type = 'C')
   OR (legacy.parent_id = 3000 AND legacy.menu_name = '作者能力' AND legacy.path = 'creator' AND legacy.menu_type = 'C');
UPDATE sys_menu button_menu
JOIN sys_menu legacy ON legacy.menu_id = button_menu.parent_id
SET button_menu.parent_id = 3001, button_menu.order_num = 4
WHERE button_menu.perms = 'user:creator:update'
  AND legacy.parent_id = 3000 AND legacy.menu_name = '作者能力'
  AND legacy.path = 'creator' AND legacy.menu_type = 'C';
DELETE FROM sys_role_menu WHERE menu_id IN (
  SELECT doomed_id FROM (
    SELECT parent.menu_id AS doomed_id FROM sys_menu parent
    WHERE parent.parent_id = 5005 AND parent.menu_name IN ('用户与创作者','用户与创作者管理') AND parent.menu_type = 'C'
    UNION
    SELECT child.menu_id FROM sys_menu child JOIN sys_menu parent ON parent.menu_id = child.parent_id
    WHERE parent.parent_id = 5005 AND parent.menu_name IN ('用户与创作者','用户与创作者管理') AND parent.menu_type = 'C'
    UNION
    SELECT parent.menu_id FROM sys_menu parent
    WHERE parent.parent_id = 3000 AND parent.menu_name = '作者能力' AND parent.path = 'creator' AND parent.menu_type = 'C'
    UNION
    SELECT child.menu_id FROM sys_menu child JOIN sys_menu parent ON parent.menu_id = child.parent_id
    WHERE parent.parent_id = 3000 AND parent.menu_name = '作者能力' AND parent.path = 'creator' AND parent.menu_type = 'C'
  ) doomed
);
DELETE FROM sys_menu WHERE menu_id IN (
  SELECT doomed_id FROM (
    SELECT parent.menu_id AS doomed_id FROM sys_menu parent
    WHERE parent.parent_id = 5005 AND parent.menu_name IN ('用户与创作者','用户与创作者管理') AND parent.menu_type = 'C'
    UNION
    SELECT child.menu_id FROM sys_menu child JOIN sys_menu parent ON parent.menu_id = child.parent_id
    WHERE parent.parent_id = 5005 AND parent.menu_name IN ('用户与创作者','用户与创作者管理') AND parent.menu_type = 'C'
    UNION
    SELECT parent.menu_id FROM sys_menu parent
    WHERE parent.parent_id = 3000 AND parent.menu_name = '作者能力' AND parent.path = 'creator' AND parent.menu_type = 'C'
    UNION
    SELECT child.menu_id FROM sys_menu child JOIN sys_menu parent ON parent.menu_id = child.parent_id
    WHERE parent.parent_id = 3000 AND parent.menu_name = '作者能力' AND parent.path = 'creator' AND parent.menu_type = 'C'
  ) doomed
);
COMMIT;

-- =====================================================================
-- 第 9 部分：消息与公告接收范围、业务权限（2026-10-09，幂等）
-- 历史公告始终默认 ADMIN；本段不把它们公开给普通用户。
-- 必须在系统菜单精简之后执行，以保留公告权限的新父节点。
-- =====================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND COLUMN_NAME = 'audience'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_notice ADD COLUMN audience VARCHAR(8) NOT NULL DEFAULT ''ADMIN'' COMMENT ''USER普通用户/ADMIN后台管理员/ALL全部'' AFTER status',
  'SELECT ''notice audience col ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @chk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice'
    AND CONSTRAINT_NAME = 'ck_sys_notice_audience' AND CONSTRAINT_TYPE = 'CHECK'
);
SET @ddl := IF(@chk_exists = 0,
  'ALTER TABLE sys_notice ADD CONSTRAINT ck_sys_notice_audience CHECK (audience IN (''USER'',''ADMIN'',''ALL''))',
  'SELECT ''notice audience check ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notice' AND INDEX_NAME = 'idx_notice_status_audience'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_notice ADD INDEX idx_notice_status_audience (status, audience, notice_id)',
  'SELECT ''notice visibility index ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(5300, '公告列表', 5152, 1, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'system:notice:list', '#', 'notice_upgrade', '消息与公告权限'),
(5301, '公告详情', 5152, 2, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'notice_upgrade', '消息与公告权限'),
(5302, '公告新增', 5152, 3, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'notice_upgrade', '消息与公告权限'),
(5303, '公告修改', 5152, 4, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'notice_upgrade', '消息与公告权限'),
(5304, '公告删除', 5152, 5, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'notice_upgrade', '消息与公告权限'),
(5305, '消息列表', 5152, 6, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'user:message:list', '#', 'notice_upgrade', '消息与公告权限'),
(5306, '消息详情', 5152, 7, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'user:message:query', '#', 'notice_upgrade', '消息与公告权限'),
(5307, '消息发送', 5152, 8, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'user:message:add', '#', 'notice_upgrade', '消息与公告权限')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), parent_id=VALUES(parent_id),
  order_num=VALUES(order_num), perms=VALUES(perms), remark=VALUES(remark);
-- 不自动扩大任何受限角色权限。超级管理员依若依 *:*:* 使用新按钮。
-- 回退：先停用 USER/ALL 公告并回退应用；随后移除5300-5307菜单及其角色绑定、
-- 恢复5152旧组件，删除新增索引/约束/列。回退列会丢失范围信息，应先导出保存。

-- =====================================================================
-- 第 10 部分：用户消息入口与原角色授权迁移（幂等）
-- =====================================================================
-- 用户中心的用户消息迁入平台运维管理 / 消息与公告。
-- 随初始化/升级流程执行；只修改菜单及角色菜单授权，不修改消息数据。
-- 部署配套 PC 前端后，管理员退出并重新登录以刷新菜单和权限。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(5005, '平台运维管理', 0, 5, 'operation', NULL, '', '', 1, 0, 'M', '0', '0', NULL, 'Operation', 'message_menu_upgrade', '平台运维管理'),
(5152, '消息与公告', 5005, 5, '/support/messages', 'support/MessagesAnnouncements', '', '', 1, 0, 'C', '0', '0', 'smartscript:support:messages', 'Bell', 'message_menu_upgrade', '统一消息与公告入口'),
(5305, '消息列表', 5152, 6, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'user:message:list', '#', 'message_menu_upgrade', '消息与公告权限'),
(5306, '消息详情', 5152, 7, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'user:message:query', '#', 'message_menu_upgrade', '消息与公告权限'),
(5307, '消息发送', 5152, 8, '#', NULL, '', '', 1, 0, 'F', '0', '0', 'user:message:add', '#', 'message_menu_upgrade', '消息与公告权限')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), parent_id=VALUES(parent_id),
  order_num=VALUES(order_num), path=VALUES(path), component=VALUES(component),
  menu_type=VALUES(menu_type), visible=VALUES(visible), status=VALUES(status),
  perms=VALUES(perms), icon=VALUES(icon), remark=VALUES(remark);

-- 逐项迁移原权限：列表、发送、详情分别映射，受限角色不会获得额外操作权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, CASE menu_id WHEN 3010 THEN 5305 WHEN 3011 THEN 5307 WHEN 3012 THEN 5306 END
FROM sys_role_menu WHERE menu_id IN (3010, 3011, 3012);

-- 新旧消息权限均补齐可见入口和父目录。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, entry.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 5005 AS menu_id UNION ALL SELECT 5152) entry
WHERE rm.menu_id IN (3010, 3011, 3012, 5305, 5306, 5307);

-- 与初始化脚本中的 admin 角色默认消息权限保持一致。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, entry.menu_id FROM sys_role r
CROSS JOIN (SELECT 5005 AS menu_id UNION ALL SELECT 5152 UNION ALL SELECT 5305
  UNION ALL SELECT 5306 UNION ALL SELECT 5307) entry
WHERE r.role_key = 'admin' AND r.del_flag = '0';

DELETE FROM sys_role_menu WHERE menu_id IN (3010, 3011, 3012);
DELETE FROM sys_menu WHERE menu_id IN (3010, 3011, 3012);
COMMIT;


-- =====================================================================
-- 第 11 部分：用户中心会话列表退役（幂等）。保留原菜单 ID 和逐项授权，
-- 查询及操作权限挂到消息与公告；会话详情仍为隐藏路由，业务数据不变。
-- =====================================================================
START TRANSACTION;
UPDATE sys_menu SET parent_id = 5152, menu_type = 'F', path = '#', component = NULL,
  route_name = '', icon = '#', remark = '消息与公告用户沟通查询权限'
WHERE menu_id = 5201 AND perms = 'chat:session:list';

UPDATE sys_menu SET parent_id = 5152
WHERE parent_id = 5201 AND menu_type = 'F';

-- 原会话查询角色补齐新的页面入口和父目录，不授予额外沟通或公告操作权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, entry.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 5005 AS menu_id UNION ALL SELECT 5152) entry
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms = 'chat:session:list';
COMMIT;

-- =====================================================================
-- 第 12 部分：App 用户名称唯一索引（幂等）
-- =====================================================================
-- App 业务名称唯一；管理员昵称和逻辑删除账号不参与名称占用。
-- 生成列沿用 sys_user 的排序规则，首尾空格及大小写不构成不同名称。
-- 有历史重复名称的数据库需先处理冲突，再执行本段；不自动改写用户资料。
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'app_display_name'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN app_display_name VARCHAR(30) GENERATED ALWAYS AS (CASE WHEN user_type IN (''01'',''02'',''03'') AND del_flag = ''0'' THEN NULLIF(TRIM(nick_name), '''') ELSE NULL END) VIRTUAL',
  'SELECT ''app display name column ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_app_display_name'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_user ADD UNIQUE INDEX uk_sys_user_app_display_name (app_display_name)',
  'SELECT ''app display name unique index ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 12.1 阶段 C：存量库列/索引补齐（对齐线上；幂等，不删改既有数据）
-- ---------------------------------------------------------------------
-- 线上比旧初始化多出的列与索引；短剧/交易历史库同样适用。
-- 已退役的 5 个列（sys_work.core_setting/character_setting、
-- user_notification.template_code/template_params、sys_ai_write_record.reference_doc_ids）
-- 与 6 个冗余索引不再创建，旧库保持原样、不做破坏性删除。
-- =====================================================================
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'real_name');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN real_name varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''实名认证通过后的真实姓名快照（合同签署域使用）'' AFTER app_display_name',
  'SELECT ''sys_user.real_name ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work' AND COLUMN_NAME = 'channel_id');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_work ADD COLUMN channel_id int DEFAULT NULL COMMENT ''所属频道：146男频 147女频'' AFTER genre_id',
  'SELECT ''sys_work.channel_id ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tag' AND COLUMN_NAME = 'group_name');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_tag ADD COLUMN group_name varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''标签分组'' AFTER tag_type, ADD COLUMN channel_id bigint DEFAULT NULL COMMENT ''所属频道ID（NULL=通用）'' AFTER group_name',
  'SELECT ''sys_tag group/channel cols ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_demand：任务/托管/审核字段（缺 escrow_status 会让 idx_square 建不起来）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'escrow_status');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_demand
     ADD COLUMN tags varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL AFTER genre_id,
     ADD COLUMN source varchar(32) COLLATE utf8mb4_general_ci DEFAULT ''manual'' COMMENT ''来源(manual/auto)'' AFTER remark,
     ADD COLUMN total_episodes int DEFAULT NULL COMMENT ''总集数(如24)'' AFTER auditor_id,
     ADD COLUMN accepted_creator_id bigint DEFAULT NULL COMMENT ''接单人用户ID'' AFTER total_episodes,
     ADD COLUMN accepted_at datetime DEFAULT NULL COMMENT ''接单时间'' AFTER accepted_creator_id,
     ADD COLUMN duration_hours int DEFAULT NULL COMMENT ''接单后完成时限(小时)'' AFTER accepted_at,
     ADD COLUMN due_at datetime DEFAULT NULL COMMENT ''应完成时间=accepted_at+duration_hours'' AFTER duration_hours,
     ADD COLUMN is_overdue tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否逾期:0否1是'' AFTER due_at,
     ADD COLUMN escrow_amount decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT ''托管金额(元)=预算'' AFTER is_overdue,
     ADD COLUMN escrow_status varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''none'' COMMENT ''托管状态'' AFTER escrow_amount,
     ADD COLUMN deposit_amount decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT ''定金金额(元)=floor(预算/10)'' AFTER escrow_status,
     ADD COLUMN deposit_status varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''none'' COMMENT ''定金状态'' AFTER deposit_amount,
     ADD COLUMN deposit_paid_at datetime DEFAULT NULL COMMENT ''定金入账时间'' AFTER deposit_status,
     ADD COLUMN deposit_settled_at datetime DEFAULT NULL COMMENT ''定金结算时间'' AFTER deposit_paid_at,
     ADD COLUMN fail_reason varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''失败原因'' AFTER deposit_settled_at,
     ADD COLUMN fail_at datetime DEFAULT NULL COMMENT ''失败终止时间'' AFTER fail_reason',
  'SELECT ''sys_demand task/escrow cols ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_tag：名称+类型+频道唯一（一个标签名可在不同频道重复），并补标签导航索引
SET @uk_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tag' AND INDEX_NAME = 'uk_tag_name_type_channel');
SET @uk_old := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tag' AND INDEX_NAME = 'uk_tag_name');
SET @ddl := IF(@uk_exists = 0 AND @uk_old > 0,
  'ALTER TABLE sys_tag DROP INDEX uk_tag_name, ADD UNIQUE KEY uk_tag_name_type_channel (tag_name, tag_type, channel_id) USING BTREE',
  IF(@uk_exists = 0,
     'ALTER TABLE sys_tag ADD UNIQUE KEY uk_tag_name_type_channel (tag_name, tag_type, channel_id) USING BTREE',
     'SELECT ''sys_tag uk ok'' AS note'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tag' AND INDEX_NAME = 'idx_tag_nav');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_tag ADD INDEX idx_tag_nav (status, channel_id, tag_type, sort) USING BTREE',
  'SELECT ''sys_tag nav index ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冗余/重复索引（线上已删）：主键可替代的重复唯一键、同列重复普通索引、组合索引前缀
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_inquiry' AND INDEX_NAME = 'uk_inquiry_id');
SET @ddl := IF(@idx_exists > 0,
  'ALTER TABLE sys_inquiry DROP INDEX uk_inquiry_id',
  'SELECT ''sys_inquiry redundant uk ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_quote' AND INDEX_NAME = 'idx_inquiry_role');
SET @ddl := IF(@idx_exists > 0,
  'ALTER TABLE sys_quote DROP INDEX idx_inquiry_role',
  'SELECT ''sys_quote redundant index ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_episode' AND INDEX_NAME = 'idx_work_id');
SET @ddl := IF(@idx_exists > 0,
  'ALTER TABLE sys_episode DROP INDEX idx_work_id',
  'SELECT ''sys_episode redundant index ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 第 14 部分：版权域菜单修复（幂等，新库与已有库共用）
-- 现象：角色管理 → 编辑的菜单树里出现 ????，以及「公告详情」下的合同按钮。
-- 成因（均为早期一次性脚本 add_withdraw_contract_menus.sql / add_contract_menu_new.sql 写入）：
--   1) 脚本未按 UTF-8 执行，「提现解冻 / 提现导出 / 合同作废」入库成了四个半角问号；
--   2) add_contract_menu_new.sql 把「合同管理」与按钮写到 5301 时，5301 已被第 9 部分的
--      「公告详情」占用；而真正的合同页面是 5231（版权审核管理下，ID 段从 5000 起）。
--      于是 5308「合同作废」挂在了公告详情下，其余按钮要么落空、要么原地残留；
--      5301 的菜单元数据被覆盖成合同页面，但权限串没跟着改。
--   3) 这批按钮的 perms 是 smartscript:copyright:contract:xxx，而后端
--      ContractManageController / WithdrawReviewController 校验的是
--      copyright:contract:xxx / smartscript:copyright:withdraw:xxx，权限串对不上。
-- 处理：只补正名称、把错位的合同按钮挂回合同页；按后端实际校验口径补齐缺失权限位
--   （5401–5404 提现按钮、5419–5423 合同按钮；5405–5418 已被其它批次占用），
--   并授予当前能打开对应页面的角色（与第 5 部分同一口径：菜单可见性与按钮权限同步，
--   不扩大任何角色的入口）。提现审核 / 合同管理两页此前只由一次性脚本建出、未纳入
--   本文件，新库缺页时一并补建（老库按路由命中现有页，不重复建）。
-- 不删记录：公告详情（5301）恢复只读权限、合同作废（5308）改名并改挂 5231，
--   两者的角色绑定随之一并生效。
-- =====================================================================
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- 14.0 5301 曾被 add_contract_menu_new.sql 覆盖成合同页面（component/path/perms 全错），
--      按第 9 部分的原始口径恢复为「公告详情」按钮行，父节点回到消息与公告 5152。
UPDATE sys_menu
SET menu_name = '公告详情', parent_id = 5152, order_num = 2, path = '#', component = NULL,
    menu_type = 'F', perms = 'system:notice:query', icon = '#'
WHERE menu_id = 5301 AND NOT (menu_type = 'F' AND perms = 'system:notice:query' AND parent_id = 5152);

-- 14.1 被编码损坏的按钮名（HEX 比对，避免脚本本身再次被错误编码时口径失真）
UPDATE sys_menu SET menu_name = '提现解冻'
WHERE menu_id = 5220 AND HEX(menu_name) = '3F3F3F3F'
  AND perms = 'smartscript:copyright:withdraw:unfreeze';
UPDATE sys_menu SET menu_name = '提现导出'
WHERE menu_id = 5221 AND HEX(menu_name) = '3F3F3F3F'
  AND perms = 'smartscript:copyright:withdraw:export';
UPDATE sys_menu SET menu_name = '合同作废', parent_id = 5231
WHERE menu_id = 5308 AND HEX(menu_name) = '3F3F3F3F'
  AND perms = 'smartscript:copyright:contract:cancel';

-- 14.2 提现按钮（提现审核页）：后端校验 smartscript:copyright:withdraw:*
-- 提现审核页与合同页此前只由一次性脚本建出（未纳入本文件），新库缺页，按真实路由补建；
-- 已有页（老库 5209 / 5231）时 @X_page 直接命中，不会重复建页。
SET @withdraw_page := (SELECT menu_id FROM sys_menu
                       WHERE path = 'withdraw-review' AND menu_type = 'C' LIMIT 1);
SET @copyright_root := (SELECT menu_id FROM sys_menu
                        WHERE path = 'copyright' AND parent_id = 0 AND menu_type = 'M' LIMIT 1);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5320, '提现审核', @copyright_root, 11, 'withdraw-review', 'copyright/WithdrawReview', '', '',
       1, 0, 'C', '0', '0', 'smartscript:copyright:withdraw:list', 'money', 'menu-repair', 'D模块-提现审核页面'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) m WHERE m.menu_id = 5320)
  AND @withdraw_page IS NULL AND @copyright_root IS NOT NULL;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 5320 FROM sys_role r WHERE r.role_key = 'admin' AND r.del_flag = '0';
SET @withdraw_page := (SELECT menu_id FROM sys_menu
                       WHERE path = 'withdraw-review' AND menu_type = 'C' LIMIT 1);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5401, '提现查询', @withdraw_page, 1, '', NULL, '', '', 1, 0, 'F', '0', '0',
       'smartscript:copyright:withdraw:query', '#', 'menu-repair', '提现详情查询（对齐后端校验口径）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5401);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5402, '审核通过', @withdraw_page, 2, '', NULL, '', '', 1, 0, 'F', '0', '0',
       'smartscript:copyright:withdraw:approve', '#', 'menu-repair', '提现审核通过（对齐后端校验口径）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5402);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5403, '审核驳回', @withdraw_page, 3, '', NULL, '', '', 1, 0, 'F', '0', '0',
       'smartscript:copyright:withdraw:reject', '#', 'menu-repair', '提现审核驳回（对齐后端校验口径）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5403);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5404, '提现冻结', @withdraw_page, 4, '', NULL, '', '', 1, 0, 'F', '0', '0',
       'smartscript:copyright:withdraw:freeze', '#', 'menu-repair', '冻结已通过提现（对齐后端校验口径）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5404);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, t.target_id
FROM sys_role_menu rm
JOIN (          SELECT @withdraw_page AS source_id, 5401 AS target_id
  UNION ALL SELECT @withdraw_page, 5402 UNION ALL SELECT @withdraw_page, 5403
  UNION ALL SELECT @withdraw_page, 5404
) t ON t.source_id = rm.menu_id
WHERE @withdraw_page IS NOT NULL;

-- 14.3 合同按钮：线上合同页 5231 及其按钮 5232-5236 / 5308 由第 18 部分权威 upsert，
--      这里只补历史库缺失页/缺按钮；用显式 ID 5419-5423，重复执行不会产生重复按钮。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT t.mid, t.btn_name, p.menu_id, t.ord, '', NULL, '', '', 1, 0, 'F', '0', '0', t.perms, '#', 'menu-repair', t.remark
FROM (          SELECT 5419 AS mid, '合同列表' AS btn_name, 9 AS ord, 'copyright:contract:list' AS perms, '合同列表/预览/下载（对齐后端校验口径）' AS remark
  UNION ALL SELECT 5420, '生成合同', 10, 'copyright:contract:generate', '生成电子合同（对齐后端校验口径）'
  UNION ALL SELECT 5421, '签署登记', 11, 'copyright:contract:sign', '登记合同签署状态（对齐后端校验口径）'
  UNION ALL SELECT 5422, '合同归档', 12, 'copyright:contract:archive', '合同归档（对齐后端校验口径）'
  UNION ALL SELECT 5423, '合同作废', 13, 'copyright:contract:cancel', '合同作废（对齐后端校验口径）') t
JOIN sys_menu p ON p.menu_id = 5231 AND p.menu_type = 'C'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = t.mid)
  AND NOT EXISTS (SELECT 1 FROM (SELECT perms FROM sys_menu) x WHERE x.perms = t.perms);
-- 不额外授予按钮：授权以第 18 部分的 372 条权威授权为准（5419-5423 不在此列）。

-- =====================================================================
-- 第 13 部分：恢复完整系统管理（幂等，新库与已有库共用）
-- 保留现有系统根目录 ID；新增菜单不会扩大受限角色的操作权限。
-- =====================================================================
START TRANSACTION;
SET @system_root := (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='system' AND menu_type='M' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark)
SELECT '1', '系统管理', '0', '90', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录' FROM DUAL WHERE @system_root IS NULL;
SET @system_root := (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='system' AND menu_type='M' ORDER BY menu_id LIMIT 1);
UPDATE sys_menu SET menu_name='系统管理',visible='0',status='0',order_num=90 WHERE menu_id=@system_root;
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,update_by,update_time,remark) VALUES
('100',  '用户管理', @system_root,   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单'),
('101',  '角色管理', @system_root,   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单'),
('102',  '菜单管理', @system_root,   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单'),
('103',  '部门管理', @system_root,   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单'),
('104',  '岗位管理', @system_root,   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单'),
('105',  '字典管理', @system_root,   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单'),
('106',  '参数设置', @system_root,   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单'),
('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, ''),
('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, ''),
('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, ''),
('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, ''),
('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, ''),
('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, ''),
('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, ''),
('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, ''),
('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, ''),
('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, ''),
('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, ''),
('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, ''),
('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, ''),
('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, ''),
('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, ''),
('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, ''),
('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, ''),
('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, ''),
('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, ''),
('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, ''),
('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, ''),
('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, ''),
('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, ''),
('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, ''),
('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, ''),
('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, ''),
('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, ''),
('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, ''),
('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, ''),
('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, ''),
('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, ''),
('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, ''),
('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, ''),
('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, ''),
('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),parent_id=VALUES(parent_id),order_num=VALUES(order_num),
  path=VALUES(path),component=VALUES(component),menu_type=VALUES(menu_type),visible=VALUES(visible),
  status=VALUES(status),perms=VALUES(perms),icon=VALUES(icon);

-- 超级管理员角色恢复全部系统管理授权；其他角色保留原有逐项授权。
INSERT IGNORE INTO sys_role_menu (role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
  ON m.menu_id=@system_root OR m.menu_id BETWEEN 100 AND 106 OR m.menu_id BETWEEN 1000 AND 1034
WHERE r.del_flag='0' AND (r.role_id=1 OR r.role_key='admin');
INSERT IGNORE INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT role_id,@system_root FROM sys_role_menu WHERE menu_id BETWEEN 100 AND 106;
COMMIT;

-- =====================================================================
-- 收口自检
-- =====================================================================
SELECT 'SMARTSCRIPT_INIT_DONE' AS step,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
           ('app_sms_code','app_refresh_session','app_user_consent','app_user_oauth',
            'user_real_name_auth','user_phone_change_log','user_author_capability',
            'user_creator_profile',
            'user_notification','user_notification_receiver','user_notification_preference',
            'user_feedback')) AS app_tables,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
           ('sys_business_follow','sys_chat_message','sys_chat_session','sys_contact_profile',
            'sys_demand','sys_demand_submission','sys_inquiry','sys_offline_cooperation',
            'sys_order','sys_order_status_log','sys_partner','sys_quote',
            'sys_work','sys_demand_tag')) AS trade_tables,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015) AS a4_menus,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 5000 AND 5199) AS product_menus,
       (SELECT COUNT(*) FROM sys_role WHERE role_key = 'a1_operator') AS a1_role,
       (SELECT COUNT(DISTINCT dict_type) FROM sys_dict_data WHERE dict_type LIKE 'trade%') AS trade_dict_types,
       (SELECT COUNT(*) FROM sys_job WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries') AS inquiry_job,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'bio') AS bio_col;

-- 6) 校验：系统管理根目录与七个页面已恢复；旧日志目录(108)不存在；日志仍在数据统计；
--    5001 遗留树应清空；5200 用户沟通目录应不存在；同级目录 path 不得重复
--    （否则侧边栏展开状态会串）；外部视频三页 2027/2028/2029 与废弃按钮 2046/2048 应不存在，
--    其仍需的按钮权限应全部挂在「视频内容管理」2019 下
SELECT
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 108) AS deleted_log_dir_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id=0 AND path='system' AND menu_type='M') AS system_root_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 100 AND 106 AND parent_id=@system_root) AS system_pages_should_be_7,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (500,501) AND parent_id = 5002) AS logs_under_stats_should_be_2,
  (SELECT COUNT(*) FROM sys_menu
    WHERE menu_id = 5001 OR parent_id = 5001
       OR parent_id IN (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id = 5001) d)
  ) AS legacy_content_tree_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5200) AS chat_dir_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 5005 AND menu_type = 'C'
    AND (menu_name = '用户画像与推荐配置' OR component = 'operation/UserProfileRec')) AS retired_profile_menu_should_be_0,
  (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 5400
    AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 5400)) AS retired_profile_orphan_grants_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 5005 AND menu_name IN ('用户与创作者','用户与创作者管理') AND menu_type = 'C') AS duplicate_user_menu_should_be_0,
  (SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE m.parent_id = 5005 AND m.menu_name IN ('用户与创作者','用户与创作者管理') AND m.menu_type = 'C') AS duplicate_user_grants_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5142 AND parent_id = 5126 AND perms = 'content:copyright:application:query') AS copyright_detail_menu_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 3001 AND parent_id = 3000) AS app_users_under_user_center_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 3000 AND menu_name = '作者能力' AND path = 'creator' AND menu_type = 'C') AS duplicate_creator_menu_should_be_0,
  (SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE m.parent_id = 3000 AND m.menu_name = '作者能力' AND m.path = 'creator' AND m.menu_type = 'C') AS duplicate_creator_grants_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 3009 AND parent_id = 3001) AS creator_update_under_app_users_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (3010,3011,3012)) AS legacy_message_menus_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5152 AND parent_id = 5005) AS messages_under_operations_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 3000 AND component = 'chat/ChatSessions') AS legacy_chat_pages_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5201 AND parent_id = 3000 AND menu_type = 'C') AS chat_list_page_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2027,2028,2029,2046,2048)) AS retired_drama_menus_should_be_0,
  (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (2027,2028,2029,2046,2048)) AS retired_drama_grants_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 2019 AND menu_type = 'F'
    AND perms IN ('content:drama:query','content:drama:add','content:drama:edit')) AS drama_buttons_under_drama_should_be_3,
  (SELECT COUNT(*) FROM (
    SELECT parent_id, path FROM sys_menu
    WHERE menu_type IN ('M','C') AND status = '0'
    GROUP BY parent_id, path HAVING COUNT(*) > 1
  ) dup) AS duplicate_sibling_path_should_be_0;

-- 版权域菜单（第 14 部分）：不应再有问号名；公告详情不应挂合同按钮；
-- 合同按钮全部挂在合同页 5231 下且权限串与后端一致
SELECT
  (SELECT COUNT(*) FROM sys_menu WHERE menu_name LIKE '%?%') AS menu_names_with_question_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5301
     AND menu_type = 'F' AND parent_id = 5152 AND perms = 'system:notice:query') AS notice_detail_restored_should_be_1,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 5301) AS misplaced_contract_button_under_notice_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 5231 AND menu_type = 'F') AS contract_buttons_should_be_6;

-- 实名人工审核权限（已有角色需按需授权）。
UPDATE sys_menu SET menu_name = '实名审核' WHERE perms = 'user:realname:list';
INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by)
SELECT 5405,'实名审核决定',m.menu_id,2,'#','F','0','0','user:realname:audit','#','migration' FROM sys_menu m
WHERE m.perms = 'user:realname:list'
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5405)
  AND NOT EXISTS (SELECT 1 FROM (SELECT perms FROM sys_menu) x WHERE x.perms = 'user:realname:audit');
INSERT IGNORE INTO sys_role_menu (role_id,menu_id) SELECT 1,menu_id FROM sys_menu WHERE perms='user:realname:audit';


-- =====================================================================
-- 第 12 部分：交易域菜单收口、任务运营详情入口、需求超时定时任务（幂等）
-- =====================================================================
START TRANSACTION;

-- 12.1 退役四个已下线页面的菜单：交易作品 / 合作方 / 需求标签 / 商务跟进。
--      前端 component-map.js 只登记 AuthOrders / Inquiry / Quote / Demand 四页，
--      留着这几行会让新库出现「点了进不去」的死菜单；云端库此前已人工清理，此处补齐脚本口径。
DELETE FROM sys_role_menu WHERE menu_id IN (
  SELECT doomed_id FROM (
    SELECT menu_id AS doomed_id FROM sys_menu WHERE menu_id IN (5130, 5132, 5136, 5137)
    UNION
    SELECT child.menu_id FROM sys_menu child WHERE child.parent_id IN (5130, 5132, 5136, 5137)
  ) t
);
DELETE FROM sys_menu WHERE parent_id IN (5130, 5132, 5136, 5137);
DELETE FROM sys_menu WHERE menu_id IN (5130, 5132, 5136, 5137);

-- 12.2 任务运营详情页（逐轮验收 / 资金流水 / 延时记录，只读）。
--      隐藏菜单：列表页「运营详情」按钮按 query.demandId 跳入，不在侧边栏出现。
--      权限沿用已有的 trade:demand:query，不新增权限位。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5139, '任务运营详情', 5004, 9, 'demand-detail', 'trade/DemandDetail', '', 1, 0,
       'C', '1', '0', 'trade:demand:query', '#', 'c-migration', '逐轮验收/资金流水/延时记录只读运营视图'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5139);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 5139 FROM sys_role r WHERE r.role_key = 'admin' AND r.del_flag = '0';

-- 12.3 征集任务超时实时处置定时任务：原单脚本迁移 C_20261103_003 并入初始化，
--      否则新库只有询盘 job、没有需求超时 job，退回到「打开页面才判超时」的惰性口径。
INSERT INTO sys_job
  (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT 'C-任务超时实时处置', 'DEFAULT', 'tradeDemandTask.expireOverdueRounds',
       '0/10 * * * * ?', '3', '1', '0', 'c-quartz-migration', NOW(),
       '每10秒扫描：①驳回发布者超24小时未处理的延时申请 ②终止已过截止且仍在计时的活动轮(押金没收/托管退回发布者)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT invoke_target FROM sys_job) j WHERE j.invoke_target = 'tradeDemandTask.expireOverdueRounds');
COMMIT;

SELECT
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (5130, 5132, 5136, 5137)) AS retired_trade_menus_should_be_0,
  (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5139 AND component = 'trade/DemandDetail' AND visible = '1') AS demand_detail_menu_should_be_1,
  (SELECT COUNT(*) FROM sys_job WHERE invoke_target = 'tradeDemandTask.expireOverdueRounds') AS demand_timeout_job_should_be_1;

-- =====================================================================
-- 第 15 部分：现役表全量补齐（阶段 C，对齐线上 143 张现役表；幂等）
-- ---------------------------------------------------------------------
-- 现象：初始化文件长期只建 56 张表，AI/版权/结算/推广/沙箱等模块的表只存在于
--       一次性脚本或线上手工建表，空库初始化后业务启动即报表不存在。
-- 处理：按线上 `p06_platform` 结构导出逐表 CREATE TABLE IF NOT EXISTS；
--       去掉历史 AUTO_INCREMENT 值；已退役的 sys_selection 不再建表。
--       新建表统一 utf8mb4_general_ci（沙箱表原有的 utf8mb4_0900_ai_ci 一并归一）。
-- 已有库：仅补缺表，不修改既有表结构与数据。
-- =====================================================================
SET @OLD_FK_CHECKS := @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `QRTZ_BLOB_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `QRTZ_BLOB_TRIGGERS_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Blob类型的触发器表';

CREATE TABLE IF NOT EXISTS `QRTZ_CALENDARS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='日历信息表';

CREATE TABLE IF NOT EXISTS `QRTZ_CRON_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `QRTZ_CRON_TRIGGERS_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Cron类型的触发器表';

CREATE TABLE IF NOT EXISTS `QRTZ_FIRED_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='已触发的触发器表';

CREATE TABLE IF NOT EXISTS `QRTZ_JOB_DETAILS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='任务详细信息表';

CREATE TABLE IF NOT EXISTS `QRTZ_LOCKS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='存储的悲观锁信息表';

CREATE TABLE IF NOT EXISTS `QRTZ_PAUSED_TRIGGER_GRPS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='暂停的触发器表';

CREATE TABLE IF NOT EXISTS `QRTZ_SCHEDULER_STATE` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='调度器状态表';

CREATE TABLE IF NOT EXISTS `QRTZ_SIMPLE_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `QRTZ_SIMPLE_TRIGGERS_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='简单触发器的信息表';

CREATE TABLE IF NOT EXISTS `QRTZ_SIMPROP_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  CONSTRAINT `QRTZ_SIMPROP_TRIGGERS_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='同步机制的行锁表';

CREATE TABLE IF NOT EXISTS `QRTZ_TRIGGERS` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`) USING BTREE,
  CONSTRAINT `QRTZ_TRIGGERS_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `QRTZ_JOB_DETAILS` (`sched_name`, `job_name`, `job_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='触发器详细信息表';

CREATE TABLE IF NOT EXISTS `demand_sandbox_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `demand_id` bigint NOT NULL COMMENT '需求任务ID',
  `purpose` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '收款用途: escrow 托管 | deposit 定金',
  `payer_id` bigint NOT NULL COMMENT '付款人用户ID(发布者/接单人)',
  `amount` decimal(12,2) NOT NULL COMMENT '收款金额(元)',
  `out_trade_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '商户订单号(SDESC/SDDEP前缀)',
  `trade_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '支付宝交易号',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|PAID|CLOSED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_demand_purpose` (`demand_id`,`purpose`) USING BTREE,
  UNIQUE KEY `uk_demand_out_trade_no` (`out_trade_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='需求任务支付宝沙箱收款记录';

CREATE TABLE IF NOT EXISTS `sandbox_alipay_copyright_payment` (
  `pay_order_id` bigint NOT NULL,
  `out_trade_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `buyer_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `trade_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`pay_order_id`) USING BTREE,
  UNIQUE KEY `uk_copyright_sandbox_out_trade_no` (`out_trade_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='支付宝沙箱版权申请支付';

CREATE TABLE IF NOT EXISTS `sandbox_alipay_payment` (
  `order_id` bigint NOT NULL,
  `out_trade_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `buyer_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `trade_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`) USING BTREE,
  UNIQUE KEY `uk_sandbox_alipay_trade` (`out_trade_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='支付宝沙箱支付记录';

CREATE TABLE IF NOT EXISTS `sandbox_alipay_promotion_payment` (
  `apply_id` bigint NOT NULL,
  `out_trade_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `buyer_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `trade_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`apply_id`) USING BTREE,
  UNIQUE KEY `uk_sandbox_alipay_promotion_trade` (`out_trade_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='支付宝沙箱推广单支付记录';

CREATE TABLE IF NOT EXISTS `schema_migrations` (
  `version` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '迁移版本号，如 V1',
  `description` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '迁移说明',
  `checksum` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '文件校验和',
  `applied_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '执行人',
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`version`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据库迁移版本记录表';

CREATE TABLE IF NOT EXISTS `sys_ad_config` (
  `ad_id` int NOT NULL AUTO_INCREMENT,
  `category` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PLACEMENT',
  `ad_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_position` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_source` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_unit_id` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `is_enabled` tinyint NOT NULL,
  `frequency_limit` int NOT NULL,
  `daily_cap` int NOT NULL,
  `target_audience` json DEFAULT NULL,
  `watch_limit` int NOT NULL,
  `reward_points` int NOT NULL,
  `sort` int NOT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `image_url` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `link_url` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `duration_seconds` int NOT NULL DEFAULT '5',
  PRIMARY KEY (`ad_id`) USING BTREE,
  KEY `idx_ad_position` (`ad_position`) USING BTREE,
  KEY `idx_is_enabled` (`is_enabled`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='广告配置表';

CREATE TABLE IF NOT EXISTS `sys_ad_revenue` (
  `revenue_id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `work_id` bigint NOT NULL,
  `drama_id` bigint DEFAULT NULL,
  `ad_slot` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `impressions` int DEFAULT NULL,
  `clicks` int DEFAULT NULL,
  `revenue_amount` decimal(12,2) DEFAULT NULL,
  `channel` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`revenue_id`) USING BTREE,
  UNIQUE KEY `uk_date_work_slot` (`stat_date`,`work_id`,`ad_slot`) USING BTREE,
  KEY `idx_date` (`stat_date`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='广告收益流水表';

CREATE TABLE IF NOT EXISTS `sys_ai_channel` (
  `channel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '渠道ID',
  `channel_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '渠道名称（唯一）',
  `provider` varchar(30) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'openai-compatible' COMMENT '服务商（预留）',
  `base_url` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API 基址',
  `api_key` varchar(200) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '接口密钥（可空，空时不参与调用路由）',
  `models` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '可用模型列表（逗号分隔）',
  `default_model` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '默认模型（须属于 models）',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '全局默认渠道：1 是',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0启用 1停用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序（小者优先）',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`channel_id`) USING BTREE,
  UNIQUE KEY `uk_ai_channel_name` (`channel_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='AI 调用渠道/模型配置';

CREATE TABLE IF NOT EXISTS `sys_ai_quota_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `available_quota` int NOT NULL,
  `reserved_quota` int NOT NULL,
  `total_earned` bigint NOT NULL,
  `total_consumed` bigint NOT NULL,
  `total_refunded` bigint NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`account_id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_ai_quota_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `record_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `change_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `change_amount` int NOT NULL,
  `before_balance` int NOT NULL,
  `after_balance` int NOT NULL,
  `business_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`record_id`) USING BTREE,
  UNIQUE KEY `uk_record_no` (`record_no`) USING BTREE,
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`) USING BTREE,
  KEY `idx_user_created` (`user_id`) USING BTREE,
  KEY `idx_business` (`business_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_ai_request` (
  `request_id` bigint NOT NULL AUTO_INCREMENT,
  `request_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `capability` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `quota_cost` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `provider` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `prompt_hash` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `result_file_id` bigint DEFAULT NULL,
  `error_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`request_id`) USING BTREE,
  UNIQUE KEY `uk_request_no` (`request_no`) USING BTREE,
  KEY `idx_user_created` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_ai_review_rule` (
  `rule_id` int NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_key` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_value` text COLLATE utf8mb4_general_ci NOT NULL,
  `violation_level` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `threshold` decimal(5,2) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE KEY `uk_rule_key` (`rule_key`) USING BTREE,
  KEY `idx_rule_type` (`rule_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='AI审核规则配置表';

CREATE TABLE IF NOT EXISTS `sys_ai_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'skill ID',
  `skill_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'skill 名称（唯一）',
  `skill_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'write' COMMENT 'skill 类型：write（剧本/写作/润色）storyboard（分镜）',
  `file_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '上传的原始文件名',
  `file_content` mediumtext COLLATE utf8mb4_general_ci COMMENT 'skill 文件文本内容（UTF-8，单文件兼容口径）',
  `file_tree` mediumtext COLLATE utf8mb4_general_ci COMMENT '多文件 skill 包：@@@FILE:路径@@@ + 内容 顺序拼接',
  `file_count` int NOT NULL DEFAULT '1' COMMENT 'skill 包内文件数（单文件=1）',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_ai_skill_name` (`skill_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='AI 导演风格 skill 配置';

CREATE TABLE IF NOT EXISTS `sys_ai_write_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `write_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pov` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `language_style` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pace` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `input_prompt` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `output_content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `word_count` int NOT NULL,
  `ai_model` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `tokens_used` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`record_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='AI写作记录表';

CREATE TABLE IF NOT EXISTS `sys_bill_record` (
  `bill_id` bigint NOT NULL AUTO_INCREMENT,
  `bill_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `bill_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `balance_after` decimal(12,2) NOT NULL,
  `related_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`bill_id`) USING BTREE,
  UNIQUE KEY `uk_bill_no` (`bill_no`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_bill_type` (`bill_type`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='账单流水表';

CREATE TABLE IF NOT EXISTS `sys_blacklist` (
  `black_id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `target_value` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `black_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `operator_id` bigint NOT NULL,
  `duration` int NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `release_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `release_operator_id` bigint DEFAULT NULL,
  `release_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`black_id`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_expire_at` (`expire_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='黑名单表';

CREATE TABLE IF NOT EXISTS `sys_bookshelf_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `add_source` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `last_read_chapter_id` bigint DEFAULT NULL,
  `last_read_at` datetime DEFAULT NULL,
  `is_deleted` tinyint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_user_updated` (`user_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_chapter_review` (
  `chapter_id` bigint NOT NULL COMMENT '章节ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `episode_no` int DEFAULT NULL COMMENT '集号',
  `pass_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否通过（1通过 0需重写）',
  `score` int DEFAULT NULL COMMENT '通过项数 0-5',
  `issues` text COLLATE utf8mb4_general_ci COMMENT '未通过项明细（一行一项）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`chapter_id`) USING BTREE,
  KEY `idx_work_review` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='每集连贯性验收结果';

CREATE TABLE IF NOT EXISTS `sys_chat_trade_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类：price_param/price_text/progress/general',
  `content` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '定型文内容，含{price}占位符表示需填入金额',
  `role` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'both' COMMENT 'buyer/seller/both',
  `has_amount` tinyint NOT NULL DEFAULT '0' COMMENT '1=含{price}占位符，发送时须填入金额并同步更新报价',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`template_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='交易会话定型文模板';

CREATE TABLE IF NOT EXISTS `sys_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `episode_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `like_count` int NOT NULL,
  `reply_count` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`comment_id`) USING BTREE,
  KEY `idx_episode_id` (`episode_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='外部视频评论缓存表（仅在外部接口支持评论时启用）';

CREATE TABLE IF NOT EXISTS `sys_contract` (
  `contract_id` bigint NOT NULL AUTO_INCREMENT,
  `contract_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `external_contract_no` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `file_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `template_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `source_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'template' COMMENT '合同来源: template平台模板/uploaded用户上传PDF',
  `contract_content` longtext COLLATE utf8mb4_general_ci COMMENT '合同正文HTML；用户上传原件时为空',
  `contract_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `buyer_sign_status` tinyint NOT NULL,
  `seller_sign_status` tinyint NOT NULL,
  `buyer_sign_time` datetime DEFAULT NULL,
  `seller_sign_time` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `buyer_signature_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????????URL',
  `buyer_seal_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????URL',
  `seller_signature_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????????URL',
  `seller_seal_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????URL',
  `buyer_sign_pos` text COLLATE utf8mb4_general_ci COMMENT '买方签章定位JSON(上传件拖拽坐标)',
  `seller_sign_pos` text COLLATE utf8mb4_general_ci COMMENT '卖方签章定位JSON(上传件拖拽坐标)',
  `signed_pdf_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????PDF??URL',
  `source_pdf_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上传合同PDF文件URL',
  `pdf_page_count` int DEFAULT NULL COMMENT '上传PDF页数',
  `clauses_text` text COLLATE utf8mb4_general_ci COMMENT '可编辑的正文条款纯文本(null=模板默认)',
  PRIMARY KEY (`contract_id`) USING BTREE,
  UNIQUE KEY `uk_contract_no` (`contract_no`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='电子合同表';

CREATE TABLE IF NOT EXISTS `sys_contract_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `event_no` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_id` bigint NOT NULL,
  `provider` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `event_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `event_payload` json DEFAULT NULL,
  `process_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `retry_count` int NOT NULL,
  `event_time` datetime NOT NULL,
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`event_id`) USING BTREE,
  UNIQUE KEY `uk_event_no` (`event_no`) USING BTREE,
  KEY `idx_contract_status` (`contract_id`) USING BTREE,
  KEY `idx_retry` (`process_status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_copyright_application` (
  `apply_id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `copyright_center` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `external_apply_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cert_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cert_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attachment_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Application material file URL (single or multiple files separated by comma)',
  `originality_proof` text COLLATE utf8mb4_general_ci COMMENT 'Originality proof material URL (JSON array)',
  `author_identity_doc` text COLLATE utf8mb4_general_ci COMMENT 'Author identity document URL (JSON array)',
  `creation_process` text COLLATE utf8mb4_general_ci COMMENT 'Creation process material URL (JSON array)',
  `other_proof` text COLLATE utf8mb4_general_ci COMMENT 'Other supporting material URL (JSON array)',
  `rights_holder_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权利人姓名',
  `id_doc_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证件类型',
  `id_doc_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证件号码',
  `submit_time` datetime NOT NULL,
  `cert_time` datetime DEFAULT NULL,
  `sync_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `last_sync_time` datetime DEFAULT NULL,
  `config_id` bigint DEFAULT NULL COMMENT '版权中心配置ID',
  `word_count` int DEFAULT NULL COMMENT '计价字数快照',
  `fee_amount` decimal(12,2) DEFAULT NULL COMMENT '已付登记费快照',
  `pay_order_id` bigint DEFAULT NULL COMMENT '来源支付单',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `supplement_deadline` datetime DEFAULT NULL COMMENT '当前补正截止时间',
  `supplement_notice` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前补正要求摘要',
  PRIMARY KEY (`apply_id`) USING BTREE,
  UNIQUE KEY `uk_apply_no` (`apply_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_config_id` (`config_id`) USING BTREE,
  KEY `idx_user_status` (`user_id`,`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权确权申请表';

CREATE TABLE IF NOT EXISTS `sys_copyright_authorization` (
  `auth_id` bigint NOT NULL AUTO_INCREMENT,
  `auth_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `licensor_id` bigint NOT NULL,
  `licensee_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `license_scope` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`auth_id`) USING BTREE,
  UNIQUE KEY `uk_auth_no` (`auth_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_licensor_id` (`licensor_id`) USING BTREE,
  KEY `idx_licensee_id` (`licensee_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权授权记录表';

CREATE TABLE IF NOT EXISTS `sys_copyright_center_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `center_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '版权中心代码',
  `center_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '版权中心名称',
  `api_endpoint` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API端点',
  `api_key` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API密钥',
  `api_secret` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API密钥',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'enabled' COMMENT '状态',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `config_json` text COLLATE utf8mb4_general_ci COMMENT '扩展配置JSON',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE,
  UNIQUE KEY `uk_center_code` (`center_code`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权中心配置表';

CREATE TABLE IF NOT EXISTS `sys_copyright_cert` (
  `cert_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `cert_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `blockchain_hash` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cert_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `apply_time` datetime NOT NULL,
  `cert_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`cert_id`) USING BTREE,
  UNIQUE KEY `uk_cert_no` (`cert_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权存证表';

CREATE TABLE IF NOT EXISTS `sys_copyright_pay_order` (
  `pay_order_id` bigint NOT NULL AUTO_INCREMENT,
  `pay_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付单号',
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `config_id` bigint NOT NULL COMMENT '版权中心配置ID',
  `copyright_center` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '版权中心代码快照',
  `word_count` int NOT NULL DEFAULT '0' COMMENT '计价字数快照',
  `fee_amount` decimal(12,2) NOT NULL COMMENT '应付登记费快照',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'unpaid' COMMENT 'unpaid/paying/paid/closed',
  `expire_time` datetime NOT NULL COMMENT '超时关单时间（创建+24h）',
  `apply_id` bigint DEFAULT NULL COMMENT '支付成功后回填的申请ID',
  `payload_json` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请材料与表单快照 JSON',
  `paid_time` datetime DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`pay_order_id`) USING BTREE,
  UNIQUE KEY `uk_copyright_pay_no` (`pay_no`) USING BTREE,
  KEY `idx_copyright_pay_user` (`user_id`,`status`) USING BTREE,
  KEY `idx_copyright_pay_work` (`work_id`,`status`) USING BTREE,
  KEY `idx_copyright_pay_expire` (`status`,`expire_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权确权申请待支付单';

CREATE TABLE IF NOT EXISTS `sys_copyright_seal` (
  `seal_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `seal_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `seal_image_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `material_urls` text COLLATE utf8mb4_general_ci COMMENT 'Material URLs (comma-separated)',
  `review_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `seal_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'disabled' COMMENT 'disabled/enabled/abnormal',
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`seal_id`) USING BTREE,
  KEY `idx_copyright_seal_review` (`review_status`,`create_time`) USING BTREE,
  KEY `idx_copyright_seal_user` (`user_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权印章申请';

CREATE TABLE IF NOT EXISTS `sys_copyright_seal_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `seal_id` bigint NOT NULL,
  `operation_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'review_approve/review_reject/enable/disable',
  `from_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `to_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`) USING BTREE,
  KEY `idx_copyright_seal_log_seal` (`seal_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权印章审核与状态操作记录';

CREATE TABLE IF NOT EXISTS `sys_copyright_supplement` (
  `supplement_id` bigint NOT NULL AUTO_INCREMENT,
  `apply_id` bigint NOT NULL,
  `request_message_id` bigint DEFAULT NULL COMMENT '触发本轮补正的消息ID',
  `requirement_text` varchar(2000) COLLATE utf8mb4_general_ci NOT NULL COMMENT '补正要求说明',
  `attachment_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '第三方补正通知附件',
  `deadline_time` datetime NOT NULL COMMENT '补正截止（默认收通知+30天）',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'open' COMMENT 'open/submitted/overdue/closed',
  `materials_json` text COLLATE utf8mb4_general_ci COMMENT '用户提交的补正材料 JSON',
  `user_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `submit_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`supplement_id`) USING BTREE,
  KEY `idx_copyright_supp_apply` (`apply_id`,`status`) USING BTREE,
  KEY `idx_copyright_supp_deadline` (`status`,`deadline_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权申请补正单（同一申请内，不重交官费）';

CREATE TABLE IF NOT EXISTS `sys_copyright_third_party_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `external_message_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '第三方消息幂等ID',
  `apply_id` bigint DEFAULT NULL COMMENT '关联申请（可后置匹配）',
  `external_apply_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '第三方申请编号',
  `message_type` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'supplement_required/notice/certificated/rejected/other',
  `title` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '补正说明/通知正文',
  `attachment_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '如国版补正单 PDF',
  `requirement_json` text COLLATE utf8mb4_general_ci COMMENT '要求清单 JSON',
  `deadline_time` datetime DEFAULT NULL COMMENT '第三方给出的截止；空则平台按+30天',
  `payload_json` mediumtext COLLATE utf8mb4_general_ci COMMENT '原始回调 JSON',
  `process_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/processed/ignored/failed',
  `process_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `processed_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_copyright_tp_msg_ext` (`external_message_id`) USING BTREE,
  KEY `idx_copyright_tp_msg_apply` (`apply_id`) USING BTREE,
  KEY `idx_copyright_tp_msg_ext_apply` (`external_apply_no`) USING BTREE,
  KEY `idx_copyright_tp_msg_status` (`process_status`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权第三方代理回调消息';

CREATE TABLE IF NOT EXISTS `sys_creator_stats` (
  `stats_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `total_income` decimal(12,2) NOT NULL,
  `month_income` decimal(12,2) NOT NULL,
  `total_orders` int NOT NULL,
  `published_count` int NOT NULL,
  `reviewing_count` int NOT NULL,
  `sold_count` int NOT NULL,
  `total_views` bigint NOT NULL,
  `total_favorites` int NOT NULL,
  `avg_rating` decimal(3,1) NOT NULL,
  `stat_date` date NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`stats_id`) USING BTREE,
  UNIQUE KEY `uk_user_date` (`user_id`,`stat_date`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作者能力统计表';

CREATE TABLE IF NOT EXISTS `sys_delivery` (
  `delivery_id` bigint NOT NULL AUTO_INCREMENT COMMENT '交割ID',
  `delivery_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '交割单号',
  `order_id` bigint NOT NULL COMMENT '关联订单ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `buyer_id` bigint NOT NULL COMMENT '买方用户ID',
  `seller_id` bigint NOT NULL COMMENT '卖方用户ID',
  `amount` decimal(12,2) NOT NULL COMMENT '交易金额（元）',
  `license_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '授权类型',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending_upload' COMMENT '状态（pending_upload/pending_confirm/completed/cancelled）',
  `contract_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '合同编号（冗余展示）',
  `contract_sign_time` datetime DEFAULT NULL COMMENT '合同签署完成时间（冗余展示）',
  `cert_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '版权证书号（冗余展示）',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `upload_time` datetime DEFAULT NULL COMMENT '卖方提交交割时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '买方确认收货时间',
  `completed_time` datetime DEFAULT NULL COMMENT '交割完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`delivery_id`) USING BTREE,
  UNIQUE KEY `uk_delivery_no` (`delivery_no`) USING BTREE,
  UNIQUE KEY `uk_order_id` (`order_id`) USING BTREE,
  KEY `idx_buyer_id` (`buyer_id`) USING BTREE,
  KEY `idx_seller_id` (`seller_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权交割单';

CREATE TABLE IF NOT EXISTS `sys_delivery_file` (
  `file_id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `delivery_id` bigint NOT NULL COMMENT '交割ID',
  `file_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `file_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型（cert/source/doc/other）',
  `file_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件URL',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`file_id`) USING BTREE,
  KEY `idx_delivery_id` (`delivery_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='版权交割文件';

CREATE TABLE IF NOT EXISTS `sys_demand_extend_apply` (
  `apply_id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `demand_id` bigint NOT NULL COMMENT '所属征集',
  `stage_id` bigint NOT NULL COMMENT '所属验收轮次',
  `creator_id` bigint NOT NULL COMMENT '发起人(申请延时=接单创作者, 自主延时=发布者)',
  `days` int NOT NULL DEFAULT '0' COMMENT '申请延长天数',
  `hours` int NOT NULL DEFAULT '0' COMMENT '申请延长小时数',
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申请原因',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `apply_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'request' COMMENT '延时来源：request=创作者申请延时 / self=发布者自主延时',
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `decided_at` datetime DEFAULT NULL COMMENT '处理时间',
  `decided_by` bigint DEFAULT NULL COMMENT '处理人(发布者用户ID)',
  `due_after` datetime DEFAULT NULL COMMENT '本次动作生效后的轮次截止时间：自主延时/申请通过时写入，待处理与驳回为空',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`apply_id`) USING BTREE,
  KEY `idx_stage_status` (`stage_id`,`status`) USING BTREE,
  KEY `idx_demand` (`demand_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='任务轮次延时申请历史(每条申请一行, 通过/驳回均保留)';

CREATE TABLE IF NOT EXISTS `sys_demand_stage` (
  `stage_id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮次ID',
  `demand_id` bigint NOT NULL COMMENT '所属征集',
  `stage_order` int NOT NULL COMMENT '轮次序号(1,2,3...)',
  `episode_to` int NOT NULL COMMENT '本轮验收截止集数',
  `amount` decimal(12,2) NOT NULL COMMENT '本轮放款金额(元)',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/submitted/approved/rejected',
  `deliverable_desc` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创作者交付说明',
  `deliverable_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '交付物文件URL(可选)',
  `submitted_at` datetime DEFAULT NULL COMMENT '创作者提交时间',
  `reviewed_at` datetime DEFAULT NULL COMMENT '买方审核时间',
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '驳回原因',
  `release_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '放款标记: 0未放/1已发信号给支付模块',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新者',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `duration_days` int NOT NULL DEFAULT '0' COMMENT '本轮时限(天)',
  `duration_hours` int NOT NULL DEFAULT '0' COMMENT '本轮时限(小时)',
  `started_at` datetime DEFAULT NULL COMMENT '本轮开始时间',
  `due_at` datetime DEFAULT NULL COMMENT '本轮截止时间',
  `is_overdue` tinyint(1) NOT NULL DEFAULT '0' COMMENT '本轮是否逾期',
  `work_id` bigint DEFAULT NULL COMMENT '交付作品ID(sys_work)',
  `extend_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '延期申请状态',
  `extend_days` int DEFAULT NULL COMMENT '申请延长(天)',
  `extend_hours` int DEFAULT NULL COMMENT '申请延长(小时)',
  `extend_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '延期申请理由',
  `extend_applied_at` datetime DEFAULT NULL COMMENT '延期申请时间',
  `ai_score` int DEFAULT NULL COMMENT 'AI审核得分(0-100)',
  `ai_result` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'AI审核结论(JSON)',
  `ai_reviewed_at` datetime DEFAULT NULL COMMENT 'AI审核完成时间',
  PRIMARY KEY (`stage_id`) USING BTREE,
  UNIQUE KEY `uk_demand_stage` (`demand_id`,`stage_order`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='任务验收轮次';

CREATE TABLE IF NOT EXISTS `sys_draft` (
  `draft_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `auto_save` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`draft_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_updated_at` (`updated_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='草稿表';

CREATE TABLE IF NOT EXISTS `sys_drama_channel` (
  `channel_id` int NOT NULL AUTO_INCREMENT,
  `channel_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `channel_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `platform` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `account_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `account_id` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `api_config` json DEFAULT NULL,
  `is_auto_distribute` tinyint NOT NULL,
  `status` tinyint NOT NULL,
  `sort` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`channel_id`) USING BTREE,
  UNIQUE KEY `uk_channel_code` (`channel_code`) USING BTREE,
  KEY `idx_platform` (`platform`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='短剧发行渠道表';

CREATE TABLE IF NOT EXISTS `sys_episode_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_episode` (`user_id`,`episode_id`) USING BTREE,
  KEY `idx_episode_id` (`episode_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='外部视频点赞缓存表（仅在外部接口支持点赞时启用）';

CREATE TABLE IF NOT EXISTS `sys_escrow` (
  `escrow_id` bigint NOT NULL AUTO_INCREMENT,
  `escrow_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `paid_at` datetime DEFAULT NULL,
  `pay_channel` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `release_time` datetime DEFAULT NULL,
  `release_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `refund_amount` decimal(12,2) DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`escrow_id`) USING BTREE,
  UNIQUE KEY `uk_escrow_no` (`escrow_no`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='资金托管表';

CREATE TABLE IF NOT EXISTS `sys_external_drama` (
  `drama_id` bigint NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `external_content_id` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `cover_file_id` bigint DEFAULT NULL,
  `external_url` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `related_work_id` bigint DEFAULT NULL,
  `source_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `authorization_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `sync_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `copyright_note` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`drama_id`) USING BTREE,
  UNIQUE KEY `uk_channel_external` (`channel_id`,`external_content_id`) USING BTREE,
  KEY `idx_related_work` (`related_work_id`) USING BTREE,
  KEY `idx_status_auth` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS `sys_income_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `available_amount` decimal(18,2) NOT NULL,
  `frozen_amount` decimal(18,2) NOT NULL,
  `settled_amount` decimal(18,2) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`account_id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_income_settle` (
  `settle_id` bigint NOT NULL AUTO_INCREMENT COMMENT '结算单ID',
  `settle_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '结算单号',
  `user_id` bigint NOT NULL COMMENT '卖家/作者用户ID',
  `biz_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'order' COMMENT '结算来源: order订单/demand_stage任务逐轮',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID(订单结算单专用, 任务轮次为NULL)',
  `order_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单号快照',
  `biz_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务单号快照(任务行=征集任务号demand_no)',
  `demand_id` bigint DEFAULT NULL COMMENT '关联征集任务ID(任务轮次结算专用)',
  `stage_id` bigint DEFAULT NULL COMMENT '关联验收轮次ID(任务轮次结算专用, 一轮一单)',
  `stage_order` int DEFAULT NULL COMMENT '轮次序号快照(第N轮)',
  `work_title` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '作品标题快照(任务行=本轮交付作品标题, 无则任务标题)',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单总额快照',
  `platform_fee` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '平台费用快照',
  `split_rate` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '分成比例快照',
  `seller_amount` decimal(12,2) NOT NULL COMMENT '作者应得金额快照',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending待确认/confirmed已确认入账/disputed有异议',
  `dispute_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '异议原因',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `dispute_time` datetime DEFAULT NULL COMMENT '异议时间',
  `bill_id` bigint DEFAULT NULL COMMENT '入账后关联账单ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（订单完成时）',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`settle_id`) USING BTREE,
  UNIQUE KEY `uk_settle_no` (`settle_no`) USING BTREE,
  UNIQUE KEY `uk_order_id` (`order_id`) USING BTREE,
  UNIQUE KEY `uk_stage_id` (`stage_id`) USING BTREE,
  KEY `idx_user_status` (`user_id`,`status`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_demand_id` (`demand_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作者收益待结算单（到账前确认）';

CREATE TABLE IF NOT EXISTS `sys_invoice` (
  `invoice_id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `applicant_id` bigint NOT NULL,
  `invoice_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `tax_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `invoice_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reject_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `invoice_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`invoice_id`) USING BTREE,
  UNIQUE KEY `uk_invoice_no` (`invoice_no`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='发票表';

CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `module` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `operation` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `target_id` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `target_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `request_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `request_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `request_params` text COLLATE utf8mb4_general_ci,
  `response_result` text COLLATE utf8mb4_general_ci,
  `ip_address` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `error_msg` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cost_time` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`log_id`) USING BTREE,
  KEY `idx_operator_id` (`operator_id`) USING BTREE,
  KEY `idx_module` (`module`) USING BTREE,
  KEY `idx_operation` (`operation`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_request_id` (`request_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='操作日志表';

CREATE TABLE IF NOT EXISTS `sys_play_progress` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `progress_seconds` int DEFAULT NULL,
  `total_duration` int DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_episode` (`user_id`,`episode_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_points_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `balance` int NOT NULL,
  `total_earned` int NOT NULL,
  `total_spent` int NOT NULL,
  `today_earned` int NOT NULL,
  `last_update_date` date NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`account_id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='积分账户表';

CREATE TABLE IF NOT EXISTS `sys_points_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `change_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `points` int NOT NULL,
  `balance_after` int NOT NULL,
  `source` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `related_id` bigint DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`record_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='积分流水表';

CREATE TABLE IF NOT EXISTS `sys_promotion_application` (
  `apply_id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请单号',
  `work_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `slot_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'banner/search/hot',
  `slot_no` int NOT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `days` int NOT NULL,
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `source` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'author_paid' COMMENT 'author_paid 作者付费 / admin_grant 免费直上',
  `exclusive` tinyint NOT NULL DEFAULT '1' COMMENT '1=锁定独占，0=可被付费素材覆盖',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/paid/cancelled',
  `pay_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'unpaid' COMMENT 'unpaid/paid/closed',
  `pay_time` datetime DEFAULT NULL,
  `banner_id` bigint DEFAULT NULL COMMENT '轮播图素材ID（slot_type=banner 时）',
  `material_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '轮播素材图片地址（slot_type=banner 必填）',
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `lock_expire_at` datetime DEFAULT NULL COMMENT '审核通过后锁坑截止时间',
  `audit_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`apply_id`) USING BTREE,
  UNIQUE KEY `uk_promotion_apply_no` (`apply_no`) USING BTREE,
  KEY `idx_promotion_slot_window` (`slot_type`,`slot_no`,`start_time`,`end_time`) USING BTREE,
  KEY `idx_promotion_author` (`author_id`,`status`) USING BTREE,
  KEY `idx_promotion_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品推广申请与投放记录';

CREATE TABLE IF NOT EXISTS `sys_promotion_slot` (
  `slot_id` bigint NOT NULL AUTO_INCREMENT,
  `slot_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源位类型：banner/search/hot',
  `slot_no` int NOT NULL COMMENT '位次（1..N）',
  `price_per_day` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '单价（元/天）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否接受申请：1 是 0 否',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '位次说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`slot_id`) USING BTREE,
  UNIQUE KEY `uk_promotion_slot` (`slot_type`,`slot_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品推广坑位配置';

CREATE TABLE IF NOT EXISTS `sys_read_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `chapter_id` bigint DEFAULT NULL,
  `read_duration` int NOT NULL,
  `read_progress` decimal(5,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_work` (`user_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='阅读记录表';

CREATE TABLE IF NOT EXISTS `sys_recharge_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台订单号（唯一，支付对账用）',
  `user_id` bigint NOT NULL COMMENT '充值用户',
  `amount_fen` int NOT NULL COMMENT '支付金额（分）',
  `points` int NOT NULL COMMENT '到账积分',
  `channel` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付渠道 WECHAT/ALIPAY',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待支付 PAID-已支付 CANCELLED-已取消',
  `pay_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`) USING BTREE,
  UNIQUE KEY `uk_order_no` (`order_no`) USING BTREE,
  KEY `idx_user_time` (`user_id`,`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='积分充值订单表（模拟支付壳子）';

CREATE TABLE IF NOT EXISTS `sys_report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `reason` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `handle_result` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`report_id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_review_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `review_id` bigint NOT NULL COMMENT '审核记录ID',
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '审核动作',
  `operator_id` bigint NOT NULL DEFAULT '1' COMMENT '操作人ID',
  `operator_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `before_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `after_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `review_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`log_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='审核操作日志';

CREATE TABLE IF NOT EXISTS `sys_review_record` (
  `review_id` bigint NOT NULL AUTO_INCREMENT,
  `review_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `target_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `submitter_id` bigint NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `ai_result` json DEFAULT NULL,
  `ai_risk_level` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ai_sensitive_words` text COLLATE utf8mb4_general_ci,
  `review_result` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `review_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `ai_start_time` datetime DEFAULT NULL,
  `ai_end_time` datetime DEFAULT NULL,
  `review_start_time` datetime DEFAULT NULL,
  `review_end_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`review_id`) USING BTREE,
  UNIQUE KEY `uk_review_no` (`review_no`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_reviewer_id` (`reviewer_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='审核记录表';

CREATE TABLE IF NOT EXISTS `sys_risk_rule` (
  `rule_id` int NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `trigger_condition` json NOT NULL,
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `risk_level` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `threshold` decimal(10,2) DEFAULT NULL,
  `time_window` int DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `hit_count` int NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`rule_id`) USING BTREE,
  UNIQUE KEY `uk_rule_code` (`rule_code`) USING BTREE,
  KEY `idx_rule_type` (`rule_type`) USING BTREE,
  KEY `idx_risk_level` (`risk_level`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='风控规则表';

CREATE TABLE IF NOT EXISTS `sys_search_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `keyword` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `search_count` int NOT NULL,
  `last_search_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_keyword` (`keyword`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='搜索历史表';

CREATE TABLE IF NOT EXISTS `sys_settlement` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `settlement_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Settlement Number',
  `author_id` bigint NOT NULL COMMENT 'Author User ID',
  `period_start` date NOT NULL COMMENT 'Period Start',
  `period_end` date NOT NULL COMMENT 'Period End',
  `order_count` int NOT NULL DEFAULT '0' COMMENT 'Order Count',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Total Amount',
  `platform_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Platform Amount',
  `author_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Author Amount',
  `platform_ratio` int NOT NULL DEFAULT '20' COMMENT 'Platform Ratio',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'Status: pending/settled/abnormal',
  `settlement_time` datetime DEFAULT NULL COMMENT 'Settlement Time',
  `abnormal_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Abnormal Reason',
  `handle_remark` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Handle Remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`settlement_id`) USING BTREE,
  UNIQUE KEY `uk_settlement_no` (`settlement_no`) USING BTREE,
  KEY `idx_author_id` (`author_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='Settlement Table';

CREATE TABLE IF NOT EXISTS `sys_subscribe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `notify_enabled` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='追更订阅表';

CREATE TABLE IF NOT EXISTS `sys_task` (
  `task_id` int NOT NULL AUTO_INCREMENT,
  `task_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `task_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `task_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `description` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `target_count` int NOT NULL DEFAULT '1',
  `reward_points` int NOT NULL DEFAULT '0',
  `icon` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `start_time` datetime DEFAULT NULL COMMENT '生效开始时间(空=立即)',
  `end_time` datetime DEFAULT NULL COMMENT '生效结束时间(空=长期)',
  `daily_limit` int NOT NULL DEFAULT '0' COMMENT '每日可领取次数上限(0=不限)',
  `sort` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`task_id`) USING BTREE,
  UNIQUE KEY `uk_task_code` (`task_code`) USING BTREE,
  KEY `idx_task_type` (`task_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='任务表';

CREATE TABLE IF NOT EXISTS `sys_trade_agent_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'user / assistant / tool',
  `content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '文本内容',
  `tool_calls_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT 'assistant 发起的 tool_calls JSON',
  `tool_call_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'tool 消息对应的 tool_call_id',
  `tool_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具名',
  `structured_payload_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '供前端渲染的结构化卡片JSON数组',
  `tokens` int NOT NULL DEFAULT '0' COMMENT '本条消耗Token',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  KEY `idx_agent_msg_session_id` (`session_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业采购选本Agent消息表';

CREATE TABLE IF NOT EXISTS `sys_trade_agent_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '企业用户ID（sys_user.user_id）',
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '新选本对话' COMMENT '会话标题',
  `brief_json` text COLLATE utf8mb4_unicode_ci COMMENT '已提取的采购画像JSON（预算/题材/集数/诉求）',
  `total_tokens` int NOT NULL DEFAULT '0' COMMENT '累计消耗Token',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT 'active / archived',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  KEY `idx_agent_session_user_updated` (`user_id`,`updated_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业采购选本Agent会话表';

CREATE TABLE IF NOT EXISTS `sys_unlock_record` (
  `unlock_id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `unlock_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  `ad_task_id` int DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`unlock_id`) USING BTREE,
  UNIQUE KEY `uk_user_episode` (`user_id`,`episode_id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_user_follow` (
  `id` int NOT NULL AUTO_INCREMENT,
  `follower_id` int NOT NULL,
  `followee_id` int NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_follower_followee` (`follower_id`,`followee_id`) USING BTREE,
  KEY `idx_followee` (`followee_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_user_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `task_id` int NOT NULL,
  `progress` int NOT NULL,
  `is_completed` tinyint NOT NULL,
  `is_claimed` tinyint NOT NULL,
  `complete_time` datetime DEFAULT NULL,
  `claim_time` datetime DEFAULT NULL,
  `period_date` date NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_task_period` (`user_id`,`task_id`,`period_date`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户任务完成表';

CREATE TABLE IF NOT EXISTS `sys_withdraw_apply` (
  `withdraw_id` bigint NOT NULL AUTO_INCREMENT,
  `withdraw_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `fee` decimal(12,2) NOT NULL,
  `actual_amount` decimal(12,2) NOT NULL,
  `withdraw_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `account_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `account_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `bank_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pay_time` datetime DEFAULT NULL,
  `pay_trade_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `client_token` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?ͻ????ݵ????',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`withdraw_id`) USING BTREE,
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`) USING BTREE,
  UNIQUE KEY `uk_user_client_token` (`user_id`,`client_token`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='提现申请表';

CREATE TABLE IF NOT EXISTS `sys_work_chapter` (
  `chapter_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `chapter_no` int NOT NULL,
  `chapter_title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `word_count` int NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '章节摘要（只记本集推进了什么）',
  `is_free` tinyint NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`chapter_id`) USING BTREE,
  UNIQUE KEY `uk_work_chapter_no` (`work_id`,`chapter_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品章节表';

CREATE TABLE IF NOT EXISTS `sys_work_file` (
  `file_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `file_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `file_size` bigint NOT NULL,
  `is_preview` tinyint NOT NULL,
  `sort` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`file_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品文件表';

CREATE TABLE IF NOT EXISTS `sys_work_gen_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '提交用户ID',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'queued' COMMENT 'queued/running/paused/succeeded/failed/cancelled',
  `total` int NOT NULL DEFAULT '0' COMMENT '待生成集数',
  `done_count` int NOT NULL DEFAULT '0' COMMENT '已完成集数',
  `current_no` int DEFAULT NULL COMMENT '正在生成的集号',
  `current_title` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '正在生成的集名',
  `outline` mediumtext COLLATE utf8mb4_general_ci COMMENT '全剧事件卡',
  `characters` mediumtext COLLATE utf8mb4_general_ci COMMENT '人物设定（注入用）',
  `roster` text COLLATE utf8mb4_general_ci COMMENT '人物白名单（顿号分隔）',
  `genre` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '题材（台账基线起草输入）',
  `user_prompt` text COLLATE utf8mb4_general_ci COMMENT '用户要求（台账基线起草输入）',
  `model` varchar(120) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本任务使用的模型名',
  `episodes` longtext COLLATE utf8mb4_general_ci COMMENT '集队列 JSON：[{no,title,card,status,chapterId,error,attempts}]',
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近一次失败原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`job_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品批量生成任务（后台队列）';

CREATE TABLE IF NOT EXISTS `sys_work_ledger` (
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `story_summary` text COLLATE utf8mb4_general_ci COMMENT '故事简介',
  `characters` mediumtext COLLATE utf8mb4_general_ci COMMENT '主要人物卡',
  `relations` mediumtext COLLATE utf8mb4_general_ci COMMENT '人物关系当前状态（含上次变化原因）',
  `foreshadow` mediumtext COLLATE utf8mb4_general_ci COMMENT '伏笔台账（埋在哪章/计划回收章/是否已回收）',
  `timeline` mediumtext COLLATE utf8mb4_general_ci COMMENT '时间线',
  `items` mediumtext COLLATE utf8mb4_general_ci COMMENT '物品位置',
  `props` longtext COLLATE utf8mb4_general_ci COMMENT '重要道具档案',
  `style_guide` longtext COLLATE utf8mb4_general_ci COMMENT '写法基线',
  `outline` longtext COLLATE utf8mb4_general_ci COMMENT '剧本大纲（分集事件卡）',
  `threads` mediumtext COLLATE utf8mb4_general_ci COMMENT '未解线索',
  `state_board` text COLLATE utf8mb4_general_ci COMMENT '状态板：所在/持有/认知/约定，一行一条，变更须带触发',
  `chapter_meta` longtext COLLATE utf8mb4_general_ci NULL COMMENT '按章节号索引的JSON：cast/items/edges',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品创作台账（跨端共享）';

-- 存量库补齐章节元数据，保留原有台账。
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work_ledger' AND COLUMN_NAME = 'chapter_meta');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_work_ledger ADD COLUMN chapter_meta LONGTEXT NULL COMMENT ''按章节号索引的JSON：cast/items/edges'' AFTER state_board',
  'SELECT ''sys_work_ledger.chapter_meta ok'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `sys_work_rating` (
  `rating_id` bigint NOT NULL AUTO_INCREMENT COMMENT '评分记录ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '评分用户ID',
  `score` tinyint NOT NULL COMMENT '评分（满分 10 分，每星 2 分：2/4/6/8/10）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次评分时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近评分时间',
  PRIMARY KEY (`rating_id`) USING BTREE,
  UNIQUE KEY `uk_work_user` (`work_id`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品读者评分表';

CREATE TABLE IF NOT EXISTS `sys_work_storyboard` (
  `storyboard_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `chapter_id` bigint NOT NULL,
  `chapter_title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `content` mediumtext COLLATE utf8mb4_general_ci NOT NULL,
  `word_count` int NOT NULL DEFAULT '0',
  `source` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT 'manual=手动 ai=AI生成',
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending' COMMENT 'pending=待审核 approved=通过 rejected=驳回',
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ai_result` mediumtext COLLATE utf8mb4_general_ci COMMENT 'AI审核结果JSON，与作品审核格式一致',
  `ai_risk_level` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'AI风险等级：low/medium/high',
  `ai_sensitive_words` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'AI命中的敏感词，逗号分隔',
  `ai_start_time` datetime DEFAULT NULL COMMENT 'AI审核开始时间',
  `ai_end_time` datetime DEFAULT NULL COMMENT 'AI审核结束时间',
  PRIMARY KEY (`storyboard_id`) USING BTREE,
  UNIQUE KEY `uk_work_storyboard_chapter` (`work_id`,`chapter_id`) USING BTREE,
  KEY `idx_storyboard_work` (`work_id`) USING BTREE,
  KEY `idx_storyboard_chapter` (`chapter_id`) USING BTREE,
  KEY `idx_storyboard_status` (`status`) USING BTREE,
  KEY `idx_storyboard_author_time` (`work_id`,`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品分镜目录';

CREATE TABLE IF NOT EXISTS `sys_work_timeline_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `chapter_no` int NOT NULL COMMENT '埋在哪一集（排序与检索主键）',
  `seq` int NOT NULL DEFAULT '1' COMMENT '同集内序号',
  `event_type` varchar(20) NOT NULL DEFAULT '其他' COMMENT '约定/伏笔/道具转移/身份变化/关系变化/生死退场/能力突破/信息揭示/期限/其他',
  `fact` varchar(255) NOT NULL COMMENT '一句话事实：谁+做了什么+条件或代价',
  `actors` varchar(255) DEFAULT NULL COMMENT '涉及人物，顿号分隔',
  `props` varchar(255) DEFAULT NULL COMMENT '涉及道具或线索，顿号分隔',
  `status` varchar(20) NOT NULL DEFAULT '未兑现' COMMENT '未兑现/已兑现/已失效',
  `deadline_no` int DEFAULT NULL COMMENT '计划兑现集号',
  `story_time` varchar(60) DEFAULT NULL COMMENT '故事内时间（自由文本，不参与排序）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_work_chapter_seq` (`work_id`,`chapter_no`,`seq`),
  KEY `idx_work_status` (`work_id`,`status`),
  KEY `idx_work_chapter` (`work_id`,`chapter_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品时间线事件（只追加）';

CREATE TABLE IF NOT EXISTS `sys_work_version` (
  `version_id` int NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `version_no` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` longtext COLLATE utf8mb4_general_ci,
  `file_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `change_log` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `creator_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_current` tinyint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`version_id`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE,
  KEY `idx_creator` (`creator_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_work_volume` (
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `volume_no` int NOT NULL COMMENT '卷号（第N卷）',
  `episode_start` int NOT NULL COMMENT '起始集号',
  `episode_end` int NOT NULL COMMENT '结束集号',
  `snapshot` mediumtext COLLATE utf8mb4_general_ci COMMENT '卷级当前状态快照（人物当前态/未回收伏笔/道具归属/卷末悬念）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`work_id`,`volume_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='作品卷级状态快照（写正文时注入）';

CREATE TABLE IF NOT EXISTS `user_creator_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `pen_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '笔名',
  `direction` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创作方向/擅长类型',
  `portfolio` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '代表作品',
  `intro` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请说明',
  `contact` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '联系方式',
  `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
  `auditor_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `audited_at` datetime DEFAULT NULL COMMENT '审核时间',
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '驳回原因',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_creator_application_user` (`user_id`,`status`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='创作者申请';

SET FOREIGN_KEY_CHECKS = @OLD_FK_CHECKS;

-- =====================================================================
-- 第 16 部分：现役触发器（4 个，幂等）
-- ---------------------------------------------------------------------
-- init-database.sh / .ps1 都以 `mysql < 文件` 方式执行，DELIMITER 为客户端指令，
-- 两个脚本均可安全执行本段。每次 DROP + CREATE，重复执行结果一致；
-- 已退役的 trg_sys_personal_seal_bu_time 不再创建。
-- =====================================================================
DROP TRIGGER IF EXISTS `trg_sys_ai_request_bu_finished_time`;
DELIMITER $$
CREATE TRIGGER `trg_sys_ai_request_bu_finished_time` BEFORE UPDATE ON `sys_ai_request` FOR EACH ROW BEGIN
  IF NEW.status IN ('success', 'failed', 'cancelled')
     AND OLD.status NOT IN ('success', 'failed', 'cancelled') THEN
    SET NEW.finished_at = COALESCE(NEW.finished_at, CURRENT_TIMESTAMP);
  END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_sys_favorite_ai_work_count`;
DELIMITER $$
CREATE TRIGGER `trg_sys_favorite_ai_work_count` AFTER INSERT ON `sys_favorite` FOR EACH ROW BEGIN
  UPDATE sys_work
     SET favorite_count = favorite_count + 1
   WHERE work_id = NEW.work_id AND is_deleted = 0;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_sys_favorite_ad_work_count`;
DELIMITER $$
CREATE TRIGGER `trg_sys_favorite_ad_work_count` AFTER DELETE ON `sys_favorite` FOR EACH ROW BEGIN
  UPDATE sys_work
     SET favorite_count = GREATEST(favorite_count - 1, 0)
   WHERE work_id = OLD.work_id AND is_deleted = 0;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_sys_review_record_au_sync_work`;
DELIMITER $$
CREATE TRIGGER `trg_sys_review_record_au_sync_work` AFTER UPDATE ON `sys_review_record` FOR EACH ROW BEGIN
  IF NEW.target_type = 'work'
     AND (COALESCE(NEW.status, '') <> COALESCE(OLD.status, '')
       OR COALESCE(NEW.review_result, '') <> COALESCE(OLD.review_result, '')) THEN
    UPDATE sys_work
       SET status = CASE NEW.review_result
                      WHEN 'approved' THEN 'approved'
                      WHEN 'rejected' THEN 'rejected'
                      WHEN 'returned' THEN 'returned'
                      ELSE status
                    END,
           reviewer_id = NEW.reviewer_id,
           review_time = COALESCE(NEW.review_end_time, CURRENT_TIMESTAMP),
           reject_reason = CASE
                             WHEN NEW.review_result IN ('rejected', 'returned')
                             THEN NEW.review_opinion
                             ELSE NULL
                           END
     WHERE work_id = NEW.target_id
       AND is_deleted = 0;
  END IF;
END$$
DELIMITER ;

-- =====================================================================
-- 第 17 部分：字典与系统配置权威同步（幂等）
-- ---------------------------------------------------------------------
-- 与线上 p06_platform 对齐：
--   配置 21 条（含 ai.token.per.point、ai.gen.max_tokens.*、app.version.android.*、
--   已补名称的 115；100/102/103 已在阶段 A 删除，不再写入）。
--   字典类型 24 个、字典项 84 条；152 已退役（由 163/164/165 取代），110/130 为停用历史项。
-- =====================================================================
INSERT INTO sys_config (config_id,config_name,config_key,config_value,config_type,remark) VALUES
(1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),
(2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', '初始化密码 123456'),
(3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', '深色主题theme-dark，浅色主题theme-light'),
(4, '验证码', 'sys.account.captchaEnabled', 'false', 'Y', '?????'),
(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', '是否开启注册用户功能（true开启，false关闭）'),
(6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),
(7, '用户管理-初始密码修改策略', 'sys.account.initPasswordModify', '1', 'Y', '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框'),
(8, '用户管理-账号密码更新周期', 'sys.account.passwordValidateDays', '0', 'Y', '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框'),
(101, 'AI写作单次生成字数上限', 'ai.gen.max.write', '0', 'Y', '单次AI写作调用输出字数上限，0=不限'),
(104, 'AI创作token/积分配置', 'ai.token.per.point', '1000', 'Y', '1积分可消耗的token数，AI创作按实际token扣积分'),
(105, 'AI设定生成token上限（故事简介/主要人物/人物关系）', 'ai.gen.max_tokens.setting', '8192', 'N', '独立额度，不影响事件卡与正文；0=不限（不传max_tokens）'),
(106, 'AI事件卡单批token上限', 'ai.gen.max_tokens.episode', '8192', 'N', '每批6集事件卡的单次额度；0=不限'),
(107, 'AI正文单次token上限', 'ai.gen.max_tokens.write', '1500', 'N', '单次生成正文的额度；0=不限'),
(108, 'App最新版本号', 'app.version.android.versionName', '1.0.22', 'N', 'App 应用内更新：展示用版本名'),
(109, 'App最新构建号', 'app.version.android.versionCode', '22', 'N', 'App 应用内更新：与 Android versionCode 比较，必须递增'),
(110, 'App强制更新下限', 'app.version.android.minVersionCode', '1', 'N', '低于该 build 号不可跳过；0 表示不强制'),
(111, 'App安装包地址', 'app.version.android.url', 'http://192.168.100.3:1100/profile/app/script-app-1.0.22-22-680dbcf45682.apk', 'N', 'APK 直链或 /profile/app/xx.apk 相对路径'),
(112, 'App安装包大小', 'app.version.android.size', '38968209', 'N', '字节数；下载后校验，留空则不校验'),
(113, 'App更新说明', 'app.version.android.notes', 'P06 App update', 'N', '弹窗展示，可含换行'),
(114, 'App更新开关', 'app.version.android.enabled', 'true', 'N', 'false 时 App 视为已是最新'),
(115, 'AI大纲生成最大Token数', 'ai.gen.max_tokens.outline', '8192', 'N', '大纲单次输出token上限（0=不限，8192=默认）')
ON DUPLICATE KEY UPDATE config_name=VALUES(config_name),config_key=VALUES(config_key),config_value=VALUES(config_value),config_type=VALUES(config_type),remark=VALUES(remark);

INSERT INTO sys_dict_type (dict_id,dict_name,dict_type,status,remark) VALUES
(1,'用户性别','sys_user_sex','0','用户性别列表'),
(2,'菜单状态','sys_show_hide','0','菜单状态列表'),
(3,'系统开关','sys_normal_disable','0','系统开关列表'),
(4,'任务状态','sys_job_status','0','任务状态列表'),
(5,'任务分组','sys_job_group','0','任务分组列表'),
(6,'系统是否','sys_yes_no','0','系统是否列表'),
(7,'通知类型','sys_notice_type','0','通知类型列表'),
(8,'通知状态','sys_notice_status','0','通知状态列表'),
(9,'操作类型','sys_oper_type','0','操作类型列表'),
(10,'系统状态','sys_common_status','0','登录状态列表'),
(100,'交易-订单状态','trade_order_status','0','PRD 9.3；对应前端 order'),
(101,'交易-询盘状态','trade_inquiry_status','0','2026-09-28 定稿；对应前端 inquiry'),
(102,'交易-报价状态','trade_quote_status','0','2026-09-28 定稿；对应前端 quote'),
(103,'交易-报价方角色','trade_quoter_role','0','sys_quote.quoter_role；对应前端 quoterRole'),
(104,'交易-合作方类型','trade_partner_type','0','接口 2.35；对应前端 partnerType'),
(105,'交易-合作方状态','trade_partner_status','0','sys_partner.status；对应前端 partnerStatus'),
(106,'交易-授权类型','trade_license_type','0','接口 2.25；对应前端 license'),
(107,'交易-作品上架状态','trade_work_listing_status','0','接口 2.25 listingStatus；对应前端 tradeWork'),
(108,'交易-商务跟进状态','trade_follow_status','0','接口 2.38；对应前端 follow'),
(109,'交易-商务跟进方式','trade_follow_method','0','接口 2.38 method；对应前端 followMethod'),
(110,'交易-征集项目状态','trade_demand_status','0','sys_demand.status；对应前端 demand'),
(111,'交易-投稿状态','trade_submission_status','0','sys_demand_submission.status；对应前端 submission'),
(112,'交易-合作记录来源','trade_cooperation_source','0','sys_offline_cooperation.source；对应前端 cooperationSource'),
(113,'交易-合作记录状态','trade_cooperation_status','0','sys_offline_cooperation.status；对应前端 cooperationStatus')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name),dict_type=VALUES(dict_type),status=VALUES(status),remark=VALUES(remark);

-- 与线上一致：dict_code 110/130 为停用历史项，152 已退役、由 163/164/165 取代
DELETE FROM sys_dict_data WHERE dict_code = 152;
INSERT INTO sys_dict_data (dict_code,dict_sort,dict_label,dict_value,dict_type,status,remark) VALUES
(28,1,'成功','0','sys_common_status','0','正常状态'),
(29,2,'失败','1','sys_common_status','0','停用状态'),
(10,1,'默认','DEFAULT','sys_job_group','0','默认分组'),
(11,2,'系统','SYSTEM','sys_job_group','0','系统分组'),
(8,1,'正常','0','sys_job_status','0','正常状态'),
(9,2,'暂停','1','sys_job_status','0','停用状态'),
(6,1,'正常','0','sys_normal_disable','0','正常状态'),
(7,2,'停用','1','sys_normal_disable','0','停用状态'),
(16,1,'正常','0','sys_notice_status','0','正常状态'),
(17,2,'关闭','1','sys_notice_status','0','关闭状态'),
(14,1,'通知','1','sys_notice_type','0','通知'),
(15,2,'公告','2','sys_notice_type','0','公告'),
(19,1,'新增','1','sys_oper_type','0','新增操作'),
(20,2,'修改','2','sys_oper_type','0','修改操作'),
(21,3,'删除','3','sys_oper_type','0','删除操作'),
(22,4,'授权','4','sys_oper_type','0','授权操作'),
(23,5,'导出','5','sys_oper_type','0','导出操作'),
(24,6,'导入','6','sys_oper_type','0','导入操作'),
(25,7,'强退','7','sys_oper_type','0','强退操作'),
(26,8,'生成代码','8','sys_oper_type','0','生成操作'),
(27,9,'清空数据','9','sys_oper_type','0','清空操作'),
(18,99,'其他','0','sys_oper_type','0','其他操作'),
(4,1,'显示','0','sys_show_hide','0','显示菜单'),
(5,2,'隐藏','1','sys_show_hide','0','隐藏菜单'),
(1,1,'男','0','sys_user_sex','0','性别男'),
(2,2,'女','1','sys_user_sex','0','性别女'),
(3,3,'未知','2','sys_user_sex','0','性别未知'),
(12,1,'是','Y','sys_yes_no','0','系统默认是'),
(13,2,'否','N','sys_yes_no','0','系统默认否'),
(146,1,'线上合作意向','online','trade_cooperation_source','0','C 模块枚举'),
(147,2,'线下谈判','offline','trade_cooperation_source','0','C 模块枚举'),
(148,1,'待跟进','pending','trade_cooperation_status','0','C 模块枚举'),
(149,2,'洽谈中','ongoing','trade_cooperation_status','0','C 模块枚举'),
(150,3,'已达成','completed','trade_cooperation_status','0','C 模块枚举'),
(151,4,'已终止','cancelled','trade_cooperation_status','0','C 模块枚举'),
(139,1,'征集中','open','trade_demand_status','0','C 模块枚举'),
(140,2,'已截止','closed','trade_demand_status','0','C 模块枚举'),
(141,3,'已选定','selected','trade_demand_status','0','C 模块枚举'),
(164,4,'待审核','pending_review','trade_demand_status','0',NULL),
(165,5,'审核未通过','rejected','trade_demand_status','0',NULL),
(136,1,'电话','phone','trade_follow_method','0','C 模块枚举'),
(137,2,'邮件','email','trade_follow_method','0','C 模块枚举'),
(138,3,'面谈','meeting','trade_follow_method','0','C 模块枚举'),
(133,1,'进行中','ongoing','trade_follow_status','0','C 模块枚举'),
(134,2,'待跟进','pending','trade_follow_status','0','C 模块枚举'),
(135,3,'已完成','completed','trade_follow_status','0','C 模块枚举'),
(109,1,'待回复','pending','trade_inquiry_status','0','C 模块枚举'),
(110,2,'已接受','accepted','trade_inquiry_status','1','2026-09-29 已并入 quoted（议价中）'),
(111,3,'已拒绝','rejected','trade_inquiry_status','0','C 模块枚举'),
(112,4,'已关闭','closed','trade_inquiry_status','0','C 模块枚举'),
(113,5,'议价中','quoted','trade_inquiry_status','0','C 模块枚举'),
(114,6,'已达成','deal','trade_inquiry_status','0','C 模块枚举'),
(127,1,'独家','exclusive','trade_license_type','0','C 模块枚举'),
(128,2,'非独家','non_exclusive','trade_license_type','0','C 模块枚举'),
(129,3,'改编','adaptation','trade_license_type','0','C 模块枚举'),
(130,4,'可议价','negotiable','trade_license_type','1','2026-09-30 退役：议价改为所有作品共有能力，由 negotiable_min/max 表达区间'),
(100,1,'询盘中','inquiry','trade_order_status','0','C 模块枚举'),
(101,2,'已报价','quoted','trade_order_status','0','C 模块枚举'),
(102,3,'已确认','confirmed','trade_order_status','0','C 模块枚举'),
(103,4,'待签约','contract_pending','trade_order_status','0','C 模块枚举'),
(104,5,'待托管','escrow_pending','trade_order_status','0','C 模块枚举'),
(105,6,'交割中','delivering','trade_order_status','0','C 模块枚举'),
(106,7,'已完成','completed','trade_order_status','0','C 模块枚举'),
(107,8,'已取消','cancelled','trade_order_status','0','C 模块枚举'),
(108,9,'已退款','refunded','trade_order_status','0','C 模块枚举'),
(124,1,'正常','active','trade_partner_status','0','C 模块枚举'),
(125,2,'停用','inactive','trade_partner_status','0','C 模块枚举'),
(126,3,'待审核','pending','trade_partner_status','0','C 模块枚举'),
(121,1,'投资方','investor','trade_partner_type','0','C 模块枚举'),
(122,2,'制作机构','studio','trade_partner_type','0','C 模块枚举'),
(123,3,'发行平台','platform','trade_partner_type','0','C 模块枚举'),
(119,1,'卖方报价','seller','trade_quoter_role','0','C 模块枚举'),
(120,2,'买方议价','buyer','trade_quoter_role','0','C 模块枚举'),
(115,1,'待买方确认','pending','trade_quote_status','0','C 模块枚举'),
(116,2,'已接受','accepted','trade_quote_status','0','C 模块枚举'),
(117,3,'已拒绝','rejected','trade_quote_status','0','C 模块枚举'),
(118,4,'已过期','expired','trade_quote_status','0','C 模块枚举'),
(163,5,'待卖方确认','pending_seller','trade_quote_status','0','C 模块枚举'),
(142,1,'已投稿','submitted','trade_submission_status','0','C 模块枚举'),
(143,2,'入围','shortlisted','trade_submission_status','0','C 模块枚举'),
(144,3,'已选用','accepted','trade_submission_status','0','C 模块枚举'),
(145,4,'未选用','rejected','trade_submission_status','0','C 模块枚举'),
(131,1,'已上架','listed','trade_work_listing_status','0','C 模块枚举'),
(132,2,'已下架','offline','trade_work_listing_status','0','C 模块枚举')
ON DUPLICATE KEY UPDATE dict_sort=VALUES(dict_sort),dict_label=VALUES(dict_label),dict_value=VALUES(dict_value),dict_type=VALUES(dict_type),status=VALUES(status),remark=VALUES(remark);

-- =====================================================================
-- 第 18 部分：菜单与角色授权权威同步（幂等）
-- ---------------------------------------------------------------------
-- 以线上 205 条菜单 / 372 条角色菜单授权为唯一口径：
--   1) 先清掉本文件历史段落可能生成的退役菜单及其授权（1035-1038、5183/5184、
--      2027-2029、2046-2052、5230、5171-5175、5220、5225、5423、2010/2017 等）。
--   2) 清掉指向不存在菜单的悬空授权。
--   3) 补齐超管等权角色 super_group(101)。
--   4) 逐条 upsert 205 条菜单（含线上最新名称、可见性、权限串）。
--   5) 逐条补齐 372 条授权；普通角色不再持有已隐藏的若依监控/工具页 109-117。
-- 只收敛入口与权限，不删除任何业务数据。
-- =====================================================================
START TRANSACTION;

DELETE rm FROM sys_role_menu rm WHERE rm.menu_id IN (1035,1036,1037,1038,5183,5184,2027,2028,2029,2046,2047,2048,2049,2050,2051,2052,5230,5171,5172,5173,5174,5175,5220,5225,5423,5425,2010,2017,2020,2021,2022,2031,2032,2039,5110,5111,5122,5134,5320,5400,3010,3011,3012,107,108,5001,5200,5189,5190,5191,5192,5419);
DELETE FROM sys_menu WHERE menu_id IN (1035,1036,1037,1038,5183,5184,2027,2028,2029,2046,2047,2048,2049,2050,2051,2052,5230,5171,5172,5173,5174,5175,5220,5225,5423,5425,2010,2017,2020,2021,2022,2031,2032,2039,5110,5111,5122,5134,5320,5400,3010,3011,3012,107,108,5001,5200,5189,5190,5191,5192,5419);

DELETE rm FROM sys_role_menu rm
LEFT JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.menu_id IS NULL;

INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, app_grantable,
                      menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
SELECT 101, '超级管理员组', 'super_group', 3, '1', 0, 1, 1, '0', '0', 'admin', NOW(),
       'userId≠1 超管等权角色：全菜单+全权限码+全部数据权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'super_group')
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id FROM sys_role) x WHERE x.role_id = 101);

INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,remark) VALUES
('1', '系统管理', '0', '90', 'system', NULL, '', '', '1', '0', 'M', '0', '0', '', 'system', '系统管理目录'),
('2', '系统监控', '0', '2', 'monitor', NULL, '', '', '1', '0', 'M', '1', '0', '', 'monitor', '系统监控目录'),
('3', '系统工具', '0', '3', 'tool', NULL, '', '', '1', '0', 'M', '1', '0', '', 'tool', '系统工具目录'),
('4', '若依官网', '0', '4', 'http://ruoyi.vip', NULL, '', '', '0', '0', 'M', '0', '0', '', 'guide', '若依官网地址'),
('100', '用户管理', '1', '1', 'user', 'system/user/index', '', '', '1', '0', 'C', '0', '0', 'system:user:list', 'user', '用户管理菜单'),
('101', '角色管理', '1', '2', 'role', 'system/role/index', '', '', '1', '0', 'C', '0', '0', 'system:role:list', 'peoples', '角色管理菜单'),
('102', '菜单管理', '1', '3', 'menu', 'system/menu/index', '', '', '1', '0', 'C', '0', '0', 'system:menu:list', 'tree-table', '菜单管理菜单'),
('103', '部门管理', '1', '4', 'dept', 'system/dept/index', '', '', '1', '0', 'C', '0', '0', 'system:dept:list', 'tree', '部门管理菜单'),
('104', '岗位管理', '1', '5', 'post', 'system/post/index', '', '', '1', '0', 'C', '0', '0', 'system:post:list', 'post', '岗位管理菜单'),
('105', '字典管理', '1', '6', 'dict', 'system/dict/index', '', '', '1', '0', 'C', '0', '0', 'system:dict:list', 'dict', '字典管理菜单'),
('106', '参数设置', '1', '7', 'config', 'system/config/index', '', '', '1', '0', 'C', '0', '0', 'system:config:list', 'edit', '参数设置菜单'),
('109', '在线用户', '2', '1', 'online', 'monitor/online/index', '', '', '1', '0', 'C', '1', '0', 'monitor:online:list', 'online', '在线用户菜单'),
('110', '定时任务', '2', '2', 'job', 'monitor/job/index', '', '', '1', '0', 'C', '1', '0', 'monitor:job:list', 'job', '定时任务菜单'),
('111', '数据监控', '2', '3', 'druid', 'monitor/druid/index', '', '', '1', '0', 'C', '1', '0', 'monitor:druid:list', 'druid', '数据监控菜单'),
('112', '服务监控', '2', '4', 'server', 'monitor/server/index', '', '', '1', '0', 'C', '1', '0', 'monitor:server:list', 'server', '服务监控菜单'),
('113', '缓存监控', '2', '5', 'cache', 'monitor/cache/index', '', '', '1', '0', 'C', '1', '0', 'monitor:cache:list', 'redis', '缓存监控菜单'),
('114', '缓存列表', '2', '6', 'cacheList', 'monitor/cache/list', '', '', '1', '0', 'C', '1', '0', 'monitor:cache:list', 'redis-list', '缓存列表菜单'),
('115', '表单构建', '3', '1', 'build', 'tool/build/index', '', '', '1', '0', 'C', '1', '0', 'tool:build:list', 'build', '表单构建菜单'),
('116', '代码生成', '3', '2', 'gen', 'tool/gen/index', '', '', '1', '0', 'C', '1', '0', 'tool:gen:list', 'code', '代码生成菜单'),
('117', '系统接口', '3', '3', 'swagger', 'tool/swagger/index', '', '', '1', '0', 'C', '1', '0', 'tool:swagger:list', 'swagger', '系统接口菜单'),
('500', '操作日志', '5002', '99', 'operlog', 'monitor/operlog/index', '', '', '1', '0', 'C', '0', '0', 'monitor:operlog:list', 'form', '操作日志菜单'),
('501', '登录日志', '5002', '100', 'logininfor', 'monitor/logininfor/index', '', '', '1', '0', 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', '登录日志菜单'),
('1000', '用户查询', '100', '1', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:query', '#', ''),
('1001', '用户新增', '100', '2', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:add', '#', ''),
('1002', '用户修改', '100', '3', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:edit', '#', ''),
('1003', '用户删除', '100', '4', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:remove', '#', ''),
('1004', '用户导出', '100', '5', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:export', '#', ''),
('1005', '用户导入', '100', '6', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:import', '#', ''),
('1006', '重置密码', '100', '7', '', '', '', '', '1', '0', 'F', '0', '0', 'system:user:resetPwd', '#', ''),
('1007', '角色查询', '101', '1', '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:query', '#', ''),
('1008', '角色新增', '101', '2', '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:add', '#', ''),
('1009', '角色修改', '101', '3', '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:edit', '#', ''),
('1010', '角色删除', '101', '4', '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:remove', '#', ''),
('1011', '角色导出', '101', '5', '', '', '', '', '1', '0', 'F', '0', '0', 'system:role:export', '#', ''),
('1012', '菜单查询', '102', '1', '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:query', '#', ''),
('1013', '菜单新增', '102', '2', '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:add', '#', ''),
('1014', '菜单修改', '102', '3', '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:edit', '#', ''),
('1015', '菜单删除', '102', '4', '', '', '', '', '1', '0', 'F', '0', '0', 'system:menu:remove', '#', ''),
('1016', '部门查询', '103', '1', '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:query', '#', ''),
('1017', '部门新增', '103', '2', '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:add', '#', ''),
('1018', '部门修改', '103', '3', '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:edit', '#', ''),
('1019', '部门删除', '103', '4', '', '', '', '', '1', '0', 'F', '0', '0', 'system:dept:remove', '#', ''),
('1020', '岗位查询', '104', '1', '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:query', '#', ''),
('1021', '岗位新增', '104', '2', '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:add', '#', ''),
('1022', '岗位修改', '104', '3', '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:edit', '#', ''),
('1023', '岗位删除', '104', '4', '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:remove', '#', ''),
('1024', '岗位导出', '104', '5', '', '', '', '', '1', '0', 'F', '0', '0', 'system:post:export', '#', ''),
('1025', '字典查询', '105', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:query', '#', ''),
('1026', '字典新增', '105', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:add', '#', ''),
('1027', '字典修改', '105', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:edit', '#', ''),
('1028', '字典删除', '105', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:remove', '#', ''),
('1029', '字典导出', '105', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:dict:export', '#', ''),
('1030', '参数查询', '106', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:query', '#', ''),
('1031', '参数新增', '106', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:add', '#', ''),
('1032', '参数修改', '106', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:edit', '#', ''),
('1033', '参数删除', '106', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:remove', '#', ''),
('1034', '参数导出', '106', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'system:config:export', '#', ''),
('1039', '操作查询', '500', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:query', '#', ''),
('1040', '操作删除', '500', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:remove', '#', ''),
('1041', '日志导出', '500', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:export', '#', ''),
('1042', '登录查询', '501', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:query', '#', ''),
('1043', '登录删除', '501', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:remove', '#', ''),
('1044', '日志导出', '501', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:export', '#', ''),
('1045', '账户解锁', '501', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:unlock', '#', ''),
('1046', '在线查询', '109', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:online:query', '#', ''),
('1047', '批量强退', '109', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:online:batchLogout', '#', ''),
('1048', '单条强退', '109', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:online:forceLogout', '#', ''),
('1049', '任务查询', '110', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:query', '#', ''),
('1050', '任务新增', '110', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:add', '#', ''),
('1051', '任务修改', '110', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:edit', '#', ''),
('1052', '任务删除', '110', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:remove', '#', ''),
('1053', '状态修改', '110', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:changeStatus', '#', ''),
('1054', '任务导出', '110', '6', '#', '', '', '', '1', '0', 'F', '0', '0', 'monitor:job:export', '#', ''),
('1055', '生成查询', '116', '1', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:query', '#', ''),
('1056', '生成修改', '116', '2', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:edit', '#', ''),
('1057', '生成删除', '116', '3', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:remove', '#', ''),
('1058', '导入代码', '116', '4', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:import', '#', ''),
('1059', '预览代码', '116', '5', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:preview', '#', ''),
('1060', '生成代码', '116', '6', '#', '', '', '', '1', '0', 'F', '0', '0', 'tool:gen:code', '#', ''),
('2000', '内容管理', '0', '2', 'content', NULL, NULL, '', '1', '0', 'M', '0', '0', NULL, 'Document', 'B模块内容管理目录（替代原 A1 内容与作品）'),
('2011', '标签管理', '2000', '2', 'tag', 'content/tag/index', NULL, '', '1', '0', 'C', '0', '0', 'content:tag:list', 'Tag', 'B模块内容管理菜单'),
('2012', '外部视频管理', '2000', '8', 'external-drama', NULL, NULL, '', '1', '0', 'M', '1', '0', NULL, 'VideoCamera', 'B模块外部视频管理目录'),
('2013', '作品管理', '2000', '3', 'work', 'content/work/index', NULL, '', '1', '0', 'C', '0', '0', 'content:work:list', 'Document', 'B模块内容管理菜单'),
('2014', '书城作品管理', '2000', '4', 'bookstore', 'content/bookstore/index', NULL, '', '1', '0', 'C', '0', '1', 'content:bookstore:list', 'ShoppingCart', 'B模块内容管理菜单'),
('2015', '排行榜管理', '2000', '5', 'ranking', 'content/ranking/index', NULL, '', '1', '0', 'C', '0', '0', 'content:ranking:list', 'Chart', 'B模块内容管理菜单'),
('2018', '渠道管理', '2012', '1', 'channel', 'content/external-drama/channel/index', NULL, '', '1', '0', 'C', '1', '0', 'content:channel:list', 'Guide', 'B模块外部视频菜单'),
('2019', '视频内容管理', '2012', '2', 'drama', 'content/external-drama/drama/index', NULL, '', '1', '0', 'C', '1', '0', 'content:drama:list', 'VideoPlay', 'B模块外部视频菜单'),
('2023', '标签查询', '2011', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:tag:query', '#', 'B模块内容管理菜单（B_20260928_002，云库适配）'),
('2024', '标签新增', '2011', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:tag:add', '#', 'B模块内容管理菜单（B_20260928_002，云库适配）'),
('2025', '标签编辑', '2011', '3', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:tag:edit', '#', 'B模块内容管理菜单（B_20260928_002，云库适配）'),
('2026', '标签删除', '2011', '4', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:tag:remove', '#', 'B模块内容管理菜单（B_20260928_002，云库适配）'),
('2030', '作品查询', '2013', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:work:query', '#', 'B模块批1菜单（B_20260928_003）'),
('2033', '排行榜查询', '2015', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:ranking:query', '#', 'B模块批1菜单（B_20260928_003）'),
('2034', '排行榜编辑', '2015', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:ranking:edit', '#', 'B模块批1菜单（B_20260928_003）'),
('2040', '渠道查询', '2018', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:channel:query', '#', 'B模块批3菜单（B_20260928_005）'),
('2041', '渠道新增', '2018', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:channel:add', '#', 'B模块批3菜单（B_20260928_005）'),
('2042', '渠道编辑', '2018', '3', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:channel:edit', '#', 'B模块批3菜单（B_20260928_005）'),
('2043', '视频查询', '2019', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:drama:query', '#', 'B模块批3菜单（B_20260928_005）'),
('2044', '视频新增', '2019', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:drama:add', '#', 'B模块批3菜单（B_20260928_005）'),
('2045', '视频编辑', '2019', '3', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:drama:edit', '#', 'B模块批3菜单（B_20260928_005）'),
('3000', '用户中心', '0', '9', 'appuser', NULL, '', NULL, '1', '0', 'M', '0', '0', NULL, 'peoples', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3001', 'App用户与创作者', '3000', '1', 'users', 'user/UserManage', '', 'A4AppUser', '1', '0', 'C', '0', '0', 'user:app:list', 'user', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3002', 'App用户查询', '3001', '1', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:app:query', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3003', 'App用户状态', '3001', '2', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:app:status', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3004', '用户角色授权', '3001', '3', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:app:grant', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3005', '实名审核', '3000', '2', 'realname', 'user/realname/index', '', 'A4RealName', '1', '0', 'C', '0', '0', 'user:realname:list', 'form', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3006', '实名认证记录详情', '3005', '1', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:realname:query', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3009', '作者能力变更', '3001', '4', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:creator:update', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3013', '用户反馈', '3000', '5', 'feedback', 'user/feedback/index', '', 'A4Feedback', '1', '0', 'C', '0', '0', 'user:feedback:list', 'edit', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3014', '反馈详情', '3013', '1', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:feedback:query', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3015', '反馈处理', '3013', '2', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:feedback:handle', '#', 'A4 PC 管理能力权限（A4_20260922_002）'),
('3016', '创作者审核', '3000', '3', 'creator-apply', 'user/creator-apply/index', '', 'A4CreatorApply', '1', '0', 'C', '0', '0', 'user:creatorapp:list', 'star', 'A4-创作者申请审核'),
('3017', '创作者申请查询', '3016', '1', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:creatorapp:query', '#', 'A4-创作者申请查询'),
('3018', '创作者申请审核', '3016', '2', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'user:creatorapp:audit', '#', 'A4-创作者申请审核决定'),
('5000', '工作台', '0', '1', 'workspace', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'dashboard', 'A1SEED'),
('5002', '数据统计', '0', '7', 'statistics', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'Chart', 'A1SEED'),
('5003', '版权审核管理', '0', '3', 'copyright', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'Stamp', 'A1SEED'),
('5004', '交易商务管理', '0', '4', 'trade', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'Sell', 'A1SEED'),
('5005', '平台运维管理', '0', '5', 'operation', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'Operation', '平台运维管理'),
('5006', 'AI 创作与福利', '0', '6', 'ai-group', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'Magic', 'A1SEED'),
('5100', '数据总览', '5000', '1', '/dashboard', 'dashboard/Dashboard', '', '', '1', '0', 'C', '0', '0', 'smartscript:dashboard:view', 'DataLine', 'A1SEED'),
('5120', '作品审核工作台', '5125', '1', 'review', 'copyright/ReviewWorkbench', '', '', '1', '0', 'C', '0', '0', 'smartscript:copyright:review', 'Stamp', 'A1SEED'),
('5121', 'AI审核规则配置', '5125', '2', 'review-rules', 'copyright/AiReviewRules', '', '', '1', '0', 'C', '0', '0', 'smartscript:copyright:reviewRules', 'Stamp', 'A1SEED'),
('5123', '版权资产库管理', '5003', '5', 'assets', 'copyright/CopyrightAssets', '', '', '1', '0', 'C', '0', '0', 'smartscript:copyright:assets', 'Folder', 'A1SEED'),
('5124', '印章审核', '5003', '6', 'seals', 'copyright/SealReview', '', '', '1', '0', 'C', '0', '0', 'smartscript:copyright:seals', 'Stamp', 'A1SEED'),
('5125', 'AI初审+人工复核', '5003', '1', 'ai-review', NULL, '', '', '1', '0', 'M', '0', '0', NULL, 'Stamp', 'A1SEED'),
('5126', '版权申请管理', '5003', '2', 'application', 'copyright/CopyrightApplicationManage', '', '', '1', '0', 'C', '0', '0', 'content:copyright:application', 'Form', '版权申请管理页面'),
('5127', '版权中心配置', '5003', '3', 'center-config', 'copyright/CopyrightCenterConfig', NULL, '', '1', '0', 'C', '0', '0', 'content:copyright:config', 'Setting', '版权中心配置页面'),
('5131', '订单管理', '5004', '4', 'orders', 'trade/AuthOrders', '', '', '1', '0', 'C', '0', '0', 'trade:orders:list', 'Sell', 'A1SEED'),
('5133', '询盘管理', '5004', '2', 'inquiry', 'trade/Inquiry', '', '', '1', '0', 'C', '0', '0', 'trade:inquiry:list', 'Chat', 'A1SEED'),
('5138', '任务管理', '5004', '8', 'demand', 'trade/Demand', '', '', '1', '0', 'C', '0', '0', 'trade:demand:list', 'List', 'C模块交易菜单补全'),
('5139', '任务运营详情', '5004', '9', 'demand-detail', 'trade/DemandDetail', '', '', '1', '0', 'C', '1', '0', 'trade:demand:query', '#', '逐轮验收/资金流水/延时记录只读运营视图'),
('5140', '广告运营配置', '5005', '1', 'ad-config', 'operation/AdConfig', '', '', '1', '0', 'C', '0', '0', 'smartscript:ops:adConfig', 'Picture', 'A1SEED'),
('5141', '版权申请查询', '5126', '1', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:copyright:application:query', '#', '版权申请查询按钮权限'),
('5142', '版权申请详情', '5126', '2', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:copyright:application:query', '#', '版权申请详情按钮权限'),
('5143', '版权申请删除', '5126', '3', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:copyright:application:remove', '#', '版权申请删除按钮权限'),
('5144', '版权申请同步', '5126', '4', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:copyright:application:query', '#', '版权申请同步按钮权限'),
('5145', '版权申请导出', '5126', '5', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:copyright:application:export', '#', '版权申请导出按钮权限'),
('5150', 'ai创作skill和模型配置', '5006', '1', '/ai/operations', 'system/AiOperations', '', '', '1', '0', 'C', '0', '0', 'smartscript:ai:operations', 'Magic', 'A1SEED'),
('5151', '福利与积分配置', '5006', '2', '/support/welfare', 'welfare/WelfarePoints', '', '', '1', '0', 'C', '0', '0', 'smartscript:support:welfare', 'Present', 'A1SEED'),
('5152', '消息与公告', '5005', '5', '/support/messages', 'support/MessagesAnnouncements', '', '', '1', '0', 'C', '0', '0', 'smartscript:support:messages', 'Bell', '统一消息与公告入口'),
('5182', '订单查询', '5131', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:orders:query', '#', 'C 模块交易按钮权限'),
('5185', '询盘查询', '5133', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:inquiry:query', '#', 'C 模块交易按钮权限'),
('5186', '询盘跟进', '5133', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:inquiry:edit', '#', 'C 模块交易按钮权限'),
('5187', '询盘转订单', '5133', '3', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:inquiry:convert', '#', 'C 模块交易按钮权限'),
('5188', '报价接受/拒绝', '5133', '6', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:quote:edit', '#', 'C 模块交易按钮权限'),
('5193', '征集投稿查询', '5138', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:demand:query', '#', 'C 模块交易按钮权限'),
('5194', '任务发布', '5138', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:demand:add', '#', '平台代需求方发布任务（含验收轮次）'),
('5195', '询盘发起', '5133', '4', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:inquiry:add', '#', 'C 模块交易操作按钮权限'),
('5196', '报价提交/议价', '5133', '7', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:quote:add', '#', 'C 模块交易操作按钮权限'),
('5197', '任务运营编辑', '5138', '3', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:demand:edit', '#', '编辑/关闭任务与延时审批'),
('5198', '任务逐轮验收', '5138', '4', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:demand:review', '#', '逐轮交付验收与放款触发'),
('5201', '会话列表', '3000', '6', 'chat-sessions', 'chat/ChatSessions', '', '', '1', '0', 'C', '0', '0', 'chat:session:list', 'list', 'A3 用户沟通会话列表（原 5200 目录迁入用户中心）'),
('5202', '会话详情', '3000', '7', 'chat-detail', 'chat/ChatDetail', '', '', '1', '0', 'C', '1', '0', 'chat:session:query', '#', 'A3 用户沟通会话详情（原 5200 目录迁入用户中心）'),
('5203', '分配管理员', '5201', '1', '', NULL, '', '', '1', '0', 'F', '0', '0', 'chat:session:assign', '#', 'A3 分配管理员按钮'),
('5204', '变更状态', '5201', '2', '', NULL, '', '', '1', '0', 'F', '0', '0', 'chat:session:status', '#', 'A3 变更状态按钮'),
('5205', '发送消息', '5201', '3', '', NULL, '', '', '1', '0', 'F', '0', '0', 'chat:message:send', '#', 'A3 发送消息按钮'),
('5206', '查看消息', '5201', '4', '', NULL, '', '', '1', '0', 'F', '0', '0', 'chat:message:list', '#', 'A3 查看消息按钮'),
('5207', '标记已读', '5201', '5', '', NULL, '', '', '1', '0', 'F', '0', '0', 'chat:session:read', '#', 'A3 标记已读按钮'),
('5208', '创建会话', '5201', '6', '', NULL, '', '', '1', '0', 'F', '0', '0', 'chat:session:create', '#', 'A3 创建会话按钮'),
('5209', '提现审核', '5003', '9', 'withdraw-review', 'copyright/WithdrawReview', NULL, '', '1', '0', 'C', '1', '0', 'smartscript:copyright:withdraw:list', 'money', '已并入结算与提现页；侧栏隐藏，权限保留'),
('5215', '版权中心配置查询', '5127', '1', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'content:copyright:config:query', '#', '版权中心配置查询按钮'),
('5216', '版权中心配置新增', '5127', '2', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'content:copyright:config:add', '#', '版权中心配置新增按钮'),
('5217', '版权中心配置编辑', '5127', '3', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'content:copyright:config:edit', '#', '版权中心配置编辑按钮'),
('5218', '版权中心配置删除', '5127', '4', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'content:copyright:config:remove', '#', '版权中心配置删除按钮'),
('5219', '版权中心配置导出', '5127', '5', '#', NULL, '', NULL, '1', '0', 'F', '0', '0', 'content:copyright:config:export', '#', '版权中心配置导出按钮'),
('5221', '提现导出', '5209', '6', '', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:withdraw:export', '#', '??????'),
('5222', '结算与提现', '5003', '8', 'settlement-manage', 'copyright/SettlementWithdrawManage', NULL, '', '1', '0', 'C', '0', '0', 'smartscript:copyright:settlement:list', 'Money', '结算单与提现审核同页（Tab）；默认进入结算单'),
('5223', '个人收益管理', '5003', '10', 'income-manage', 'copyright/IncomeManage', NULL, '', '1', '0', 'C', '0', '0', 'smartscript:copyright:income:list', 'Money', '个人收益只读核查：账户余额与账单流水'),
('5224', '查询结算', '5222', '1', '', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:settlement:query', '#', '?????????'),
('5226', '处理异常', '5222', '3', '', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:settlement:handle', '#', '?????????'),
('5227', '查询收益', '5223', '1', '', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:income:query', '#', '个人收益账户/流水查询'),
('5229', '电子证书管理', '5003', '5', 'cert-manage', 'copyright/CopyrightCertManage', NULL, '', '1', '0', 'C', '0', '0', 'smartscript:copyright:cert', 'document', '版权存证电子证书管理'),
('5231', '合同管理', '5003', '7', 'contract-manage', 'copyright/ContractManage', NULL, '', '1', '0', 'C', '0', '0', 'smartscript:copyright:contract:list', 'DocumentCopy', '版权结算合同管理'),
('5232', '合同查询', '5231', '1', '#', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:query', '#', ''),
('5233', '合同新增', '5231', '2', '#', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:add', '#', ''),
('5234', '合同修改', '5231', '3', '#', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:edit', '#', ''),
('5235', '合同删除', '5231', '4', '#', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:remove', '#', ''),
('5236', '合同导出', '5231', '5', '#', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:export', '#', ''),
('5300', '公告列表', '5152', '1', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'system:notice:list', '#', '消息与公告权限'),
('5301', '公告详情', '5152', '2', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'system:notice:query', 'document', '消息与公告权限'),
('5302', '公告新增', '5152', '3', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'system:notice:add', '#', '消息与公告权限'),
('5303', '公告修改', '5152', '4', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'system:notice:edit', '#', '消息与公告权限'),
('5304', '公告删除', '5152', '5', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'system:notice:remove', '#', '消息与公告权限'),
('5305', '消息列表', '5152', '6', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'user:message:list', '#', '消息与公告权限'),
('5306', '消息详情', '5152', '7', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'user:message:query', '#', '消息与公告权限'),
('5307', '消息发送', '5152', '8', '#', NULL, '', '', '1', '0', 'F', '0', '0', 'user:message:add', '#', '消息与公告权限'),
('5308', '合同作废', '5231', '7', '', '', NULL, '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:cancel', '#', '????'),
('5401', '提现查询', '5209', '1', '', NULL, '', '', '1', '0', 'F', '0', '0', 'smartscript:copyright:withdraw:query', '#', '提现详情查询（对齐后端校验口径）'),
('5402', '书城列表', '2013', '5', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:bookstore:list', '#', '书城作品列表（并入作品管理）'),
('5403', '书城查询', '2013', '6', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:bookstore:query', '#', '书城作品查询（并入作品管理）'),
('5404', '书城编辑', '2013', '7', '', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:bookstore:edit', '#', '书城作品编辑（并入作品管理）'),
('5405', '实名审核决定', '3005', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'user:realname:audit', '#', ''),
('5411', '作品推广', '2000', '4', 'promotion', 'content/promotion/index', NULL, '', '1', '0', 'C', '0', '0', 'content:promotion:list', 'Star', ''),
('5412', '推广查询', '5411', '1', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:query', '#', '作品推广查询'),
('5413', '推广新增', '5411', '2', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:add', '#', '新增推广申请/轮播图'),
('5414', '推广修改', '5411', '3', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:edit', '#', '编辑推广申请/轮播图'),
('5415', '推广删除', '5411', '4', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:remove', '#', '删除推广申请/轮播图'),
('5416', '推广审核', '5411', '5', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:audit', '#', '审核推广申请'),
('5417', '价格配置', '5411', '6', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:price', '#', '位次单价与启停配置'),
('5418', '免费上推荐', '5411', '7', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'content:promotion:grant', '#', '管理员免费直上推荐'),
('5420', '生成合同', '5231', '10', '', NULL, '', '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:generate', '#', '生成电子合同（对齐后端校验口径）'),
('5421', '签署登记', '5231', '11', '', NULL, '', '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:sign', '#', '登记合同签署状态（对齐后端校验口径）'),
('5422', '合同归档', '5231', '12', '', NULL, '', '', '1', '0', 'F', '0', '0', 'smartscript:copyright:contract:archive', '#', '合同归档（对齐后端校验口径）'),
('5424', '报价记录', '5133', '5', '#', NULL, NULL, '', '1', '0', 'F', '0', '0', 'trade:quote:list', '#', '报价管理并入询盘管理：行内查看该询盘全部报价')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),parent_id=VALUES(parent_id),order_num=VALUES(order_num),path=VALUES(path),component=VALUES(component),query=VALUES(query),route_name=VALUES(route_name),is_frame=VALUES(is_frame),is_cache=VALUES(is_cache),menu_type=VALUES(menu_type),visible=VALUES(visible),status=VALUES(status),perms=VALUES(perms),icon=VALUES(icon),remark=VALUES(remark);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1),
(1, 2),
(1, 3),
(1, 4),
(1, 100),
(1, 101),
(1, 102),
(1, 103),
(1, 104),
(1, 105),
(1, 106),
(1, 500),
(1, 501),
(1, 1000),
(1, 1001),
(1, 1002),
(1, 1003),
(1, 1004),
(1, 1005),
(1, 1006),
(1, 1007),
(1, 1008),
(1, 1009),
(1, 1010),
(1, 1011),
(1, 1012),
(1, 1013),
(1, 1014),
(1, 1015),
(1, 1016),
(1, 1017),
(1, 1018),
(1, 1019),
(1, 1020),
(1, 1021),
(1, 1022),
(1, 1023),
(1, 1024),
(1, 1025),
(1, 1026),
(1, 1027),
(1, 1028),
(1, 1029),
(1, 1030),
(1, 1031),
(1, 1032),
(1, 1033),
(1, 1034),
(1, 1039),
(1, 1040),
(1, 1041),
(1, 1042),
(1, 1043),
(1, 1044),
(1, 1045),
(1, 1046),
(1, 1047),
(1, 1048),
(1, 1049),
(1, 1050),
(1, 1051),
(1, 1052),
(1, 1053),
(1, 1054),
(1, 1055),
(1, 1056),
(1, 1057),
(1, 1058),
(1, 1059),
(1, 1060),
(1, 2000),
(1, 2011),
(1, 2012),
(1, 2013),
(1, 2014),
(1, 2015),
(1, 2018),
(1, 2019),
(1, 2023),
(1, 2024),
(1, 2025),
(1, 2026),
(1, 2030),
(1, 2033),
(1, 2034),
(1, 2040),
(1, 2041),
(1, 2042),
(1, 2043),
(1, 2044),
(1, 2045),
(1, 3000),
(1, 3001),
(1, 3002),
(1, 3003),
(1, 3004),
(1, 3005),
(1, 3006),
(1, 3009),
(1, 3013),
(1, 3014),
(1, 3015),
(1, 3016),
(1, 3017),
(1, 3018),
(1, 5000),
(1, 5002),
(1, 5003),
(1, 5004),
(1, 5005),
(1, 5006),
(1, 5100),
(1, 5120),
(1, 5121),
(1, 5123),
(1, 5124),
(1, 5125),
(1, 5126),
(1, 5127),
(1, 5131),
(1, 5133),
(1, 5138),
(1, 5139),
(1, 5140),
(1, 5141),
(1, 5142),
(1, 5143),
(1, 5144),
(1, 5145),
(1, 5150),
(1, 5151),
(1, 5152),
(1, 5182),
(1, 5185),
(1, 5186),
(1, 5187),
(1, 5188),
(1, 5193),
(1, 5194),
(1, 5195),
(1, 5196),
(1, 5197),
(1, 5198),
(1, 5201),
(1, 5202),
(1, 5203),
(1, 5204),
(1, 5205),
(1, 5206),
(1, 5207),
(1, 5208),
(1, 5209),
(1, 5215),
(1, 5216),
(1, 5217),
(1, 5218),
(1, 5219),
(1, 5221),
(1, 5222),
(1, 5223),
(1, 5224),
(1, 5226),
(1, 5227),
(1, 5229),
(1, 5231),
(1, 5301),
(1, 5302),
(1, 5303),
(1, 5304),
(1, 5305),
(1, 5306),
(1, 5307),
(1, 5308),
(1, 5401),
(1, 5402),
(1, 5403),
(1, 5404),
(1, 5405),
(1, 5411),
(1, 5412),
(1, 5413),
(1, 5414),
(1, 5415),
(1, 5416),
(1, 5417),
(1, 5418),
(1, 5420),
(1, 5421),
(1, 5422),
(1, 5424),
(2, 2),
(2, 3),
(2, 4),
(2, 500),
(2, 501),
(2, 1039),
(2, 1040),
(2, 1041),
(2, 1042),
(2, 1043),
(2, 1044),
(2, 1045),
(2, 1046),
(2, 1047),
(2, 1048),
(2, 1049),
(2, 1050),
(2, 1051),
(2, 1052),
(2, 1053),
(2, 1054),
(2, 1055),
(2, 1056),
(2, 1057),
(2, 1058),
(2, 1059),
(2, 1060),
(2, 5002),
(100, 5000),
(100, 5002),
(100, 5100),
(101, 2),
(101, 3),
(101, 4),
(101, 500),
(101, 501),
(101, 1000),
(101, 1001),
(101, 1002),
(101, 1003),
(101, 1004),
(101, 1005),
(101, 1006),
(101, 1007),
(101, 1008),
(101, 1009),
(101, 1010),
(101, 1011),
(101, 1012),
(101, 1013),
(101, 1014),
(101, 1015),
(101, 1016),
(101, 1017),
(101, 1018),
(101, 1019),
(101, 1020),
(101, 1021),
(101, 1022),
(101, 1023),
(101, 1024),
(101, 1025),
(101, 1026),
(101, 1027),
(101, 1028),
(101, 1029),
(101, 1030),
(101, 1031),
(101, 1032),
(101, 1033),
(101, 1034),
(101, 1039),
(101, 1040),
(101, 1041),
(101, 1042),
(101, 1043),
(101, 1044),
(101, 1045),
(101, 1046),
(101, 1047),
(101, 1048),
(101, 1049),
(101, 1050),
(101, 1051),
(101, 1052),
(101, 1053),
(101, 1054),
(101, 1055),
(101, 1056),
(101, 1057),
(101, 1058),
(101, 1059),
(101, 1060),
(101, 2000),
(101, 2011),
(101, 2012),
(101, 2013),
(101, 2014),
(101, 2015),
(101, 2018),
(101, 2019),
(101, 2023),
(101, 2024),
(101, 2025),
(101, 2026),
(101, 2030),
(101, 2033),
(101, 2034),
(101, 2040),
(101, 2041),
(101, 2042),
(101, 2043),
(101, 2044),
(101, 2045),
(101, 3000),
(101, 3001),
(101, 3002),
(101, 3003),
(101, 3004),
(101, 3005),
(101, 3006),
(101, 3009),
(101, 3013),
(101, 3014),
(101, 3015),
(101, 5000),
(101, 5002),
(101, 5003),
(101, 5004),
(101, 5005),
(101, 5006),
(101, 5100),
(101, 5120),
(101, 5121),
(101, 5123),
(101, 5124),
(101, 5125),
(101, 5131),
(101, 5133),
(101, 5138),
(101, 5139),
(101, 5140),
(101, 5150),
(101, 5151),
(101, 5152),
(101, 5182),
(101, 5185),
(101, 5186),
(101, 5187),
(101, 5188),
(101, 5193),
(101, 5194),
(101, 5195),
(101, 5196),
(101, 5197),
(101, 5198),
(101, 5201),
(101, 5202),
(101, 5203),
(101, 5204),
(101, 5205),
(101, 5206),
(101, 5207),
(101, 5208),
(101, 5209),
(101, 5222),
(101, 5305),
(101, 5306),
(101, 5307),
(101, 5401),
(101, 5402),
(101, 5403),
(101, 5404),
(101, 5411),
(101, 5412),
(101, 5413),
(101, 5414),
(101, 5415),
(101, 5416),
(101, 5417),
(101, 5418),
(101, 5424);

-- 受限运营角色按线上口径只保留 3 个入口
DELETE FROM sys_role_menu WHERE role_id = 2 AND menu_id IN (109,110,111,112,113,114,115,116,117);

COMMIT;

-- =====================================================================
-- 收口自检（SUMMARY）：结构、触发器、菜单、授权、配置、字典齐备且无悬空
-- 末行必须是 PASS<TAB>fail_cnt=0, ...；两个初始化脚本的 summary 闸门据此判定。
-- =====================================================================
CREATE TEMPORARY TABLE _ss_expect_menu (menu_id BIGINT PRIMARY KEY);
INSERT INTO _ss_expect_menu VALUES (1),(2),(3),(4),(100),(101),(102),(103),(104),(105),(106),(109),(110),(111),(112),(113),(114),(115),(116),(117),(500),(501),(1000),(1001),(1002),(1003),(1004),(1005),(1006),(1007),(1008),(1009),(1010),(1011),(1012),(1013),(1014),(1015),(1016),(1017),(1018),(1019),(1020),(1021),(1022),(1023),(1024),(1025),(1026),(1027),(1028),(1029),(1030),(1031),(1032),(1033),(1034),(1039),(1040),(1041),(1042),(1043),(1044),(1045),(1046),(1047),(1048),(1049),(1050),(1051),(1052),(1053),(1054),(1055),(1056),(1057),(1058),(1059),(1060),(2000),(2011),(2012),(2013),(2014),(2015),(2018),(2019),(2023),(2024),(2025),(2026),(2030),(2033),(2034),(2040),(2041),(2042),(2043),(2044),(2045),(3000),(3001),(3002),(3003),(3004),(3005),(3006),(3009),(3013),(3014),(3015),(3016),(3017),(3018),(5000),(5002),(5003),(5004),(5005),(5006),(5100),(5120),(5121),(5123),(5124),(5125),(5126),(5127),(5131),(5133),(5138),(5139),(5140),(5141),(5142),(5143),(5144),(5145),(5150),(5151),(5152),(5182),(5185),(5186),(5187),(5188),(5193),(5194),(5195),(5196),(5197),(5198),(5201),(5202),(5203),(5204),(5205),(5206),(5207),(5208),(5209),(5215),(5216),(5217),(5218),(5219),(5221),(5222),(5223),(5224),(5226),(5227),(5229),(5231),(5232),(5233),(5234),(5235),(5236),(5300),(5301),(5302),(5303),(5304),(5305),(5306),(5307),(5308),(5401),(5402),(5403),(5404),(5405),(5411),(5412),(5413),(5414),(5415),(5416),(5417),(5418),(5420),(5421),(5422),(5424);

CREATE TEMPORARY TABLE _ss_expect_grant (role_id BIGINT, menu_id BIGINT, PRIMARY KEY (role_id, menu_id));
INSERT INTO _ss_expect_grant VALUES (1,1),(1,2),(1,3),(1,4),(1,100),(1,101),(1,102),(1,103),(1,104),(1,105),(1,106),(1,500),(1,501),(1,1000),(1,1001),(1,1002),(1,1003),(1,1004),(1,1005),(1,1006),(1,1007),(1,1008),(1,1009),(1,1010),(1,1011),(1,1012),(1,1013),(1,1014),(1,1015),(1,1016),(1,1017),(1,1018),(1,1019),(1,1020),(1,1021),(1,1022),(1,1023),(1,1024),(1,1025),(1,1026),(1,1027),(1,1028),(1,1029),(1,1030),(1,1031),(1,1032),(1,1033),(1,1034),(1,1039),(1,1040),(1,1041),(1,1042),(1,1043),(1,1044),(1,1045),(1,1046),(1,1047),(1,1048),(1,1049),(1,1050),(1,1051),(1,1052),(1,1053),(1,1054),(1,1055),(1,1056),(1,1057),(1,1058),(1,1059),(1,1060),(1,2000),(1,2011),(1,2012),(1,2013),(1,2014),(1,2015),(1,2018),(1,2019),(1,2023),(1,2024),(1,2025),(1,2026),(1,2030),(1,2033),(1,2034),(1,2040),(1,2041),(1,2042),(1,2043),(1,2044),(1,2045),(1,3000),(1,3001),(1,3002),(1,3003),(1,3004),(1,3005),(1,3006),(1,3009),(1,3013),(1,3014),(1,3015),(1,3016),(1,3017),(1,3018),(1,5000),(1,5002),(1,5003),(1,5004),(1,5005),(1,5006),(1,5100),(1,5120),(1,5121),(1,5123),(1,5124),(1,5125),(1,5126),(1,5127),(1,5131),(1,5133),(1,5138),(1,5139),(1,5140),(1,5141),(1,5142),(1,5143),(1,5144),(1,5145),(1,5150),(1,5151),(1,5152),(1,5182),(1,5185),(1,5186),(1,5187),(1,5188),(1,5193),(1,5194),(1,5195),(1,5196),(1,5197),(1,5198),(1,5201),(1,5202),(1,5203),(1,5204),(1,5205),(1,5206),(1,5207),(1,5208),(1,5209),(1,5215),(1,5216),(1,5217),(1,5218),(1,5219),(1,5221),(1,5222),(1,5223),(1,5224),(1,5226),(1,5227),(1,5229),(1,5231),(1,5301),(1,5302),(1,5303),(1,5304),(1,5305),(1,5306),(1,5307),(1,5308),(1,5401),(1,5402),(1,5403),(1,5404),(1,5405),(1,5411),(1,5412),(1,5413),(1,5414),(1,5415),(1,5416),(1,5417),(1,5418),(1,5420),(1,5421),(1,5422),(1,5424),(2,2),(2,3),(2,4),(2,500),(2,501),(2,1039),(2,1040),(2,1041),(2,1042),(2,1043),(2,1044),(2,1045),(2,1046),(2,1047),(2,1048),(2,1049),(2,1050),(2,1051),(2,1052),(2,1053),(2,1054),(2,1055),(2,1056),(2,1057),(2,1058),(2,1059),(2,1060),(2,5002),(100,5000),(100,5002),(100,5100),(101,2),(101,3),(101,4),(101,500),(101,501),(101,1000),(101,1001),(101,1002),(101,1003),(101,1004),(101,1005),(101,1006),(101,1007),(101,1008),(101,1009),(101,1010),(101,1011),(101,1012),(101,1013),(101,1014),(101,1015),(101,1016),(101,1017),(101,1018),(101,1019),(101,1020),(101,1021),(101,1022),(101,1023),(101,1024),(101,1025),(101,1026),(101,1027),(101,1028),(101,1029),(101,1030),(101,1031),(101,1032),(101,1033),(101,1034),(101,1039),(101,1040),(101,1041),(101,1042),(101,1043),(101,1044),(101,1045),(101,1046),(101,1047),(101,1048),(101,1049),(101,1050),(101,1051),(101,1052),(101,1053),(101,1054),(101,1055),(101,1056),(101,1057),(101,1058),(101,1059),(101,1060),(101,2000),(101,2011),(101,2012),(101,2013),(101,2014),(101,2015),(101,2018),(101,2019),(101,2023),(101,2024),(101,2025),(101,2026),(101,2030),(101,2033),(101,2034),(101,2040),(101,2041),(101,2042),(101,2043),(101,2044),(101,2045),(101,3000),(101,3001),(101,3002),(101,3003),(101,3004),(101,3005),(101,3006),(101,3009),(101,3013),(101,3014),(101,3015),(101,5000),(101,5002),(101,5003),(101,5004),(101,5005),(101,5006),(101,5100),(101,5120),(101,5121),(101,5123),(101,5124),(101,5125),(101,5131),(101,5133),(101,5138),(101,5139),(101,5140),(101,5150),(101,5151),(101,5152),(101,5182),(101,5185),(101,5186),(101,5187),(101,5188),(101,5193),(101,5194),(101,5195),(101,5196),(101,5197),(101,5198),(101,5201),(101,5202),(101,5203),(101,5204),(101,5205),(101,5206),(101,5207),(101,5208),(101,5209),(101,5222),(101,5305),(101,5306),(101,5307),(101,5401),(101,5402),(101,5403),(101,5404),(101,5411),(101,5412),(101,5413),(101,5414),(101,5415),(101,5416),(101,5417),(101,5418),(101,5424);

CREATE TEMPORARY TABLE _ss_retired_menu (menu_id BIGINT PRIMARY KEY);
INSERT INTO _ss_retired_menu VALUES (1035),(1036),(1037),(1038),(5183),(5184),(2027),(2028),(2029),(2046),(2047),(2048),(2049),(2050),(2051),(2052),(5230),(5171),(5172),(5173),(5174),(5175),(5220),(5225),(5423),(5425),(2010),(2017),(2020),(2021),(2022),(2031),(2032),(2039),(5110),(5111),(5122),(5134),(5320),(5400),(3010),(3011),(3012),(107),(108),(5001),(5200),(5189),(5190),(5191),(5192),(5419);

SET @fail_cnt :=
    ((SELECT COUNT(*) FROM information_schema.TABLES
       WHERE TABLE_SCHEMA = DATABASE()
         AND LOWER(TABLE_NAME) IN ('app_refresh_session','app_sms_code','app_user_consent','app_user_email','app_user_oauth','demand_sandbox_payment','gen_table','gen_table_column','qrtz_blob_triggers','qrtz_calendars','qrtz_cron_triggers','qrtz_fired_triggers','qrtz_job_details','qrtz_locks','qrtz_paused_trigger_grps','qrtz_scheduler_state','qrtz_simple_triggers','qrtz_simprop_triggers','qrtz_triggers','sandbox_alipay_copyright_payment','sandbox_alipay_payment','sandbox_alipay_promotion_payment','schema_migrations','sys_ad_config','sys_ad_revenue','sys_ai_channel','sys_ai_quota_account','sys_ai_quota_record','sys_ai_request','sys_ai_review_rule','sys_ai_skill','sys_ai_write_record','sys_banner','sys_bill_record','sys_blacklist','sys_bookshelf_record','sys_business_follow','sys_category','sys_chapter_review','sys_chat_message','sys_chat_session','sys_chat_trade_template','sys_comment','sys_config','sys_contact_profile','sys_contract','sys_contract_event','sys_copyright_application','sys_copyright_authorization','sys_copyright_center_config','sys_copyright_cert','sys_copyright_pay_order','sys_copyright_seal','sys_copyright_seal_log','sys_copyright_supplement','sys_copyright_third_party_message','sys_creator_stats','sys_delivery','sys_delivery_file','sys_demand','sys_demand_extend_apply','sys_demand_stage','sys_demand_submission','sys_demand_tag','sys_dept','sys_dict_data','sys_dict_type','sys_draft','sys_drama_channel','sys_episode','sys_episode_like','sys_escrow','sys_external_drama','sys_favorite','sys_income_account','sys_income_settle','sys_inquiry','sys_invoice','sys_job','sys_job_log','sys_logininfor','sys_menu','sys_notice','sys_notice_read','sys_offline_cooperation','sys_oper_log','sys_operation_log','sys_order','sys_order_status_log','sys_partner','sys_play_history','sys_play_progress','sys_points_account','sys_points_record','sys_post','sys_promotion_application','sys_promotion_slot','sys_quote','sys_ranking_snapshot','sys_read_record','sys_recharge_order','sys_report','sys_review_log','sys_review_record','sys_risk_rule','sys_role','sys_role_dept','sys_role_menu','sys_search_history','sys_settlement','sys_subscribe','sys_tag','sys_task','sys_trade_agent_message','sys_trade_agent_session','sys_unlock_record','sys_user','sys_user_block','sys_user_follow','sys_user_post','sys_user_role','sys_user_task','sys_withdraw_apply','sys_work','sys_work_chapter','sys_work_file','sys_work_gen_job','sys_work_ledger','sys_work_rating','sys_work_storyboard','sys_work_tag','sys_work_timeline_event','sys_work_version','sys_work_volume','user_author_capability','user_creator_application','user_creator_profile','user_feedback','user_notification','user_notification_preference','user_notification_receiver','user_phone_change_log','user_real_name_auth')) <> 143)
  + ((SELECT COUNT(*) FROM information_schema.TRIGGERS
       WHERE TRIGGER_SCHEMA = DATABASE()
         AND TRIGGER_NAME IN ('trg_sys_favorite_ai_work_count','trg_sys_favorite_ad_work_count',
                              'trg_sys_ai_request_bu_finished_time','trg_sys_review_record_au_sync_work')) <> 4)
  + ((SELECT COUNT(*) FROM _ss_expect_menu e LEFT JOIN sys_menu m ON m.menu_id = e.menu_id
       WHERE m.menu_id IS NULL) <> 0)
  + ((SELECT COUNT(*) FROM _ss_expect_grant g LEFT JOIN sys_role_menu rm
         ON rm.role_id = g.role_id AND rm.menu_id = g.menu_id
       WHERE rm.menu_id IS NULL) <> 0)
  + ((SELECT COUNT(*) FROM _ss_retired_menu r JOIN sys_menu m ON m.menu_id = r.menu_id) <> 0)
  + ((SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
       WHERE m.parent_id <> 0 AND p.menu_id IS NULL) <> 0)
  + ((SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.menu_id = rm.menu_id
       WHERE m.menu_id IS NULL) <> 0)
  + ((SELECT COUNT(*) FROM sys_config WHERE config_id IN (1,2,3,4,5,6,7,8,101,104,105,106,107,108,109,110,111,112,113,114,115)) <> 21)
  + ((SELECT COUNT(*) FROM sys_dict_type WHERE dict_id IN (1,2,3,4,5,6,7,8,9,10,100,101,102,103,104,105,106,107,108,109,110,111,112,113)) <> 24)
  + ((SELECT COUNT(*) FROM sys_dict_data WHERE dict_code IN (28,29,10,11,8,9,6,7,16,17,14,15,19,20,21,22,23,24,25,26,27,18,4,5,1,2,3,12,13,146,147,148,149,150,151,139,140,141,164,165,136,137,138,133,134,135,109,110,111,112,113,114,127,128,129,130,100,101,102,103,104,105,106,107,108,124,125,126,121,122,123,119,120,115,116,117,118,163,142,143,144,145,131,132)) <> 84)
  + ((SELECT COUNT(*) FROM sys_job WHERE invoke_target IN
       ('ryTask.ryNoParams','ryTask.ryParams(\'ry\')',
        'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',
        'tradeInquiryTask.closeExpiredInquiries','tradeDemandTask.expireOverdueRounds')) <> 5);

SELECT IF(@fail_cnt = 0, 'PASS', 'FAIL') AS result,
       CONCAT('fail_cnt=', @fail_cnt,
              ', tables=', (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()),
              ', triggers=', (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()),
              ', menus=', (SELECT COUNT(*) FROM sys_menu),
              ', role_menu=', (SELECT COUNT(*) FROM sys_role_menu),
              ', configs=', (SELECT COUNT(*) FROM sys_config),
              ', dict_types=', (SELECT COUNT(*) FROM sys_dict_type),
              ', dict_data=', (SELECT COUNT(*) FROM sys_dict_data),
              ', jobs=', (SELECT COUNT(*) FROM sys_job)) AS details;

DROP TEMPORARY TABLE _ss_expect_menu;
DROP TEMPORARY TABLE _ss_expect_grant;
DROP TEMPORARY TABLE _ss_retired_menu;

