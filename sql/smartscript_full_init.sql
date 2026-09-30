-- =====================================================================
-- smartscript_full_init.sql —— 平台数据库全量初始化/增量升级（唯一入口）
-- =====================================================================
-- 用途：一个文件同时支持两种场景，可重复执行（幂等）：
--   1) 全新空库：完整导入若依基线 + 平台全部结构/菜单/权限/字典/任务。
--   2) 已有库（含历史版本建的库）：自动补齐缺失的表/列/索引/菜单/字典，
--      不删除、不覆盖任何既有业务数据。
-- 建议通过 scripts/db/init-database.sh / .ps1 执行（内部按本清单跑这一个文件）；
-- 也可直接： mysql -h<host> -u<user> -p <库名> < sql/smartscript_full_init.sql
--
-- 结构：
--   第 0 部分  若依基线（仅当 sys_dept 不存在，即空库时才导入）
--   第 1 部分  sys_user 加固与数据归一（原 A2 迁移）
--   第 2 部分  App/用户域 11 张表（原 A2 迁移 + A4-001 增量折入）
--   第 3 部分  sys_user.bio（A5）、H12 覆盖索引、sys_role.app_grantable（A4-003）
--   第 4 部分  产品/用户中心/内容/交易菜单（A1+PC+A4-002+B1+C 最终形态，upsert）
--   第 5 部分  角色与授权（A1 受限运营角色、超管授权）
--   第 6 部分  C 交易域 15 张业务表（含列补齐与唯一索引）
--   第 7 部分  交易字典（14 类型 / 52 项）
--   第 8 部分  询盘过期自动关闭定时任务
-- 与历史迁移脚本的差异：
--   - 原迁移的控制表/快照表（a2_/a4_ 前缀）为升级回滚记账服务，此处不需要，已省略。
--   - 原 C 迁移交易按钮 menu_id 5160-5174 与 B1 内容按钮 5160-5166 冲突（静默漏插），
--     已将交易按钮重编号为 5180-5196。
--   - 基线若依内容内嵌于下方第 0 部分；sql/ry_20260320.sql 仅为 A3 联调子集保留。
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
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
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
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- | ----------------------------
-- | 初始化-用户信息表数据
-- | ----------------------------
insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试员');


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
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


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
-- | 一级菜单
insert into sys_menu values('1', '系统管理', '0', '1', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '4', 'http://ruoyi.vip', null, '', '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', sysdate(), '', null, '若依官网地址');
-- | 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- | 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
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
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
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
-- | 初始化-角色和菜单关联表数据
-- | ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
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

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');


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
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- | ----------------------------
-- | 初始化-公告信息表数据
-- | ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('3', '若依开源框架介绍', '1', '<p><span style=\"color: rgb(230, 0, 0);\">项目介绍</span></p><p><font color=\"#333333\">RuoYi开源项目是为企业用户定制的后台脚手架框架，为企业打造的一站式解决方案，降低企业开发成本，提升开发效率。主要包括用户管理、角色管理、部门管理、菜单管理、参数管理、字典管理、</font><span style=\"color: rgb(51, 51, 51);\">岗位管理</span><span style=\"color: rgb(51, 51, 51);\">、定时任务</span><span style=\"color: rgb(51, 51, 51);\">、</span><span style=\"color: rgb(51, 51, 51);\">服务监控、登录日志、操作日志、代码生成等功能。其中，还支持多数据源、数据权限、国际化、Redis缓存、Docker部署、滑动验证码、第三方认证登录、分布式事务、</span><font color=\"#333333\">分布式文件存储</font><span style=\"color: rgb(51, 51, 51);\">、分库分表处理等技术特点。</span></p><p><img src=\"https://foruda.gitee.com/images/1773931848342439032/a4d22313_1815095.png\" style=\"width: 64px;\"><br></p><p><span style=\"color: rgb(230, 0, 0);\">官网及演示</span></p><p><span style=\"color: rgb(51, 51, 51);\">若依官网地址：&nbsp;</span><a href=\"http://ruoyi.vip\" target=\"_blank\">http://ruoyi.vip</a><a href=\"http://ruoyi.vip\" target=\"_blank\"></a></p><p><span style=\"color: rgb(51, 51, 51);\">若依文档地址：&nbsp;</span><a href=\"http://doc.ruoyi.vip\" target=\"_blank\">http://doc.ruoyi.vip</a><br></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【不分离版】：&nbsp;</span><a href=\"http://demo.ruoyi.vip\" target=\"_blank\">http://demo.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【分离版本】：&nbsp;</span><a href=\"http://vue.ruoyi.vip\" target=\"_blank\">http://vue.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【微服务版】：&nbsp;</span><a href=\"http://cloud.ruoyi.vip\" target=\"_blank\">http://cloud.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【移动端版】：&nbsp;</span><a href=\"http://h5.ruoyi.vip\" target=\"_blank\">http://h5.ruoyi.vip</a></p><p><br style=\"color: rgb(48, 49, 51); font-family: &quot;Helvetica Neue&quot;, Helvetica, Arial, sans-serif; font-size: 12px;\"></p>', '0', 'admin', sysdate(), '', null, '管理员');


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
  phone           VARCHAR(20)   NOT NULL COMMENT '手机号',
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

CREATE TABLE IF NOT EXISTS user_real_name_auth (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  real_name_mask VARCHAR(64) NULL,
  id_number_mask VARCHAR(64) NULL,
  material_ref VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  auditor_id BIGINT NULL,
  audited_at DATETIME NULL,
  reject_reason VARCHAR(500) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_realname_user (user_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实名认证申请';

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

CREATE TABLE IF NOT EXISTS user_notification (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64) NULL COMMENT 'A4 创建幂等键；历史行为 NULL',
  type VARCHAR(32) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body VARCHAR(2000) NULL,
  template_code VARCHAR(64) NULL,
  template_params VARCHAR(2000) NULL,
  biz_ref VARCHAR(64) NULL,
  business_type VARCHAR(64) NULL COMMENT 'A4 业务类型；历史行 NULL',
  business_id VARCHAR(64) NULL COMMENT 'A4 业务标识；历史行 NULL',
  create_by VARCHAR(64) NOT NULL DEFAULT 'system',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_request_id (request_id),
  KEY idx_user_notification_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知';

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

UPDATE sys_menu SET order_num = 8 WHERE menu_id = 1 AND menu_type = 'M';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(5100, '数据总览',           5000, 1, '/dashboard', 'dashboard/Dashboard',            '', 1, 0, 'C', '0', '0', 'smartscript:dashboard:view',   'DataLine', 'A1', 'A1SEED'),
(2010, '分类管理',           2000, 1, 'category',       'content/category/index',     '', 1, 0, 'C', '0', '0', 'content:category:list',        'Collection', 'b-migration', 'B模块内容管理菜单'),
(2011, '标签管理',           2000, 2, 'tag',            'content/tag/index',          '', 1, 0, 'C', '0', '0', 'content:tag:list',             'Tag',        'b-migration', 'B模块内容管理菜单'),
(2013, '作品管理',           2000, 3, 'work',           'content/work/index',         '', 1, 0, 'C', '0', '0', 'content:work:list',            'Document',   'b-migration', 'B模块内容管理菜单'),
(2014, '书城作品管理',       2000, 4, 'bookstore',      'content/bookstore/index',    '', 1, 0, 'C', '0', '0', 'content:bookstore:list',       'ShoppingCart','b-migration', 'B模块内容管理菜单'),
(2015, '排行榜管理',         2000, 5, 'ranking',        'content/ranking/index',      '', 1, 0, 'C', '0', '0', 'content:ranking:list',         'Chart',      'b-migration', 'B模块内容管理菜单'),
(2016, 'Banner管理',         2000, 6, 'banner',         'content/banner/index',       '', 1, 0, 'C', '0', '0', 'content:banner:list',          'Picture',    'b-migration', 'B模块内容管理菜单'),
(2017, '作品上传资料',       2000, 7, 'workfile',       'content/workfile/index',     '', 1, 0, 'C', '0', '0', 'content:workfile:list',        'Upload',     'b-migration', 'B模块内容管理菜单'),
(2012, '外部视频管理',       2000, 8, 'external-drama', NULL,                         '', 1, 0, 'M', '0', '0', NULL,                           'VideoCamera','b-migration', 'B模块外部视频管理目录'),
(2018, '渠道管理',           2012, 1, 'channel',        'content/external-drama/channel/index', '', 1, 0, 'C', '0', '0', 'content:channel:list',    'Guide',      'b-migration', 'B模块外部视频菜单'),
(2019, '视频内容管理',       2012, 2, 'drama',          'content/external-drama/drama/index',   '', 1, 0, 'C', '0', '0', 'content:drama:list',      'VideoPlay',  'b-migration', 'B模块外部视频菜单'),
(2027, '关联剧本',           2012, 3, 'bind',           'content/external-drama/bind/index',    '', 1, 0, 'C', '0', '0', 'content:dramabind:list',  'Link',       'b-migration', 'B模块外部视频菜单'),
(2028, '上下架管理',         2012, 4, 'status',         'content/external-drama/status/index',  '', 1, 0, 'C', '0', '0', 'content:dramastatus:list','Switch',     'b-migration', 'B模块外部视频菜单'),
(2029, '播放数据',           2012, 5, 'stats',          'content/external-drama/stats/index',   '', 1, 0, 'C', '0', '0', 'content:dramastats:list', 'DataLine',   'b-migration', 'B模块外部视频菜单'),
(5110, '运营数据总览',       5002, 1, 'overview', 'statistics/OperationOverview', '', 1, 0, 'C', '0', '0', 'smartscript:stats:overview', 'DataLine', 'A1', 'A1SEED'),
(5111, '明细数据查询',       5002, 2, 'detail',   'statistics/DetailQuery',       '', 1, 0, 'C', '0', '0', 'smartscript:stats:detail',   'Search',   'A1', 'A1SEED'),
(5125, 'AI初审+人工复核',    5003, 1, 'ai-review', NULL,                          '', 1, 0, 'M', '0', '0', NULL,                             'Stamp', 'A1', 'A1SEED'),
(5120, '作品审核工作台',     5125, 1, 'review',       'copyright/ReviewWorkbench',  '', 1, 0, 'C', '0', '0', 'smartscript:copyright:review',      'Stamp', 'A1', 'A1SEED'),
(5121, 'AI审核规则配置',     5125, 2, 'review-rules', 'copyright/AiReviewRules',    '', 1, 0, 'C', '0', '0', 'smartscript:copyright:reviewRules', 'Stamp', 'A1', 'A1SEED'),
(5122, '版权中心对接',       5003, 4, 'center', 'copyright/CopyrightCenter',          '', 1, 0, 'C', '0', '0', 'smartscript:copyright:center',  'Link',   'A1', 'A1SEED'),
(5123, '版权资产库管理',     5003, 5, 'assets', 'copyright/CopyrightAssets',          '', 1, 0, 'C', '0', '0', 'smartscript:copyright:assets',  'Folder', 'A1', 'A1SEED'),
(5124, '印章审核',           5003, 6, 'seals',  'common/ModuleScaffold',              '', 1, 0, 'C', '0', '0', 'smartscript:copyright:seals',   'Stamp',  'A1', 'A1SEED'),
(5130, '交易作品管理',       5004, 1, 'works',      'trade/TradeWorks',   '', 1, 0, 'C', '0', '0', 'trade:works:list',     'Sell',   'A1', 'A1SEED'),
(5133, '询盘管理',           5004, 2, 'inquiry',    'trade/Inquiry',      '', 1, 0, 'C', '0', '0', 'trade:inquiry:list',   'Chat',   'A1', 'A1SEED'),
(5135, '报价管理',           5004, 3, 'quote',      'trade/Quote',        '', 1, 0, 'C', '0', '0', 'trade:quote:list',     'Money',  'c-migration', 'C模块交易菜单补全'),
(5131, '授权订单管理',       5004, 4, 'orders',     'trade/AuthOrders',   '', 1, 0, 'C', '0', '0', 'trade:orders:list',    'Sell',   'A1', 'A1SEED'),
(5132, '合作方管理',         5004, 5, 'partners',   'trade/Partners',     '', 1, 0, 'C', '0', '0', 'trade:partners:list',  'User',   'A1', 'A1SEED'),
(5136, '需求标签',           5004, 6, 'demand-tags','trade/DemandTags',   '', 1, 0, 'C', '0', '0', 'trade:tags:list',      'Tag',    'c-migration', 'C模块交易菜单补全'),
(5137, '商务跟进',           5004, 7, 'follow-up',  'trade/FollowUp',     '', 1, 0, 'C', '0', '0', 'trade:followups:list', 'Phone',  'c-migration', 'C模块交易菜单补全'),
(5138, '征集项目',           5004, 8, 'demand',     'trade/Demand',       '', 1, 0, 'C', '0', '0', 'trade:demand:list',    'List',   'c-migration', 'C模块交易菜单补全'),
(5134, '合同与结算',         5004, 9, 'contracts',  'common/ModuleScaffold', '', 1, 0, 'C', '0', '0', 'smartscript:trade:contracts', 'Document', 'A1', 'A1SEED'),
(5140, '广告运营配置',         5005, 1, 'ad-config',      'operation/AdConfig',      '', 1, 0, 'C', '0', '0', 'smartscript:ops:adConfig',   'Picture',   'A1', 'A1SEED'),
(5141, '用户画像与推荐配置',   5005, 2, 'user-profile-rec','operation/UserProfileRec','', 1, 0, 'C', '0', '0', 'smartscript:ops:userProfile','Operation', 'A1', 'A1SEED'),
(5142, '用户与创作者管理',     5005, 3, '/user',          'user/UserManage',         '', 1, 0, 'C', '0', '0', 'smartscript:ops:creatorUser','User',      'A1', 'A1SEED-product-not-system-user'),
(5143, '全局风控管理',         5005, 4, '/risk',          'risk/RiskManage',         '', 1, 0, 'C', '0', '0', 'smartscript:ops:risk',       'Warning',   'A1', 'A1SEED'),
(5150, 'AI 创作与次数',       5006, 1, '/ai/operations',    'common/ModuleScaffold', '', 1, 0, 'C', '0', '0', 'smartscript:ai:operations',   'Magic',  'A1', 'A1SEED'),
(5151, '福利与积分配置',      5006, 2, '/support/welfare',  'common/ModuleScaffold', '', 1, 0, 'C', '0', '0', 'smartscript:support:welfare', 'Present','A1', 'A1SEED'),
(5152, '消息与公告',          5006, 3, '/support/messages', 'common/ModuleScaffold', '', 1, 0, 'C', '0', '0', 'smartscript:support:messages','Bell',   'A1', 'A1SEED')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
  path=VALUES(path), component=VALUES(component), menu_type=VALUES(menu_type),
  perms=VALUES(perms), icon=VALUES(icon), remark=VALUES(remark);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(3000, '用户中心',           0,    9, 'appuser',  NULL,                  '', NULL,        1, 0, 'M', '0', '0', NULL,                'peoples', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3001, 'App用户与创作者',    3000, 1, 'users',    'user/UserManage',     '', 'A4AppUser',  1, 0, 'C', '0', '0', 'user:app:list',     'user',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3005, '实名审核',           3000, 2, 'realname', 'user/realname/index', '', 'A4RealName', 1, 0, 'C', '0', '0', 'user:realname:list','form',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3008, '作者能力',           3000, 3, 'creator',  'user/UserManage',     '', 'A4Creator',  1, 0, 'C', '0', '0', 'user:creator:list', 'star',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3010, '用户消息',           3000, 4, 'message',  'user/message/index',  '', 'A4Message',  1, 0, 'C', '0', '0', 'user:message:list', 'message', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3013, '用户反馈',           3000, 5, 'feedback', 'user/feedback/index', '', 'A4Feedback', 1, 0, 'C', '0', '0', 'user:feedback:list','edit',    'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3002, 'A4-App用户查询',     3001, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:app:query',      '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3003, 'A4-App用户状态',     3001, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:app:status',     '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3004, 'A4-用户角色授权',    3001, 3, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:app:grant',      '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3006, 'A4-实名申请查询',    3005, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:realname:query', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3007, 'A4-实名审核决定',    3005, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:realname:audit', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3009, 'A4-作者能力变更',    3008, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:creator:update', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3011, 'A4-消息创建',        3010, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:message:add',    '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3012, 'A4-消息详情',        3010, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:message:query',  '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3014, 'A4-反馈详情',        3013, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:feedback:query', '#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）'),
(3015, 'A4-反馈处理',        3013, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:feedback:handle','#', 'a4_migration', 'A4 PC 管理能力权限（A4_20260922_002）')
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
(2035, 'Banner查询', 2016, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:banner:query',    '#', 'b-migration', 'B模块内容管理按钮权限'),
(2036, 'Banner新增', 2016, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:banner:add',      '#', 'b-migration', 'B模块内容管理按钮权限'),
(2037, 'Banner编辑', 2016, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:banner:edit',     '#', 'b-migration', 'B模块内容管理按钮权限'),
(2039, '上传资料查询', 2017, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:workfile:query', '#', 'b-migration', 'B模块内容管理按钮权限'),
(2040, '渠道查询',   2018, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:channel:query',     '#', 'b-migration', 'B模块外部视频按钮权限'),
(2041, '渠道新增',   2018, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:channel:add',       '#', 'b-migration', 'B模块外部视频按钮权限'),
(2042, '渠道编辑',   2018, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:channel:edit',      '#', 'b-migration', 'B模块外部视频按钮权限'),
(2043, '视频查询',   2019, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:drama:query',       '#', 'b-migration', 'B模块外部视频按钮权限'),
(2044, '视频新增',   2019, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:drama:add',         '#', 'b-migration', 'B模块外部视频按钮权限'),
(2045, '视频编辑',   2019, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:drama:edit',        '#', 'b-migration', 'B模块外部视频按钮权限'),
(2046, '关联查询',   2027, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramabind:query',   '#', 'b-migration', 'B模块外部视频按钮权限'),
(2047, '关联编辑',   2027, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramabind:edit',    '#', 'b-migration', 'B模块外部视频按钮权限'),
(2048, '上下架查询', 2028, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastatus:query', '#', 'b-migration', 'B模块外部视频按钮权限'),
(2049, '上下架编辑', 2028, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastatus:edit',  '#', 'b-migration', 'B模块外部视频按钮权限'),
(2050, '播放数据查询', 2029, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastats:query', '#', 'b-migration', 'B模块外部视频按钮权限'),
(5180, '交易作品新增',   5130, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:works:add',       '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5181, '交易作品编辑',   5130, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:works:edit',      '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5182, '授权订单查询',   5131, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:orders:query',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5183, '合作方新增',     5132, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:partners:add',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5184, '合作方编辑',     5132, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:partners:edit',   '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5185, '询盘查询',       5133, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:query',   '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5186, '询盘跟进',       5133, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:edit',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5187, '询盘转订单',     5133, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:convert', '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5188, '报价接受/拒绝',  5135, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:quote:edit',      '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5189, '需求标签新增',   5136, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:tags:add',        '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5190, '需求标签编辑',   5136, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:tags:edit',       '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5191, '需求标签删除',   5136, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:tags:remove',     '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5192, '商务跟进新增',   5137, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:followups:add',   '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5193, '征集投稿查询',   5138, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:demand:query',    '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5194, '征集令发布',     5138, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:demand:add',      '#', 'c-perm-migration', 'C 模块交易按钮权限'),
(5195, '询盘发起',       5133, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:inquiry:add',     '#', 'c-perm-ops-migration', 'C 模块交易操作按钮权限'),
(5196, '报价提交/议价',  5135, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'trade:quote:add',       '#', 'c-perm-ops-migration', 'C 模块交易操作按钮权限')
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
  UNION ALL SELECT 3004 UNION ALL SELECT 3005 UNION ALL SELECT 3006 UNION ALL SELECT 3007
  UNION ALL SELECT 3008 UNION ALL SELECT 3009 UNION ALL SELECT 3010 UNION ALL SELECT 3011
  UNION ALL SELECT 3012 UNION ALL SELECT 3013 UNION ALL SELECT 3014 UNION ALL SELECT 3015
  UNION ALL SELECT 2000 UNION ALL SELECT 2010 UNION ALL SELECT 2011 UNION ALL SELECT 2012
  UNION ALL SELECT 2013 UNION ALL SELECT 2014 UNION ALL SELECT 2015 UNION ALL SELECT 2016 UNION ALL SELECT 2017
  UNION ALL SELECT 2018 UNION ALL SELECT 2019
  UNION ALL SELECT 2020 UNION ALL SELECT 2021 UNION ALL SELECT 2022
  UNION ALL SELECT 2023 UNION ALL SELECT 2024 UNION ALL SELECT 2025 UNION ALL SELECT 2026
  UNION ALL SELECT 2027 UNION ALL SELECT 2028 UNION ALL SELECT 2029
  UNION ALL SELECT 2030 UNION ALL SELECT 2031 UNION ALL SELECT 2032 UNION ALL SELECT 2033 UNION ALL SELECT 2034
  UNION ALL SELECT 2035 UNION ALL SELECT 2036 UNION ALL SELECT 2037 UNION ALL SELECT 2039
  UNION ALL SELECT 2040 UNION ALL SELECT 2041 UNION ALL SELECT 2042 UNION ALL SELECT 2043 UNION ALL SELECT 2044
  UNION ALL SELECT 2045 UNION ALL SELECT 2046 UNION ALL SELECT 2047 UNION ALL SELECT 2048 UNION ALL SELECT 2049
  UNION ALL SELECT 2050
  UNION ALL SELECT 5130 UNION ALL SELECT 5131 UNION ALL SELECT 5132 UNION ALL SELECT 5133
  UNION ALL SELECT 5135 UNION ALL SELECT 5136 UNION ALL SELECT 5137 UNION ALL SELECT 5138
  UNION ALL SELECT 5180 UNION ALL SELECT 5181 UNION ALL SELECT 5182 UNION ALL SELECT 5183
  UNION ALL SELECT 5184 UNION ALL SELECT 5185 UNION ALL SELECT 5186 UNION ALL SELECT 5187
  UNION ALL SELECT 5188 UNION ALL SELECT 5189 UNION ALL SELECT 5190 UNION ALL SELECT 5191
  UNION ALL SELECT 5192 UNION ALL SELECT 5193 UNION ALL SELECT 5194 UNION ALL SELECT 5195
  UNION ALL SELECT 5196
) m
WHERE r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- =====================================================================
-- 第 6 部分：C 交易域业务表（15 张，幂等建表 + 存量列补齐）
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

CREATE TABLE IF NOT EXISTS sys_chat_session (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `session_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `inquiry_id` bigint DEFAULT NULL,
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
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_user1_user2` (`user1_id`,`user2_id`) USING BTREE,
  KEY `idx_last_message_time` (`last_message_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

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

CREATE TABLE IF NOT EXISTS sys_demand (
  `demand_id` bigint NOT NULL AUTO_INCREMENT,
  `demand_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `client_id` bigint NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `genre_id` int NOT NULL,
  `budget` decimal(12,2) NOT NULL,
  `deadline` date NOT NULL,
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
  PRIMARY KEY (`demand_id`),
  UNIQUE KEY `uk_demand_no` (`demand_no`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='征集项目';

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

CREATE TABLE IF NOT EXISTS sys_inquiry (
  `inquiry_id` bigint NOT NULL AUTO_INCREMENT,
  `inquiry_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
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
  PRIMARY KEY (`inquiry_id`),
  UNIQUE KEY `uk_inquiry_no` (`inquiry_no`) USING BTREE,
  UNIQUE KEY `uk_inquiry_id` (`inquiry_id`) USING BTREE,
  KEY `idx_buyer_id` (`buyer_id`) USING BTREE,
  KEY `idx_seller_id` (`seller_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询盘';

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
  `license_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
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

CREATE TABLE IF NOT EXISTS sys_quote (
  `quote_id` bigint NOT NULL AUTO_INCREMENT,
  `quote_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `inquiry_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `quoter_id` bigint NOT NULL,
  `quoter_role` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
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
  PRIMARY KEY (`quote_id`),
  UNIQUE KEY `uk_quote_no` (`quote_no`) USING BTREE,
  KEY `idx_inquiry_id` (`inquiry_id`) USING BTREE,
  KEY `idx_inquiry_role` (`inquiry_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价';

CREATE TABLE IF NOT EXISTS sys_selection (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `selection_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `demand_id` bigint DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_work_type` (`client_id`,`work_id`,`selection_type`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选品';

CREATE TABLE IF NOT EXISTS sys_work (
  `work_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `cover` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `author_id` bigint NOT NULL,
  `genre_id` int NOT NULL,
  `work_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `upload_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `length_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `summary` text COLLATE utf8mb4_general_ci,
  `core_setting` text COLLATE utf8mb4_general_ci,
  `character_setting` text COLLATE utf8mb4_general_ci,
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
  `is_top` tinyint NOT NULL DEFAULT 0 COMMENT '交易设置：是否置顶 0否 1是',
  `is_recommend` tinyint NOT NULL DEFAULT 0 COMMENT '交易设置：是否推荐 0否 1是',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '交易设置：手动排序权重（升序）',
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
  PRIMARY KEY (`work_id`),
  KEY `idx_author_id` (`author_id`) USING BTREE,
  KEY `idx_genre_id` (`genre_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_work_type` (`work_type`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_view_count` (`view_count`) USING BTREE,
  KEY `idx_price` (`price`) USING BTREE,
  KEY `idx_trade_enabled` (`trade_enabled`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易作品';

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

CREATE TABLE IF NOT EXISTS sys_tag (
  `tag_id` int NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(30) NOT NULL,
  `tag_type` varchar(20) NOT NULL,
  `use_count` int NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`) USING BTREE,
  KEY `idx_tag_type` (`tag_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签表';

CREATE TABLE IF NOT EXISTS sys_episode (
  `episode_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `episode_no` int NOT NULL,
  `title` varchar(100) NOT NULL,
  `video_url` varchar(500) NOT NULL,
  `cover_url` varchar(255) NOT NULL,
  `duration` int NOT NULL,
  `is_free` tinyint NOT NULL,
  `unlock_type` varchar(20) DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `play_count` int NOT NULL,
  `like_count` int NOT NULL,
  `comment_count` int NOT NULL,
  `completion_rate` decimal(5,2) DEFAULT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`episode_id`),
  UNIQUE KEY `uk_work_episode_no` (`work_id`,`episode_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='短剧剧集表（一个作品 work_id 下多集短剧）';

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
  UNION ALL SELECT 4,'可议价','negotiable','trade_license_type','default'
  UNION ALL SELECT 1,'已上架','listed','trade_work_listing_status','success'
  UNION ALL SELECT 2,'已下架','offline','trade_work_listing_status','info'
  UNION ALL SELECT 1,'进行中','ongoing','trade_follow_status','success'
  UNION ALL SELECT 2,'待跟进','pending','trade_follow_status','warning'
  UNION ALL SELECT 3,'已完成','completed','trade_follow_status','info'
  UNION ALL SELECT 1,'电话','phone','trade_follow_method','default'
  UNION ALL SELECT 2,'邮件','email','trade_follow_method','default'
  UNION ALL SELECT 3,'面谈','meeting','trade_follow_method','default'
  UNION ALL SELECT 1,'征集中','open','trade_demand_status','success'
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
-- 收口自检
-- =====================================================================
SELECT 'SMARTSCRIPT_INIT_DONE' AS step,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
           ('app_sms_code','app_refresh_session','app_user_consent','app_user_oauth',
            'user_real_name_auth','user_phone_change_log','user_author_capability',
            'user_notification','user_notification_receiver','user_notification_preference',
            'user_feedback')) AS app_tables,
       (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
           ('sys_business_follow','sys_chat_message','sys_chat_session','sys_contact_profile',
            'sys_demand','sys_demand_submission','sys_inquiry','sys_offline_cooperation',
            'sys_order','sys_order_status_log','sys_partner','sys_quote','sys_selection',
            'sys_work','sys_demand_tag')) AS trade_tables,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015) AS a4_menus,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 5000 AND 5199) AS product_menus,
       (SELECT COUNT(*) FROM sys_role WHERE role_key = 'a1_operator') AS a1_role,
       (SELECT COUNT(DISTINCT dict_type) FROM sys_dict_data WHERE dict_type LIKE 'trade%') AS trade_dict_types,
       (SELECT COUNT(*) FROM sys_job WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries') AS inquiry_job,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'bio') AS bio_col;
