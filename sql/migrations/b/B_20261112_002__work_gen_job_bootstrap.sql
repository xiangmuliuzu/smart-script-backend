-- 后台生成队列：补充「一键生成」的创作输入，用于台账基线自动起草
-- genre/user_prompt/model 与前端 AI 面板的题材、你的要求、模型一致，
-- 任务启动时先用它们起草 props/styleGuide/characters 三项台账基线，再逐集生成。
ALTER TABLE sys_work_gen_job
    ADD COLUMN genre varchar(120) NULL COMMENT '题材（台账基线起草输入）' AFTER roster,
    ADD COLUMN user_prompt text NULL COMMENT '用户要求（台账基线起草输入）' AFTER genre,
    ADD COLUMN model varchar(120) NULL COMMENT '本任务使用的模型名' AFTER user_prompt;
