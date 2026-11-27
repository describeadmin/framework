--liquibase formatted sql logicalFilePath:describeadmin/0003-message-soft-delete.sql

--changeset describeadmin:sys_message_deleted
ALTER TABLE sys_message ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删';
