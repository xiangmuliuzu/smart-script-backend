-- 用户上传合同原件后没有 HTML 正文（签署时直接在源 PDF 上落章），接口会写 contract_content = NULL，
-- 但建表时该列被定成 NOT NULL，导致「上传合同原件」端点自上线起一直 500。
-- 已在目标库执行一次；与 sql/sandbox_payment_support.sql 中 sys_contract 的 DDL 同步。
ALTER TABLE sys_contract MODIFY `contract_content` longtext COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同正文HTML；用户上传原件时为空';
