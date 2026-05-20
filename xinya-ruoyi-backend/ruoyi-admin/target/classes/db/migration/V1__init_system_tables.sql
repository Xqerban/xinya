-- V1: 初始化若依系统表（按需引入，此处保留最小集合用于RBAC扩展）
-- 注意：本项目采用自定义 JWT + users 表，sys_user 为可选扩展
-- 若未来需要接入若依完整 RBAC，可在此文件追加 sys_role / sys_menu / sys_user 等标准若依建表语句

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `oper_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title`         VARCHAR(50)  DEFAULT '' COMMENT '模块标题',
  `business_type` INT          DEFAULT 0 COMMENT '业务类型',
  `method`        VARCHAR(200) DEFAULT '' COMMENT '方法名称',
  `request_method`VARCHAR(10)  DEFAULT '' COMMENT '请求方式',
  `operator_type` INT          DEFAULT 0 COMMENT '操作类别',
  `oper_name`     VARCHAR(50)  DEFAULT '' COMMENT '操作人员',
  `dept_name`     VARCHAR(50)  DEFAULT '' COMMENT '部门名称',
  `oper_url`      VARCHAR(255) DEFAULT '' COMMENT '请求URL',
  `oper_ip`       VARCHAR(128) DEFAULT '' COMMENT '主机地址',
  `oper_location` VARCHAR(255) DEFAULT '' COMMENT '操作地点',
  `oper_param`    VARCHAR(2000)DEFAULT '' COMMENT '请求参数',
  `json_result`   VARCHAR(2000)DEFAULT '' COMMENT '返回参数',
  `status`        INT          DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
  `error_msg`     VARCHAR(2000)DEFAULT '' COMMENT '错误消息',
  `oper_time`     DATETIME     COMMENT '操作时间',
  `cost_time`     BIGINT       DEFAULT 0 COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 COMMENT='操作日志记录';
