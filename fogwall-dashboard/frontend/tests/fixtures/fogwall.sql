-- H2 2.4.240; 
;              
CREATE USER IF NOT EXISTS "" SALT '' HASH '' ADMIN;            
CREATE CACHED TABLE "PUBLIC"."SCHEMA_MIGRATIONS"(
    "VERSION" CHARACTER VARYING(20) NOT NULL,
    "DESCRIPTION" CHARACTER VARYING(255) NOT NULL,
    "APPLIED_AT" TIMESTAMP NOT NULL
);      
ALTER TABLE "PUBLIC"."SCHEMA_MIGRATIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_3" PRIMARY KEY("VERSION");        
-- 16 +/- SELECT COUNT(*) FROM PUBLIC.SCHEMA_MIGRATIONS;       
INSERT INTO "PUBLIC"."SCHEMA_MIGRATIONS" VALUES
('1', 'initial schema', TIMESTAMP '2026-09-08 13:13:06.328928'),
('2', 'provider id format', TIMESTAMP '2026-09-08 13:13:06.332404'),
('3', 'email unique constraint', TIMESTAMP '2026-09-08 13:13:06.333405'),
('4', 'spring session tables', TIMESTAMP '2026-09-08 13:13:06.341758'),
('5', 'unified rule shape', TIMESTAMP '2026-09-08 13:13:06.377393'),
('6', 'repo permissions FK', TIMESTAMP '2026-09-08 13:13:06.379155'),
('7', 'rename operations to operation', TIMESTAMP '2026-09-08 13:13:06.380067'),
('8', 'user ssh keys', TIMESTAMP '2026-09-08 13:13:06.382032'),
('9', 'permission groups', TIMESTAMP '2026-09-08 13:13:06.384471'),
('10', 'scm oauth tokens', TIMESTAMP '2026-09-08 13:13:06.385481'),
('11', 'ssh key locked flag and auth source', TIMESTAMP '2026-09-08 13:13:06.389639'),
('12', 'ssh key sources', TIMESTAMP '2026-09-08 13:13:06.391463'),
('13', 'email sources', TIMESTAMP '2026-09-08 13:13:06.393028'),
('14', 'push commit co-authored-by trailers', TIMESTAMP '2026-09-08 13:13:06.395147'),
('15', 'scm api proxy', TIMESTAMP '2026-09-08 13:13:06.397657'),
('16', 'scm token cache scm login', TIMESTAMP '2026-09-08 13:13:06.399549');    
CREATE CACHED TABLE "PUBLIC"."PUSH_RECORDS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "TIMESTAMP" TIMESTAMP NOT NULL,
    "URL" CHARACTER VARYING(1024),
    "UPSTREAM_URL" CHARACTER VARYING(1024),
    "PROVIDER" CHARACTER VARYING(100),
    "PROJECT" CHARACTER VARYING(255),
    "REPO_NAME" CHARACTER VARYING(255),
    "BRANCH" CHARACTER VARYING(512),
    "COMMIT_FROM" CHARACTER VARYING(40),
    "COMMIT_TO" CHARACTER VARYING(40),
    "MESSAGE" CHARACTER VARYING,
    "AUTHOR" CHARACTER VARYING(255),
    "AUTHOR_EMAIL" CHARACTER VARYING(255),
    "COMMITTER" CHARACTER VARYING(255),
    "COMMITTER_EMAIL" CHARACTER VARYING(255),
    "PUSH_USER" CHARACTER VARYING(255),
    "RESOLVED_USER" CHARACTER VARYING(255),
    "USER_EMAIL" CHARACTER VARYING(255),
    "METHOD" CHARACTER VARYING(10),
    "STATUS" CHARACTER VARYING(20) DEFAULT 'RECEIVED' NOT NULL,
    "ERROR_MESSAGE" CHARACTER VARYING,
    "BLOCKED_MESSAGE" CHARACTER VARYING,
    "AUTO_APPROVED" BOOLEAN DEFAULT FALSE NOT NULL,
    "AUTO_REJECTED" BOOLEAN DEFAULT FALSE NOT NULL,
    "SCM_USERNAME" CHARACTER VARYING(255),
    "FORWARDED_AT" TIMESTAMP
);  
ALTER TABLE "PUBLIC"."PUSH_RECORDS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B" PRIMARY KEY("ID");  
-- 23 +/- SELECT COUNT(*) FROM PUBLIC.PUSH_RECORDS;            
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('42a89c6b-009b-488d-a6df-e13166043608', TIMESTAMP '2026-09-08 13:13:42.22759', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/author-noreply-620778', '0000000000000000000000000000000000000000', '32dac5f8468842f9a9ce0b3de761b44177784e0e', NULL, 'Fixture Developer', 'noreply@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('3f50e643-3734-4189-98bc-e8cc27fe5ba1', TIMESTAMP '2026-09-08 13:13:45.406609', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/author-domain-624192', '0000000000000000000000000000000000000000', '2d890c0a726f4b8deb90c4c6e0f69acad4f93bff', NULL, 'Fixture Developer', 'developer@internal.corp.net', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('03d80189-5127-49a9-b4f7-0e591511ed8a', TIMESTAMP '2026-09-08 13:13:46.942978', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/message-wip-625794', '0000000000000000000000000000000000000000', '27cb677ac6493c9e47f318cb59018496d67c5ac4', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('acb0963c-42fb-455f-870f-64cfc8c7f0b2', TIMESTAMP '2026-09-08 13:13:48.37401', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/message-pattern-627336', '0000000000000000000000000000000000000000', '25ffe92c984225cdbc51009feeb7259196dc57b3', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('a31078aa-e342-4a3b-bba0-f877ff5bd61a', TIMESTAMP '2026-09-08 13:13:49.958312', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/secret-aws-628781', '0000000000000000000000000000000000000000', '8801e9cba03fd4e2b635290ecea85d7156bcda6e', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('71bcc23b-4525-4456-92bc-7e8733b5bb2f', TIMESTAMP '2026-09-08 13:13:52.269429', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/diff-literal-631162', '0000000000000000000000000000000000000000', '16d53f0b0f328954f0f513acb0c809e97daa5d87', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('ad381ddb-55a4-4853-bee4-5fb273a343aa', TIMESTAMP '2026-09-08 13:13:54.070855', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/diff-pattern-632701', '0000000000000000000000000000000000000000', '49d17b15216699672ade6f11c87377f1cf9a4f0e', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('0489fd19-40a0-4aed-96ae-394780b1b51d', TIMESTAMP '2026-09-08 13:13:55.444112', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/no-signoff-634463', '0000000000000000000000000000000000000000', 'be9750a4ff5798fb660755d1759da892a6b50845', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL);          
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('b6476888-7a69-4906-8079-5c0a88a9b34c', TIMESTAMP '2026-09-08 13:13:56.794449', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/signoff-mismatch-635845', '0000000000000000000000000000000000000000', '54be71ff722464a757f0a34b76dd315701e9d07c', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('0d985749-7e23-41a2-b992-be254742fae0', TIMESTAMP '2026-09-08 13:13:58.721862', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/coauthor-denied-637720', '0000000000000000000000000000000000000000', '77ffb76ca65fd610262c71508229c9b6643dfef3', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('59f76466-cee9-48d4-bac5-9dc955575a79', TIMESTAMP '2026-09-08 13:14:00.041752', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/coauthor-ok-639117', '0000000000000000000000000000000000000000', '4bd51d66b5f8cefd3831fff9030069a6aeaac972', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('ed117cf9-0789-4341-9561-cf551eff7d42', TIMESTAMP '2026-09-08 13:14:02.304695', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/multi-fail-640429', '0000000000000000000000000000000000000000', '807e55ad96e8fe485489f4726ffdc49ae75d637c', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '8 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('035b2f93-5eb4-46b2-a916-213f5943e2f6', TIMESTAMP '2026-09-08 13:14:04.436536', '/fixture-dev/fogwall-fixture', 'https://codeberg.org/fixture-dev/fogwall-fixture', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/unmapped-642728', '0000000000000000000000000000000000000000', 'aca8eb091d41eb1a10fe22e537e2d5995b6f232e', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, NULL, NULL, NULL, 'PUSH', 'REJECTED', NULL, 'User not authorized', FALSE, FALSE, NULL, NULL),
('0f36835e-186b-4729-955d-5deac7a5b658', TIMESTAMP '2026-09-08 13:14:07.4211', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/pending-branch-646501', '0000000000000000000000000000000000000000', '051becec3708b748bd374994b66c0432b4e0da6f', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('358339ec-552e-484d-854a-602be97a7eea', TIMESTAMP '2026-09-08 13:14:09.19377', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/tags/v9.9.9-fixture-1788887648', '0000000000000000000000000000000000000000', 'f768c2f9aa0d9ad0b94214290338867d06f0bcb7', NULL, NULL, NULL, NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('14396ba7-ccde-4191-a213-c7b0c891b680', TIMESTAMP '2026-09-08 13:14:10.633872', '/fixture-dev/fogwall-fixture', 'https://gitea.com/fixture-dev/fogwall-fixture', 'gitea', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/gitea-649576', '0000000000000000000000000000000000000000', 'b0a09aaef017063e4ac29cbde621c8a7f4417edd', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL); 
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('13c11e61-6ce9-4f71-8c95-6b34a08f3f97', TIMESTAMP '2026-09-08 13:14:14.993388', '/fixture-dev/fogwall-fixture', 'https://gitlab.com/fixture-dev/fogwall-fixture', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/gitlab-warn-651991', '0000000000000000000000000000000000000000', '9903d7974c466c216109dd743cbe00381742c491', NULL, 'Fixture Developer', 'unregistered@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('2e35354b-3201-4b86-afff-50e7eca07dbb', TIMESTAMP '2026-09-08 13:14:18.3801', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/multi-commit-657207', '0000000000000000000000000000000000000000', 'f0fdc1f79e410b52bf4304d231b13df2df500272', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-08 13:15:58.614895'),
('e4c62ba8-143c-4f5a-8eef-584f34a71eb1', TIMESTAMP '2026-09-08 13:14:20.932116', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/tags/lightweight-fixture-1788887660', '0000000000000000000000000000000000000000', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', NULL, NULL, NULL, NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-08 13:16:01.277154'),
('6c0a3ec7-a4d8-48ee-8f54-269f6f644533', TIMESTAMP '2026-09-08 13:14:22.362163', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/reviewer-reject-661317', '0000000000000000000000000000000000000000', '4b67355311ecce88c39b7a719f7fc34d3a485689', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('24c32427-cee8-42ff-84c8-9918b01cc788', TIMESTAMP '2026-09-08 13:14:24.108024', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/canceled-662750', '0000000000000000000000000000000000000000', '7f370e25726e8fc6ac14d09ae8c614e3b258382b', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'CANCELED', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('995e7946-c575-4de9-a0cc-6e3f8c0833ff', TIMESTAMP '2026-09-08 13:14:25.465513', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/self-certify-664499', '0000000000000000000000000000000000000000', '6b879777c725c7d6bd8f3d003acf1665092b273d', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-08 13:16:03.80875'),
('f1c0a843-a5e9-4636-8357-7b5d50fd8190', TIMESTAMP '2026-09-08 13:14:27.856617', '/fixture-dev/fogwall-fixture', 'ssh://git@github.com/fixture-dev/fogwall-fixture.git', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/ssh-server-666324', '0000000000000000000000000000000000000000', '08af8057382e3da737b1a97118b415301ff7485f', 'feat: pushed over the SSH transport', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', NULL, 'dev', NULL, 'SSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-08 13:15:32.320829');           
CREATE INDEX "PUBLIC"."IDX_PUSH_RECORDS_STATUS" ON "PUBLIC"."PUSH_RECORDS"("STATUS" NULLS FIRST);              
CREATE INDEX "PUBLIC"."IDX_PUSH_RECORDS_PROJECT" ON "PUBLIC"."PUSH_RECORDS"("PROJECT" NULLS FIRST);            
CREATE INDEX "PUBLIC"."IDX_PUSH_RECORDS_REPO" ON "PUBLIC"."PUSH_RECORDS"("REPO_NAME" NULLS FIRST);             
CREATE INDEX "PUBLIC"."IDX_PUSH_RECORDS_USER" ON "PUBLIC"."PUSH_RECORDS"("PUSH_USER" NULLS FIRST);             
CREATE INDEX "PUBLIC"."IDX_PUSH_RECORDS_TIMESTAMP" ON "PUBLIC"."PUSH_RECORDS"("TIMESTAMP" NULLS FIRST);        
CREATE INDEX "PUBLIC"."IDX_PUSH_RECORDS_COMMIT_TO" ON "PUBLIC"."PUSH_RECORDS"("COMMIT_TO" NULLS FIRST, "BRANCH" NULLS FIRST, "REPO_NAME" NULLS FIRST);         
CREATE CACHED TABLE "PUBLIC"."PUSH_STEPS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "PUSH_ID" CHARACTER VARYING(36) NOT NULL,
    "STEP_NAME" CHARACTER VARYING(255) NOT NULL,
    "STEP_ORDER" INTEGER NOT NULL,
    "STATUS" CHARACTER VARYING(20) DEFAULT 'PASS' NOT NULL,
    "CONTENT" CHARACTER VARYING,
    "ERROR_MESSAGE" CHARACTER VARYING,
    "BLOCKED_MESSAGE" CHARACTER VARYING,
    "LOGS" CHARACTER VARYING,
    "TIMESTAMP" TIMESTAMP NOT NULL
);         
ALTER TABLE "PUBLIC"."PUSH_STEPS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_C" PRIMARY KEY("ID");    
-- 391 +/- SELECT COUNT(*) FROM PUBLIC.PUSH_STEPS;             
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('07d2418e-cd48-4e75-9136-e791f707a41e', '42a89c6b-009b-488d-a6df-e13166043608', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:42.23376'),
('593af3b7-e0ea-481e-9bd6-af5737c1c8de', '42a89c6b-009b-488d-a6df-e13166043608', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.470358'),
('f71d2f2d-216c-4fc2-a9a2-e71c56ada0e8', '42a89c6b-009b-488d-a6df-e13166043608', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.470693'),
('85f6fa81-e62d-4deb-887c-a0790db3ad7e', '42a89c6b-009b-488d-a6df-e13166043608', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.672532'),
('4fe9c82f-6ec4-4aee-9f46-325fc7b9c92c', '42a89c6b-009b-488d-a6df-e13166043608', 'commitAttributionPolicy', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.676068'),
('576d71f6-59fe-477c-a342-b743b56a02bb', '42a89c6b-009b-488d-a6df-e13166043608', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.676153'),
('7a549eb3-7902-4756-a696-ee187de5da7e', '42a89c6b-009b-488d-a6df-e13166043608', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.677583'),
('c2f264f2-78b9-4fa1-895d-2d26a1681415', '42a89c6b-009b-488d-a6df-e13166043608', 'checkAuthorEmails', 250, 'FAIL', U&'\274c\fe0f  author email (noreply@example.com): blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)', NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.679466'),
('a6205a3e-6f17-46f2-a835-4cacbb0039b4', '42a89c6b-009b-488d-a6df-e13166043608', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.680725'),
('99b23ead-d45d-4a83-b2f0-a8bb5c5cb8e6', '42a89c6b-009b-488d-a6df-e13166043608', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.681244'),
('262a5288-6d09-4978-9363-b830a01ee639', '42a89c6b-009b-488d-a6df-e13166043608', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.706035'),
('e187840c-96e1-46e5-9a06-5863ce06f6c9', '42a89c6b-009b-488d-a6df-e13166043608', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.728039'),
('d202a6ba-82ac-49b2-8e8f-055fd43dd6fe', '42a89c6b-009b-488d-a6df-e13166043608', 'diff', 280, 'PASS', U&'diff --git a/notes/noreply.txt b/notes/noreply.txt\000anew file mode 100644\000aindex 0000000..b2da919\000a--- /dev/null\000a+++ b/notes/noreply.txt\000a@@ -0,0 +1 @@\000a+feat: this commit has a noreply author - 2026-09-08T17:13:41Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.729667'),
('6a9e5a2e-0db3-44a1-a86c-c60170d58a23', '42a89c6b-009b-488d-a6df-e13166043608', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.731131'),
('59e35155-4b09-4964-bc1c-1a97dcc78ac5', '42a89c6b-009b-488d-a6df-e13166043608', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:43.731173'),
('84caf414-0586-45ec-9fcd-aaecd829b4e8', '42a89c6b-009b-488d-a6df-e13166043608', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:44.173262'),
('b867976d-b2e2-4789-848f-a304d9966d99', '42a89c6b-009b-488d-a6df-e13166043608', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:44.178709'),
('651218d2-234a-4193-b26a-42fe232e9f16', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.408362'),
('40252ae0-e816-49c3-a4f2-80f25edbcc92', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.416841');           
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('c352d7bd-188f-4d2e-8720-a6fdaba0f5f3', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.417187'),
('0a94b0a1-95a6-4987-9301-c860d9c696e0', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.419217'),
('9b7a8fa0-46de-45dc-b148-7962c348ef9d', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'commitAttributionPolicy', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.419947'),
('0d8edb9a-48e8-4384-b113-ca4490f825e1', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.419975'),
('03abd3ef-d6c6-4436-a8fb-81761a1fbe18', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.420445'),
('f6dd38c4-15fb-4b7e-9e34-3d6c9e53a687', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkAuthorEmails', 250, 'FAIL', U&'\274c\fe0f  author email (developer@internal.corp.net): not in allowlist\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'not in allowlist', NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.420695'),
('9b25adee-acc7-41d7-b0d9-aca8872bf1b8', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.420732'),
('306cc52b-65ca-44f6-9512-c10d9ffb9453', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.420754'),
('e92421ac-4828-4508-bcaa-752671db4650', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.423697'),
('a3cf2b69-f965-4520-8d23-23708acf6c91', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.425638'),
('f3cc9bae-0680-41cf-8599-f25987ac9dd9', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'diff', 280, 'PASS', U&'diff --git a/notes/domain.txt b/notes/domain.txt\000anew file mode 100644\000aindex 0000000..fa655e0\000a--- /dev/null\000a+++ b/notes/domain.txt\000a@@ -0,0 +1 @@\000a+feat: this commit comes from an unapproved domain - 2026-09-08T17:13:44Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.426681'),
('bae349d3-3e75-4ab8-980a-4787c3e4e643', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.427742'),
('834d912c-b3ee-4142-a328-061ac11bf4c6', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.427769'),
('a2a932ec-d0b2-48dd-b75e-b66c0fdaadc2', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.778669'),
('dddc48f0-7006-48e8-88fb-1fb52ef71597', '3f50e643-3734-4189-98bc-e8cc27fe5ba1', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:45.783896'),
('e0453a5a-5663-4cf8-84f1-778efb44e72d', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.945416'),
('f52d85c4-e461-4e46-8999-37163209901e', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.955965'),
('92b2c1f1-f55f-42c3-a018-86cc6828f711', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.956651'),
('e5eb7c1a-ba70-4e0f-a3c5-11e3ee404666', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.960969'),
('da823f63-5d0e-4ac2-b327-c4bb11ff9e6a', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.96202'); 
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('7cf9f724-1545-43ba-b6d4-926479fbd06d', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.962053'),
('2426dd8b-03ff-421c-90a8-24ca8d0b3f84', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.962849'),
('e84d943e-db47-4554-9538-28b6005f2dc1', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.962915'),
('d070c07f-55e0-4ec0-b80c-de04e3bff287', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.962947'),
('2ffe3c8f-8ae2-4710-8c91-a9673a0ca4a5', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkCommitMessages', 260, 'FAIL', U&'\274c\fe0f  WIP: still working on this feature: contains blocked term: "WIP"\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'contains blocked term: "WIP"', NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.963437'),
('79c1d96f-36d0-4453-a1d1-5d48d35862b3', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.967721'),
('62ad0ada-b33e-42ea-84cf-bd0c6933619d', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.969685'),
('42d47df1-f9f0-4e9b-a051-c3078d18df2f', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'diff', 280, 'PASS', U&'diff --git a/notes/wip.txt b/notes/wip.txt\000anew file mode 100644\000aindex 0000000..ee93add\000a--- /dev/null\000a+++ b/notes/wip.txt\000a@@ -0,0 +1 @@\000a+WIP: still working on this feature - 2026-09-08T17:13:46Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.970627'),
('711910e2-321d-4547-9f38-30e8fb0be140', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.971378'),
('67bd6f21-6dfb-463d-89d7-b833d3936032', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:46.971406'),
('3783380b-eb80-40da-8938-d463da62cca4', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:47.320533'),
('55a0b1a5-e3c3-4a15-ae98-e729e24ce74d', '03d80189-5127-49a9-b4f7-0e591511ed8a', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:47.326083'),
('e589ddd8-723a-4ce2-a342-c84aa9893228', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.376057'),
('3c2a4b71-dda0-471d-acda-8eebbd54ca13', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.386452'),
('3d32c9e2-eb34-4265-a9a1-7b4421d8d01f', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.386904'),
('cc429e31-fab2-40a3-b17c-9678859162d1', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.391628'),
('9543a087-3ac2-4eb1-b816-c3c86a197313', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.39399'),
('10179dda-e97e-421b-863e-fe55eaeb9120', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.394024'),
('6e9ea0db-6472-4c37-b2e3-4cd01662c8bd', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.394857'),
('4bd68acd-8886-451a-8b97-e824080eb917', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.394942'),
('52783078-ac08-4d91-b073-29c1239c42e9', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.39497');          
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('6f3fe4df-fa63-4362-9d3d-f4dea9f879c0', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkCommitMessages', 260, 'FAIL', U&'\274c\fe0f  chore: rotate token=[REDACTED] in CI config: matches blocked pattern: (?i)(password|secret|token)\\s*[=:]\\s*\\S+\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'matches blocked pattern: (?i)(password|secret|token)\s*[=:]\s*\S+', NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.39529'),
('c77b40b9-3d74-461c-90ff-afe83bb01a01', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.398131'),
('51b0e0ad-2123-4617-ab5d-148928413332', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.399919'),
('f21dc0a4-0e60-426c-ab59-db5d3a3475c7', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'diff', 280, 'PASS', U&'diff --git a/notes/rotate.txt b/notes/rotate.txt\000anew file mode 100644\000aindex 0000000..cc995b8\000a--- /dev/null\000a+++ b/notes/rotate.txt\000a@@ -0,0 +1 @@\000a+chore: rotate token=[REDACTED] in CI config - 2026-09-08T17:13:47Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.40073'),
('57c1d383-0a32-4428-80af-3cc596856550', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.401375'),
('5446cfa3-4290-4119-8c47-a48ea6189529', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.4014'),
('27906a16-7fe1-4c37-8f15-b4561117c08e', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [generic-api-key]  notes/rotate.txt:1\000a  commit: 25ffe92\000a  match:  token=[REDACTED] \000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[generic-api-key]  notes/rotate.txt:1\000a  commit: 25ffe92\000a  match:  token=[REDACTED] ', NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.76957'),
('be247e5d-897b-4b8a-b8d5-3bfa4efb41c9', 'acb0963c-42fb-455f-870f-64cfc8c7f0b2', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:48.7729'),
('d033ba17-9aba-4ae2-afc2-9553bb0386bf', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:49.959772'),
('8e608ff4-25fb-4c05-a9d0-9bbd93e6e60e', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.752133'),
('aa278f77-e5a4-48eb-8efb-31ea99a74d90', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.752568'),
('df72eb77-a94b-4155-a5af-9c5ff6fafbb5', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.75519'),
('b0a638c1-0e73-4ba8-944c-28111affbd8d', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.756286'),
('61988011-e291-41e7-b274-611680f3a854', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.756325'),
('b3739557-ccd4-4f9c-abe3-5d5dc863774b', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.757382'),
('dfa05f8e-c70e-4841-b69b-11d04eda4ba8', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.757463'),
('f3684f3b-a307-4f9d-b84e-d0d164443ae7', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.757527'),
('6192443a-c2d1-43c8-b95a-52d866c0ebb7', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.757575'),
('e1f3bec5-bed3-40d2-a548-47b359a4417e', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.76318'); 
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('af810ab5-e5c2-4dcb-87f7-c7735035d021', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.766061'),
('9ec0097a-e53b-44cb-aa6f-380fffda7029', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'diff', 280, 'PASS', U&'diff --git a/aws-credentials b/aws-credentials\000anew file mode 100644\000aindex 0000000..53c17b0\000a--- /dev/null\000a+++ b/aws-credentials\000a@@ -0,0 +1,3 @@\000a+[default]\000a+aws_access_key_id = [REDACTED]\000a+aws_secret_access_key = [REDACTED]\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.767121'),
('957bbc44-0631-45a5-9524-b22a3e4872e7', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.76817'),
('85cf1155-9a13-4a46-8aed-2f824b6b1227', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:50.768194'),
('e1da1280-b375-4824-ba3b-f1be5efc5a22', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [generic-api-key]  aws-credentials:3\000a  commit: 8801e9c\000a  match:  aws_secret_access_key = [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[generic-api-key]  aws-credentials:3\000a  commit: 8801e9c\000a  match:  aws_secret_access_key = [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-08 13:13:51.1469'),
('84d306ae-4cbd-4c95-9060-6d12d3f2d22b', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [aws-access-token]  aws-credentials:2\000a  commit: 8801e9c\000a  match:  [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[aws-access-token]  aws-credentials:2\000a  commit: 8801e9c\000a  match:  [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-08 13:13:51.146997'),
('dff2097c-3611-4c52-89fd-07b4ebffa98f', 'a31078aa-e342-4a3b-bba0-f877ff5bd61a', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:51.152905'),
('93e9a2bd-75fd-4385-8f42-2e65322f1295', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.271503'),
('409bc993-4caa-4aef-b1f5-5721cacb2429', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.286077'),
('b13a9c12-ba03-44d1-a61b-11821810b097', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.286684'),
('de609a5d-a652-4dab-be91-6627a2a38e62', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.292122'),
('de6f384a-896f-46a4-8517-31bbecd41a09', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.293395'),
('4fbf1892-b96d-4f08-b68b-e10454ff5052', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.293435'),
('d846e58c-ce22-4a4d-a13a-0fcc52ee17a8', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.294505'),
('b87dc3d6-e877-4661-bbac-664e1f890d20', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.294661'),
('2274b78e-2120-4ded-86dc-c4de21728fdd', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.294742'),
('9776a892-363c-4e48-9a28-fa245dcfca2b', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.294786'),
('3e371df6-82a9-46f6-9e54-378171a43615', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.297401');       
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('b8d35b5c-e0fd-4d5e-af7d-63c4323a3a26', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.299233'),
('d35d20ba-e091-4750-b5ef-e5fe65c5dc75', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'diff', 280, 'PASS', U&'diff --git a/config.yml b/config.yml\000anew file mode 100644\000aindex 0000000..0d0e7b4\000a--- /dev/null\000a+++ b/config.yml\000a@@ -0,0 +1,3 @@\000a+upstream:\000a+  api: https://internal.corp.example.com/api/v1\000a+  timeout: 30\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.300116'),
('46f5cda8-6e6c-4609-b702-a5898e47b02a', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'scanDiff', 300, 'FAIL', U&'blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.300696'),
('6d847e8f-ac91-4188-b64e-a0d2eef40c9b', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'scanDiff', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.300707'),
('7bf3ac6b-2430-4d16-9165-d4f94910b37c', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.301502'),
('a3d08b86-02a2-43df-b3c3-03320bce18b3', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.690546'),
('02661669-39a6-42ae-ae86-a9a93d810fda', '71bcc23b-4525-4456-92bc-7e8733b5bb2f', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:52.693693'),
('2d70f00a-76bf-4c65-8c45-e10ef5f6e6ac', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.071864'),
('cea0829a-55b1-41de-a0f4-5648235da277', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.077672'),
('fa88d50d-fb50-457e-997e-6b790097e302', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.078076'),
('de8aa295-d8c9-467f-a9f0-ef0847c160a3', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.080776'),
('f699df89-6a22-4433-83ac-c422aca13b48', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.081401'),
('5b4e1c53-cc72-41c4-ba9e-8b5a7fc02efe', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.081425'),
('c85e616d-ca3b-483b-a3ce-6de5ca1b33a0', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.082175'),
('c3299f83-c9f4-4193-bc36-1756a93a0d62', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.082235'),
('150cb103-8ea3-45ed-96b7-1907bfa23a4d', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.082267'),
('54095e96-1607-4462-8b45-7f74bef860c6', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.082297'),
('9bb5ecd2-bf1f-486d-8506-6e88f41b0ab0', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.085595'),
('e1d376bf-9547-4d5d-b606-f69900deac71', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.088675'),
('f6d9a255-0511-40f4-8cb3-2115ff755515', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'diff', 280, 'PASS', U&'diff --git a/deploy.sh b/deploy.sh\000anew file mode 100644\000aindex 0000000..3e0ea35\000a--- /dev/null\000a+++ b/deploy.sh\000a@@ -0,0 +1,2 @@\000a+#!/bin/bash\000a+curl -X POST http://ci.corp.example.com/deploy -d ''{"version": "1.2.3"}''\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.089927');        
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('7a2b2438-d56a-4b81-be26-265a8332b722', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'scanDiff', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in deploy.sh\000a  curl -X POST http://ci.corp.example.com/deploy -d ''{"version": "1.2.3"}''', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in deploy.sh', NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.09016'),
('b3fcff32-d1d6-421b-b747-8614b3f157fe', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.091228'),
('4ade8063-2a94-4878-a3a8-a8ccd449c6ec', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.453768'),
('548763d3-0dbf-4c1c-8c51-78c3e4b751cf', 'ad381ddb-55a4-4853-bee4-5fb273a343aa', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:54.456613'),
('81dcd7a6-a54c-43fa-aa81-de7422586dc6', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.445196'),
('4dd938e8-6053-47fd-ba24-a445447e34b8', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.450037'),
('349f37d7-ba06-4ada-bf3e-3fe3806432c7', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.450258'),
('809dea18-cdf3-4ec4-8265-e9715ede9f0c', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.452276'),
('8901edc8-df03-4d96-9889-1f7f19ac040e', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.4528'),
('b3233d1d-daa2-459b-b18b-19aa7c29745d', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.452822'),
('a883da5a-c4eb-4fde-a238-5a5c2af9d7dc', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.453534'),
('6e2b6bb6-44b6-4d93-8b94-3e8886fd96f4', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.453595'),
('0468d181-b7f6-4064-8d4c-a1740adb8d83', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit be9750a has no Signed-off-by trailer\000a  \2192 This repository requires the Developer Certificate of Origin (DCO) sign-off.\000a  \2192 Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase --signoff <base> for a range).', 'missing Signed-off-by (be9750a)', NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.454158'),
('f11d5358-90d4-4fe4-bba2-3359c14f56da', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.454207'),
('edffc917-6a8a-41e4-b91e-245482a11794', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.457876'),
('1eddc2d9-c296-4315-a437-fda10aba6e47', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.460982'),
('55546be8-4cd4-4bdb-9a95-339f2c22eff2', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'diff', 280, 'PASS', U&'diff --git a/notes/unsigned.txt b/notes/unsigned.txt\000anew file mode 100644\000aindex 0000000..324e5e8\000a--- /dev/null\000a+++ b/notes/unsigned.txt\000a@@ -0,0 +1 @@\000a+feat: forgot to sign off - 2026-09-08T17:13:54Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.46215'),
('e836d67b-25cc-47f5-b57c-76cdc1b636e1', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.463058'),
('d4615fdb-7fba-4891-9ca4-d80d00314d26', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.463087');              
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('a2dcb824-14aa-4968-8b82-f6daa53a9d9b', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.834936'),
('7a7e89c1-baec-4cc7-8b5b-ddbca3bc256d', '0489fd19-40a0-4aed-96ae-394780b1b51d', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:55.837647'),
('91af1c13-a93c-4aa4-a4cf-a03026da397b', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:56.795378'),
('2ce6a700-a41b-432b-b89b-adbc1b3e82bc', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.334332'),
('467ac44d-bb1f-47e8-aa1d-e7d6ddd51385', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.334946'),
('c0f784f1-c399-4544-9e69-da963c73fbab', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.339197'),
('301dc201-4b38-433c-b987-87e5694f386a', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.340579'),
('4897c069-6dab-4f98-a6be-2a6cb6d12f5e', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.340624'),
('99e30380-bf9c-4b9d-ba06-6ca2d1383f53', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.341892'),
('9aef227e-74b5-4166-b699-efd2b4504ac7', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.341979'),
('a8b32b0b-fc25-4be3-b6d5-423f0c282a40', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit 54be71f has no Signed-off-by matching its author <fixture-dev@example.com>\000a  \2192 The DCO requires you to sign off your own work: a Signed-off-by whose email equals the commit author.\000a  \2192 Fix: git config user.email to your author email, then git commit --amend --signoff.', 'Signed-off-by does not match author (54be71f)', NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.342506'),
('fa7bc5b2-970c-4a41-b990-99ca47d7cb94', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.342562'),
('e8e37397-dcf0-48e7-bd0a-23a45b9c06b6', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.344952'),
('930d6f1d-a012-42ea-92d8-0939cf79883c', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.347885'),
('de6f187b-064c-4a87-b84e-6c24e83fd36f', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'diff', 280, 'PASS', U&'diff --git a/notes/mismatch.txt b/notes/mismatch.txt\000anew file mode 100644\000aindex 0000000..87c4af9\000a--- /dev/null\000a+++ b/notes/mismatch.txt\000a@@ -0,0 +1 @@\000a+feat: signed off by the wrong person - 2026-09-08T17:13:56Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.349042'),
('afe428cf-3d41-4239-99a5-7fb826d035a6', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.350006'),
('c5149268-57b9-4ef7-a5f5-1c9b08aea72d', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.350027'),
('9638c98f-b927-48fa-9925-c109f1c0ee63', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.707907'),
('7120110f-109a-4e7b-9c2e-03e36176eec4', 'b6476888-7a69-4906-8079-5c0a88a9b34c', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:57.711293'),
('00d98269-bc02-4d63-8b7b-ef301e66ac09', '0d985749-7e23-41a2-b992-be254742fae0', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.722708');        
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('5abfa420-e3a4-43d8-b8fc-1ebead591b70', '0d985749-7e23-41a2-b992-be254742fae0', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.726445'),
('4c5c131b-d307-42a4-8032-ed21058a9c1a', '0d985749-7e23-41a2-b992-be254742fae0', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.726647'),
('13070fd2-3b6c-496b-9626-edcec88c19ce', '0d985749-7e23-41a2-b992-be254742fae0', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.728363'),
('aac53d52-1937-4cec-ae3d-e09fca357dcf', '0d985749-7e23-41a2-b992-be254742fae0', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.728786'),
('8456f405-c465-4176-b823-119317f968c9', '0d985749-7e23-41a2-b992-be254742fae0', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.728801'),
('660f6430-5e06-405b-89d9-2527e3272853', '0d985749-7e23-41a2-b992-be254742fae0', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.729258'),
('6e686e0d-4aab-4d0b-9e25-c767a633c8d8', '0d985749-7e23-41a2-b992-be254742fae0', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.729294'),
('c1cdd980-bcd4-4515-a131-0a73560a0068', '0d985749-7e23-41a2-b992-be254742fae0', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit 77ffb76 Co-authored-by (Contractor <contractor@outside.example.net>): not in allowlist\000a  \2192 Co-authors must be permitted by policy (allowed domain / not a blocked address).\000a  \2192 Fix: remove the disallowed Co-authored-by line, or use an approved co-author identity.', 'Co-authored-by not allowed (77ffb76)', NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.729603'),
('a7162d00-cf61-44f1-a331-cd347dc98d9a', '0d985749-7e23-41a2-b992-be254742fae0', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.729633'),
('9d94e152-e4e4-4b84-81f8-51e783d37a8f', '0d985749-7e23-41a2-b992-be254742fae0', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.731157'),
('7fb5d8d6-7387-441b-9a20-5cee75229424', '0d985749-7e23-41a2-b992-be254742fae0', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.733432'),
('5ac12e77-44a8-4234-b099-9ab44fc4bd31', '0d985749-7e23-41a2-b992-be254742fae0', 'diff', 280, 'PASS', U&'diff --git a/notes/coauthor.txt b/notes/coauthor.txt\000anew file mode 100644\000aindex 0000000..70ccd68\000a--- /dev/null\000a+++ b/notes/coauthor.txt\000a@@ -0,0 +1 @@\000a+feat: paired with an outside contractor - 2026-09-08T17:13:58Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.734287'),
('13124852-ea38-4db9-a187-18ef393722df', '0d985749-7e23-41a2-b992-be254742fae0', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.735055'),
('17b47e64-bc58-4877-b22b-4321183fb9b5', '0d985749-7e23-41a2-b992-be254742fae0', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:58.735079'),
('f7254796-24a6-4f58-9ad7-48356f472990', '0d985749-7e23-41a2-b992-be254742fae0', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:59.104568'),
('427a9ec8-c006-4a13-a0d5-beea8fecdecb', '0d985749-7e23-41a2-b992-be254742fae0', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:13:59.108996'),
('092526e1-feeb-4892-ad70-df7deb0c2956', '59f76466-cee9-48d4-bac5-9dc955575a79', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.042345'),
('c64caada-45a5-4158-a845-52ef2b9f5b14', '59f76466-cee9-48d4-bac5-9dc955575a79', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.046355'),
('e0f204da-53ab-4912-af52-4e6f8696b158', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.046585'),
('6b02f1b9-b6bb-4461-913d-e98e97597593', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.047953');    
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('2dd849de-5afb-499e-9756-ba039ec3f32c', '59f76466-cee9-48d4-bac5-9dc955575a79', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.048235'),
('57f4be11-3630-426f-970d-110b2990524a', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.048251'),
('c020f896-8c6e-404f-897b-20b2cb6c2f10', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.048539'),
('aa59a85c-8710-4493-9bac-c21f320baa3a', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.048568'),
('82fb7636-ded5-495c-a0e2-5a3741588f57', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.048592'),
('38a60cec-8af6-4af2-ae2e-7305bedac24a', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.04861'),
('cfc72d77-fdaf-4605-a5b6-71dc5ac33c22', '59f76466-cee9-48d4-bac5-9dc955575a79', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.049617'),
('e42d443c-418f-49da-935d-54f8c7a98a09', '59f76466-cee9-48d4-bac5-9dc955575a79', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.050799'),
('22a306d2-1b48-4b7c-ba0e-fed8e5c2314b', '59f76466-cee9-48d4-bac5-9dc955575a79', 'diff', 280, 'PASS', U&'diff --git a/notes/pair.txt b/notes/pair.txt\000anew file mode 100644\000aindex 0000000..524a84d\000a--- /dev/null\000a+++ b/notes/pair.txt\000a@@ -0,0 +1 @@\000a+feat: pair-programmed with an allow-listed co-author - 2026-09-08T17:13:59Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.051415'),
('c0e7c2b7-2c44-419e-b3d1-6d423788512b', '59f76466-cee9-48d4-bac5-9dc955575a79', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.051972'),
('005d84aa-eead-454d-a629-ef2f58e6fe6e', '59f76466-cee9-48d4-bac5-9dc955575a79', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.051999'),
('9ba13b17-5242-4674-9798-da7df1a8ff90', '59f76466-cee9-48d4-bac5-9dc955575a79', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.415658'),
('427c215a-ec66-4997-b41d-7dd678a9ab34', '59f76466-cee9-48d4-bac5-9dc955575a79', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.41966'),
('cc3b7983-a579-4b96-8c6c-30ff38e77818', '59f76466-cee9-48d4-bac5-9dc955575a79', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:00.419702'),
('daf6963c-285e-40ae-99be-6f19510f7b08', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.305209'),
('6e72e589-f2dd-46eb-8969-7e747da0192e', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.317913'),
('51ec8154-f044-46a0-bd2d-f6c6b80d122a', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.318195'),
('7558f7b5-c69f-4b2f-93a2-206f49c15666', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.320556'),
('d6040b76-aed6-4880-b559-72e510a06297', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'commitAttributionPolicy', 160, 'WARN', U&'2 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.321538'),
('ba579101-3e2b-40e5-b356-595388db38ae', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.321559'),
('39357569-77f0-4ef4-8567-982a8800076c', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.322243'),
('d51ab7e5-3781-47ce-885a-98384e38f870', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkAuthorEmails', 250, 'FAIL', U&'\274c\fe0f  author email (noreply@example.com): blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.322441');        
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('f2370247-a478-47a6-8f38-2d68e79d3f6d', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit 807e55a has no Signed-off-by trailer\000a  \2192 This repository requires the Developer Certificate of Origin (DCO) sign-off.\000a  \2192 Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase --signoff <base> for a range).', 'missing Signed-off-by (807e55a)', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.322556'),
('1ebe45fb-9924-4fe7-be68-bfbcc4c4d2f2', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkCommitMessages', 260, 'FAIL', U&'\274c\fe0f  WIP: commit 2 \2014 bad commit message: contains blocked term: "WIP"\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'contains blocked term: "WIP"', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.322968'),
('cbbf227c-4d29-401d-9997-f1d8617c3dfe', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.326205'),
('fa274a88-a54c-4bbe-aa57-280ed7df0f6c', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.32991'),
('dc0c5282-5353-4040-bed7-cd75e6397c5a', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'diff', 280, 'PASS', U&'diff --git a/ci-config.env b/ci-config.env\000anew file mode 100644\000aindex 0000000..e2977e7\000a--- /dev/null\000a+++ b/ci-config.env\000a@@ -0,0 +1 @@\000a+GITHUB_TOKEN=[REDACTED]\000adiff --git a/config.yml b/config.yml\000anew file mode 100644\000aindex 0000000..bb7ce43\000a--- /dev/null\000a+++ b/config.yml\000a@@ -0,0 +1,2 @@\000a+upstream:\000a+  api: https://internal.corp.example.com/api/v1\000adiff --git a/multi/1.txt b/multi/1.txt\000anew file mode 100644\000aindex 0000000..3e78d2a\000a--- /dev/null\000a+++ b/multi/1.txt\000a@@ -0,0 +1 @@\000a+test: commit 1 \2014 noreply author email - 2026-09-08T17:14:01Z\000adiff --git a/multi/2.txt b/multi/2.txt\000anew file mode 100644\000aindex 0000000..efd74ff\000a--- /dev/null\000a+++ b/multi/2.txt\000a@@ -0,0 +1 @@\000a+WIP: commit 2 \2014 bad commit message - 2026-09-08T17:14:01Z\000adiff --git a/multi/5.txt b/multi/5.txt\000anew file mode 100644\000aindex 0000000..3ff8b23\000a--- /dev/null\000a+++ b/multi/5.txt\000a@@ -0,0 +1 @@\000a+test: commit 5 \2014 unregistered commit email - 2026-09-08T17:14:01Z\000adiff --git a/multi/6.txt b/multi/6.txt\000anew file mode 100644\000aindex 0000000..d128d5e\000a--- /dev/null\000a+++ b/multi/6.txt\000a@@ -0,0 +1 @@\000a+test: commit 6 \2014 missing DCO sign-off - 2026-09-08T17:14:01Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.331001'),
('ae17f243-8a75-40e2-aa8f-b00145f0904f', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanDiff', 300, 'FAIL', U&'blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.331241'),
('1e9b0a1f-76c4-4286-adfb-5c6fc8008d7a', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanDiff', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.33125'),
('d3e11471-8801-4894-a31c-f10d70ac2c66', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanDiff', 300, 'FAIL', U&'commit 255629f: blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', '255629f: blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.333611'),
('a8279d71-cb98-4dda-b6aa-6aa1fe082b03', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanDiff', 300, 'FAIL', U&'commit 255629f: blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', '255629f: blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.333615');      
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('c302745b-848f-4638-9eef-55c6a5f75b3c', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.334465'),
('227f58cf-02ad-4c84-a001-62f69bf76a97', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [github-pat]  ci-config.env:1\000a  commit: 51eaa60\000a  match:  [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[github-pat]  ci-config.env:1\000a  commit: 51eaa60\000a  match:  [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.716835'),
('87556b6a-5a71-4ca7-94e1-1e7393a0a654', 'ed117cf9-0789-4341-9561-cf551eff7d42', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:02.721106'),
('8640a112-c20b-4588-9528-c287e746344b', '035b2f93-5eb4-46b2-a916-213f5943e2f6', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:04.43848'),
('d450305b-1bea-4eb8-8f9d-09fc8f5a9259', '035b2f93-5eb4-46b2-a916-213f5943e2f6', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:05.960403'),
('976b39eb-25e7-4b7e-a0f0-32d14cfe034d', '035b2f93-5eb4-46b2-a916-213f5943e2f6', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:05.960725'),
('aff83215-7e24-4bf8-80b0-37055f5451de', '035b2f93-5eb4-46b2-a916-213f5943e2f6', 'checkUserPermission', 150, 'FAIL', U&'\000a\26d4\fe0f  Push Blocked - Unauthorized\000a\000a\274c\fe0f  dev is not allowed to push to:\000a   \+01f517\fe0f  https://codeberg.org/fixture-dev/fogwall-fixture\000a', 'User not authorized', NULL, NULL, TIMESTAMP '2026-09-08 13:14:06.493072'),
('4380bb9b-bb7e-4f18-9d5f-27c5199f0fe7', '0f36835e-186b-4729-955d-5deac7a5b658', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:07.422302'),
('898c89c9-9469-4a28-9acc-5cf82522d7b5', '0f36835e-186b-4729-955d-5deac7a5b658', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.029374'),
('eee9f627-f14d-4628-8f7f-19f3cba52837', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.029948'),
('1c5a73af-b23d-4d12-a389-71cb2b3f4ca2', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.033393'),
('7c89bbc2-f0cd-427f-b80d-7e7e29fdacee', '0f36835e-186b-4729-955d-5deac7a5b658', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.03421'),
('4c26df39-212b-473d-a1d7-591f1c729202', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.03425'),
('a8a6dced-6e4f-48e6-be53-d6f39b6a2793', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.035271'),
('d3eae848-2201-4641-adb5-482eb8a1f744', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.035345'),
('783c134c-87a7-4468-aa4b-08243a61b9f8', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.035408'),
('c291f1a1-da1d-4cb8-a9b6-98b78b25aa18', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.035446'),
('f219d486-e3f5-4a82-abcc-74c3f9250e41', '0f36835e-186b-4729-955d-5deac7a5b658', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.038045'),
('866834d0-bdd1-41bd-8b92-21291cf91c79', '0f36835e-186b-4729-955d-5deac7a5b658', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.040803'),
('45b5b69b-865a-4185-bfe9-93e2ebf07e96', '0f36835e-186b-4729-955d-5deac7a5b658', 'diff', 280, 'PASS', U&'diff --git a/docs/release-notes.md b/docs/release-notes.md\000anew file mode 100644\000aindex 0000000..6afad14\000a--- /dev/null\000a+++ b/docs/release-notes.md\000a@@ -0,0 +1 @@\000a+docs: add release notes stub - 2026-09-08T17:14:06Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.04188');               
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('560500ed-ec72-4020-9bb5-b438f6b37138', '0f36835e-186b-4729-955d-5deac7a5b658', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.042781'),
('37db2d77-dcda-434f-a395-61eb668ce917', '0f36835e-186b-4729-955d-5deac7a5b658', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.042799'),
('c4606120-873d-46e0-bb26-0217d44432b9', '0f36835e-186b-4729-955d-5deac7a5b658', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.407796'),
('35e2c36c-06c4-43da-b850-c5133bbb67bf', '0f36835e-186b-4729-955d-5deac7a5b658', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.411339'),
('01d96c3b-3d7a-4825-94f8-68ccba0a16b3', '0f36835e-186b-4729-955d-5deac7a5b658', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:08.411371'),
('66555d0d-15d4-47c3-a444-f5c9f1272990', '358339ec-552e-484d-854a-602be97a7eea', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.195129'),
('3680ce2b-3e40-4585-a075-207802fc9dd6', '358339ec-552e-484d-854a-602be97a7eea', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.205842'),
('bea18f41-74fe-4d06-9b15-fadcdceba7dd', '358339ec-552e-484d-854a-602be97a7eea', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.206316'),
('25060cfd-9b31-4f7c-af14-2f80ddb05e8e', '358339ec-552e-484d-854a-602be97a7eea', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.208942'),
('81758f66-9df0-4cf6-b5dd-a882bd3ff2e4', '358339ec-552e-484d-854a-602be97a7eea', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.208999'),
('88e59ce5-f635-4e23-ae80-f85827a288e5', '358339ec-552e-484d-854a-602be97a7eea', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.209028'),
('51d588fe-029e-49c5-96d6-2a408ec42bd2', '358339ec-552e-484d-854a-602be97a7eea', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.209657'),
('02fab44e-5722-464d-aa40-96e6c5668027', '358339ec-552e-484d-854a-602be97a7eea', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.209683'),
('fffe821c-b233-40b3-ba44-8d6c298fa73f', '358339ec-552e-484d-854a-602be97a7eea', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.209698'),
('0d70d062-2700-4fe6-b046-5b2fa59efaef', '358339ec-552e-484d-854a-602be97a7eea', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.209754'),
('5c4bb28d-e5b2-416f-9e07-1825c1ce041b', '358339ec-552e-484d-854a-602be97a7eea', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.211852'),
('b5245008-828e-4975-be14-dabeacd2a5c1', '358339ec-552e-484d-854a-602be97a7eea', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.211892'),
('cbdfb173-cfc2-4934-9f6a-4d42c447e991', '358339ec-552e-484d-854a-602be97a7eea', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.211903'),
('670e453e-4fe9-4802-89e9-6a85db5d57dc', '358339ec-552e-484d-854a-602be97a7eea', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.211914'),
('fc80b75a-7b9e-4d66-a77c-9718a6749542', '358339ec-552e-484d-854a-602be97a7eea', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.569578'),
('845862b2-5c3d-4e25-87f9-18d7dca5b6c3', '358339ec-552e-484d-854a-602be97a7eea', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.569608'),
('70be0ab7-ca03-4607-9968-4aa12e7ab185', '358339ec-552e-484d-854a-602be97a7eea', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:09.569623'),
('e595d9a0-e464-45b4-8e46-47ab79d27c56', '14396ba7-ccde-4191-a213-c7b0c891b680', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:10.635244'); 
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('aab86d8b-dca7-4794-9a05-846eec3d916e', '14396ba7-ccde-4191-a213-c7b0c891b680', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.367106'),
('d8cfac7d-0595-4c3f-9878-aed2355b95dc', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.367362'),
('ae4c4274-a6b8-43fa-8cb9-9e065d95f57f', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.628832'),
('71f560b2-d51d-4ef4-a905-6cbe9ee29ae3', '14396ba7-ccde-4191-a213-c7b0c891b680', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.630202'),
('e7123f5b-c22b-415c-819d-6c5a47508e63', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.630229'),
('48c2d0f1-eb98-4c09-89d4-c3edca111cdf', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.630954'),
('f8df7754-bc24-42bd-b1a8-54128fea379d', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.631011'),
('ebbf4693-89bc-42ae-90e2-7c21406c14b8', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.631041'),
('8ad9a47d-1995-42e7-bc47-a19cdf9cddb8', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.631079'),
('d95db8d7-0f88-4792-ad7a-d6ec8acb863c', '14396ba7-ccde-4191-a213-c7b0c891b680', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.633012'),
('5998700c-af0b-4410-9e7f-5bd029a8e579', '14396ba7-ccde-4191-a213-c7b0c891b680', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.636'),
('bd9039fe-9dbd-4ebb-a935-f8fa0ca047cc', '14396ba7-ccde-4191-a213-c7b0c891b680', 'diff', 280, 'PASS', U&'diff --git a/README.md b/README.md\000aindex 2271fc7..4502783 100644\000a--- a/README.md\000a+++ b/README.md\000a@@ -1,3 +1,3 @@\000a # fogwall-fixture\000a \000a-fogwall UI fixture capture \2014 safe to delete\000a\\ No newline at end of file\000a+fogwall UI fixture capture \2014 safe to deletedocs: touch readme via fogwall - 2026-09-08T17:14:10Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.637438'),
('9d510a40-cdd2-493d-b4c4-b8dfdf3b172c', '14396ba7-ccde-4191-a213-c7b0c891b680', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.638662'),
('96a8ed59-16a2-466c-8ede-8eb20c2d31ee', '14396ba7-ccde-4191-a213-c7b0c891b680', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.638693'),
('da2342ba-08be-467d-8763-cd462c4ebc8a', '14396ba7-ccde-4191-a213-c7b0c891b680', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.980253'),
('428175e4-19b2-4be0-bdc3-75bd89d1a55e', '14396ba7-ccde-4191-a213-c7b0c891b680', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.982842'),
('001122a4-821a-482c-87c2-2b8bdc440ebe', '14396ba7-ccde-4191-a213-c7b0c891b680', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:11.982857'),
('65735ae0-866e-4429-a546-debf94cb38a8', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:14.994286'),
('efe45d07-e99a-4295-a1ed-942024018536', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.62563'),
('769f6a9d-026e-45a1-bc1e-b18048631119', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.625832'),
('1d458998-42bb-4d50-9d22-a7fc84d25cae', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.800256');         
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('f3a0824d-1157-4630-af53-0a7622a9dd88', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'commitAttributionPolicy', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.803392'),
('494a1486-510e-49a6-a9e7-6c02af6c1c5e', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.80345'),
('1b38c6c6-d42d-4bd9-97c4-3d1b0143279d', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.804632'),
('a288637b-3ed5-409d-8321-657c2434a5d6', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.804716'),
('cb2b80c5-6f52-4501-bed4-835d6323d6d6', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.80476'),
('d7dd7188-8a6d-4009-a272-1067b8c580aa', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.804923'),
('2c3224cd-8b70-4284-8b7f-3b335d34b027', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.807458'),
('5f0bcc28-3b90-40df-8264-d5f49811fae3', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.810305'),
('4c44c32e-e37c-4208-a978-0e5e1988e017', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'diff', 280, 'PASS', U&'diff --git a/notes/gitlab.txt b/notes/gitlab.txt\000anew file mode 100644\000aindex 0000000..4f84455\000a--- /dev/null\000a+++ b/notes/gitlab.txt\000a@@ -0,0 +1 @@\000a+test: identity resolution \2014 gitlab resolved, email unregistered - 2026-09-08T17:14:14Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.811467'),
('720db002-b7c8-4d29-b3bc-71e76149cbdc', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.81243'),
('09a5ae5e-3a0c-4998-9dc1-6202a51ffe58', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:16.812464'),
('b8598ddd-e8ae-4f87-b95c-1fdae9cf2d6c', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:17.193738'),
('5bd22a0d-d247-4c97-baaf-8e573dcdce04', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:17.197685'),
('5444aaab-112f-4f86-be83-df938d9c2b80', '13c11e61-6ce9-4f71-8c95-6b34a08f3f97', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:17.197728'),
('f0b0b594-8436-402a-b858-519275879861', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:18.381608'),
('24e1445d-2936-46b9-861e-de10e55ca3f8', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.192421'),
('b5475ac5-450e-4de9-b1a1-b495c8240351', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.192869'),
('b21e64a9-fd70-4b2f-ad67-2f12a2a18047', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.194947'),
('85bd7824-3e02-4b71-9827-71ae8b323a68', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.195405'),
('12fc9bae-5fe9-46d9-919d-3aa559ec469a', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.195426'),
('ab8906ca-ab4c-41f8-9c0a-319fbc348043', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.196207');           
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('519d776c-4224-434c-9c7f-62fa5073884c', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.196263'),
('a9cdf9f4-3152-4372-bea6-3bb9aa450ba2', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.19631'),
('876e1001-bff2-43b8-9e3a-e80e5d275590', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.19635'),
('73d8295f-6603-4570-adb6-bb1b6de0b894', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.198442'),
('ac1ff427-33cf-496c-b327-b0dc13c91425', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.203194'),
('12b70500-05c7-41d2-b609-0c8f61b42d44', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'diff', 280, 'PASS', U&'diff --git a/src/alpha.txt b/src/alpha.txt\000anew file mode 100644\000aindex 0000000..7a3bda8\000a--- /dev/null\000a+++ b/src/alpha.txt\000a@@ -0,0 +1 @@\000a+feat(alpha): first of three - 2026-09-08T17:14:17Z\000adiff --git a/src/beta.txt b/src/beta.txt\000anew file mode 100644\000aindex 0000000..6e8ecc6\000a--- /dev/null\000a+++ b/src/beta.txt\000a@@ -0,0 +1 @@\000a+feat(beta): second of three - 2026-09-08T17:14:17Z\000adiff --git a/src/gamma.txt b/src/gamma.txt\000anew file mode 100644\000aindex 0000000..6ee284a\000a--- /dev/null\000a+++ b/src/gamma.txt\000a@@ -0,0 +1 @@\000a+fix(gamma): third of three - 2026-09-08T17:14:17Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.204451'),
('1b6a29e7-6c0b-4c37-b26e-c0583f4ee6cc', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.207077'),
('dec1e2f7-5043-4cfa-9bb0-8ee0ee746e08', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.207097'),
('7a797de1-8ccb-42d5-9549-e01e6fc6e724', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.572235'),
('a1283358-d7cd-4ac3-8a37-3be059913b4d', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.576709'),
('2325ec9c-d131-435c-82e1-9d629f296597', '2e35354b-3201-4b86-afff-50e7eca07dbb', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:19.576735'),
('391ad1ba-ac26-4c69-86e5-d0ec428248d5', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.933032'),
('8713a6be-2c1c-488e-8b64-49d2e4d9fec7', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.937185'),
('c5cde643-5240-4854-be60-4dc8e18a6749', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.93746'),
('cba0caae-714e-46b4-b16d-fddfb240862e', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939363'),
('fd09df01-818e-4422-b16b-a6f249b12bc9', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939387'),
('9bcdb69a-7b81-462e-be76-f8f9afd6a860', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939395'),
('9132f7c4-9bae-4840-8dbd-341d3d365e3a', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939922'),
('872864d0-60b3-43e6-9816-0a50c8926970', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939955'),
('8ad9fd28-1fe8-4c87-b530-f7c8fdd893b4', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939965');               
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('ab5372fa-3216-41aa-b3be-1d91ee0bcc3c', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.939978'),
('b733d102-ec63-4234-a091-44b1d7b8d84e', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.940008'),
('f56dce81-5fdb-42cb-8ce2-5fb4957937b5', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.940021'),
('0cd644a5-a9ae-41dc-9a57-803defe9ea47', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.940029'),
('032ad9f9-e3fa-400d-a85f-049591acf18a', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:20.940039'),
('500058e5-671f-4070-ae7b-eb908bbb14b5', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:21.30992'),
('e4a5ea68-2257-4bb6-ad5e-8361849af669', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:21.30996'),
('c27c5ecf-5faa-44ff-b1be-dc68209aba9f', 'e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:21.309973'),
('0600f5ee-fe5f-451f-b87c-448f7ddea93d', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.363457'),
('0720fde3-aa78-4f96-9f67-12c160fc2310', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.368093'),
('cbd8bbc0-c182-43eb-aeb1-2a00a766b762', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.368457'),
('bec14af6-1537-4193-9f8f-313f8230990a', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.37098'),
('8b464765-3d2f-44d9-bb27-1f3eca465a4b', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.371692'),
('02d1d6b7-acc0-45e7-aaef-f070b8d6e495', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.371719'),
('e37b4e14-5f7c-47e1-b0b3-896acc0b175f', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.372536'),
('509f75c9-5f4a-44cd-baa0-85d60f3ffbf3', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.372605'),
('56aabe1b-6085-4f55-9e6c-f135f1ce1762', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.372644'),
('877155cd-6904-4a40-af14-cf387b3d17e3', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.372682'),
('e135a7f8-6eaa-43d7-b2bc-fc98caf83dc5', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.383988'),
('577fc639-ee7f-4eba-a5dd-c936c083be5e', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.386358'),
('ce596c5a-9a71-4176-be63-a183702bad40', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'diff', 280, 'PASS', U&'diff --git a/config/feature-flags.yml b/config/feature-flags.yml\000anew file mode 100644\000aindex 0000000..4d6af48\000a--- /dev/null\000a+++ b/config/feature-flags.yml\000a@@ -0,0 +1 @@\000a+feat: enable experimental flag - 2026-09-08T17:14:21Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.387245'),
('9e6ca629-d737-4c80-9c4e-e25a50fc3add', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.38813');  
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('af9e226c-b7f5-4fe9-94e8-0202814ac176', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.388157'),
('1a7e2486-e943-4d0a-a111-c2ed3be4ef5d', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.735948'),
('6bc09c33-810b-4e3b-8b61-3c87cc30237a', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.739147'),
('c3a1432e-cf75-49dc-beda-f172bb7fad84', '6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:22.739168'),
('942c10b6-8ba5-45f9-942c-598360379cc4', '24c32427-cee8-42ff-84c8-9918b01cc788', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.108867'),
('127421af-8e4b-4361-8878-9bd8affc103b', '24c32427-cee8-42ff-84c8-9918b01cc788', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.113502'),
('4908b20b-0a9a-41d7-b006-e61b51d1f6c5', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.113788'),
('59a3b4f6-bb8e-40a7-8d3e-4423950e9f1c', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.116004'),
('9363bc6b-a98b-445e-b3bf-50c734eea80b', '24c32427-cee8-42ff-84c8-9918b01cc788', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.116456'),
('11fb2bd6-f83e-4d3a-9289-8963ebccfe67', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.116473'),
('03a81b5f-bfe7-4995-a08e-86bc838c7078', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.117144'),
('03f88543-3b93-4d64-bc9a-533434d52c65', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.117191'),
('693b564b-4f84-4c51-be87-f8b246305406', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.117217'),
('cc6003ad-5e58-4cb8-af4a-958c785e3a23', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.117241'),
('e9bd0279-d8a1-40c2-9844-2881ef353b28', '24c32427-cee8-42ff-84c8-9918b01cc788', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.118932'),
('2f9f96ff-91ff-4d29-aa84-6da0c7e93158', '24c32427-cee8-42ff-84c8-9918b01cc788', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.121122'),
('9bb6b0d4-fd43-4a03-a1bc-1b256105d673', '24c32427-cee8-42ff-84c8-9918b01cc788', 'diff', 280, 'PASS', U&'diff --git a/scratch.txt b/scratch.txt\000anew file mode 100644\000aindex 0000000..6574da6\000a--- /dev/null\000a+++ b/scratch.txt\000a@@ -0,0 +1 @@\000a+chore: exploratory change, withdrawn - 2026-09-08T17:14:23Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.122193'),
('7007add1-f2b7-4c9a-9534-3c72f7ea3a34', '24c32427-cee8-42ff-84c8-9918b01cc788', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.123286'),
('6f125e1b-14a5-4df9-9593-105c3a31e7f6', '24c32427-cee8-42ff-84c8-9918b01cc788', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.123317'),
('63623e9a-acfe-4685-86f4-a358d1ac9f32', '24c32427-cee8-42ff-84c8-9918b01cc788', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.490684'),
('25e87401-57f6-489a-ae6b-ce81dbc42bfd', '24c32427-cee8-42ff-84c8-9918b01cc788', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.493113'),
('27f5550c-dd4b-4c7e-82a6-d97a8beac9aa', '24c32427-cee8-42ff-84c8-9918b01cc788', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:24.493127');
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('6556984b-58ae-4168-aed4-326c57e4bdea', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.466795'),
('09986fd4-7bf2-4a73-b153-0fa70fafb58f', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.949757'),
('34f443c1-05dc-4104-9473-3d3b0fbeea54', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.949943'),
('dbf238a3-b8a2-4307-99cc-01394601e21a', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.950869'),
('655eac45-acd8-4465-86d3-0a976b08df07', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.951057'),
('630d4f48-8752-41b4-b469-1df76452547e', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.951065'),
('3f664987-b886-4b51-8934-be4c5c3ffc24', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.951471'),
('7af44b14-9a0b-41ef-a6f8-72702a9a92b3', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.951569'),
('0ef47ebc-eacb-4788-9f9f-b9e1b8d61781', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.951656'),
('ac6ce7cb-26a7-4c84-85cb-c097dd441f0c', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.951698'),
('0fcd092d-7e92-4841-a403-251cf479ed5f', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.953916'),
('764bb449-36a1-4031-97a4-9849bf6f964b', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.957582'),
('b9bf819f-2c5e-45f6-82d8-1ba288c51273', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'diff', 280, 'PASS', U&'diff --git a/docs/faq.md b/docs/faq.md\000anew file mode 100644\000aindex 0000000..b4b8a02\000a--- /dev/null\000a+++ b/docs/faq.md\000a@@ -0,0 +1 @@\000a+docs: answer the most common question - 2026-09-08T17:14:25Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.958313'),
('2b24e152-e7bf-44c5-9d3a-f96157445bb9', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.958794'),
('61dc9bf5-3fa0-4016-99e4-b929f96227c7', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:25.958804'),
('a636612d-f771-4ef8-b1a4-68f07aa0938a', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:26.313754'),
('ea684e5e-f2ec-4c0d-9372-464bcd46552c', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:26.315725'),
('b07bbf37-dd41-4540-b6a2-dcd32da75f2c', '995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:26.315738'),
('11737692-a42e-4837-923b-924680b55fe2', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkUrlRules', 100, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:27.877869'),
('2591551e-b406-4e02-a9a8-b4342bf3e809', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkUserPermission', 150, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.118505'),
('54c54128-8f62-4417-a423-ee8f69f1e164', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'commitAttributionPolicy', 160, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.119396'),
('1413260f-be0b-4c39-9f59-5a3193c39780', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'commitEnrichment', 195, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.119465');               
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('2516aae3-bc2a-4646-a395-d2dc9a27a82d', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkEmptyBranch', 210, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.121287'),
('8365d80d-ec97-411e-b287-15920c8d2024', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkHiddenCommits', 220, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.12327'),
('a247d6b4-3e4f-46af-b09f-c9d5f3c8c9d5', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkAuthorEmails', 250, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.123819'),
('cdb33bf5-67e7-4f7e-ab11-50939567aade', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkTrailers', 255, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.124041'),
('6ca6d23a-c750-4e6d-b59d-605d0cdb9b96', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkCommitMessages', 260, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.124427'),
('313537f5-c253-4100-82d4-032cc53c0569', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'scanContentPatternsMessages', 265, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.12638'),
('e382b3a9-8611-4ff6-8acb-c1a8c6acea15', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'inspection', 270, 'PASS', NULL, NULL, NULL, U&'CREATE refs/heads/fixture/ssh-server-666324 0000000 -> 08af805\000a---LOG---\000aNew branch - tip commit by Fixture Developer <fixture-dev@example.com>', TIMESTAMP '2026-09-08 13:14:28.126648'),
('05c4a93d-8d40-4075-b7aa-273b70e8b6d9', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'diff', 280, 'PASS', U&'diff --git a/docs/ssh-transport.md b/docs/ssh-transport.md\000anew file mode 100644\000aindex 0000000..00c57cc\000a--- /dev/null\000a+++ b/docs/ssh-transport.md\000a@@ -0,0 +1 @@\000a+feat: pushed over the SSH transport - 2026-09-08T17:14:27Z\000a', NULL, NULL, U&'ref: refs/heads/fixture/ssh-server-666324\000a---LOG---\000arange: 0000000000000000000000000000000000000000..08af8057382e3da737b1a97118b415301ff7485f\000a---LOG---\000alines: 7\000a---LOG---\000atype: auto', TIMESTAMP '2026-09-08 13:14:28.127822'),
('709537ce-2149-4b92-a601-65eb97eb54a6', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'binaryBlob', 290, 'PASS', NULL, NULL, NULL, 'PASS: aggregate: refs/heads/fixture/ssh-server-666324', TIMESTAMP '2026-09-08 13:14:28.129323'),
('a113b319-8722-48e7-b01e-8f028c08cb16', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'scanDiff', 300, 'PASS', NULL, NULL, NULL, 'PASS: aggregate diff', TIMESTAMP '2026-09-08 13:14:28.130157'),
('a07033a8-60a5-45e2-876a-0c531f1dea82', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'checkSignatures', 320, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.130253'),
('774234bc-0435-4909-a1c1-d2a93ad8b996', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'scanSecrets', 340, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.480915'),
('f1db0fe4-97c4-41d5-b4b7-a78c71f84cb3', 'f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'scanContentPatternsDiff', 345, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-08 13:14:28.482298');         
CREATE INDEX "PUBLIC"."IDX_PUSH_STEPS_PUSH_ID" ON "PUBLIC"."PUSH_STEPS"("PUSH_ID" NULLS FIRST);
CREATE CACHED TABLE "PUBLIC"."SCM_API_GITHUB_NODE_CACHE"(
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "NODE_ID" CHARACTER VARYING(255) NOT NULL,
    "REPO_OWNER" CHARACTER VARYING(255) NOT NULL,
    "REPO_NAME" CHARACTER VARYING(255) NOT NULL,
    "CACHED_AT" TIMESTAMP NOT NULL
);              
ALTER TABLE "PUBLIC"."SCM_API_GITHUB_NODE_CACHE" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B4" PRIMARY KEY("PROVIDER", "NODE_ID");   
-- 3 +/- SELECT COUNT(*) FROM PUBLIC.SCM_API_GITHUB_NODE_CACHE;
INSERT INTO "PUBLIC"."SCM_API_GITHUB_NODE_CACHE" VALUES
('github', 'R_kgDOUSmvJw', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-08 13:16:09.981795'),
('github', 'PR_kwDOUSmvJ88AAAABCtZgQA', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-08 13:16:12.336536'),
('github', 'I_kwDOUSmvJ88AAAABQUGLYg', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-08 13:16:17.222039');      
CREATE CACHED TABLE "PUBLIC"."SCM_API_GITLAB_PROJECT_CACHE"(
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "PROJECT_ID" CHARACTER VARYING(255) NOT NULL,
    "REPO_OWNER" CHARACTER VARYING(255) NOT NULL,
    "REPO_NAME" CHARACTER VARYING(255) NOT NULL,
    "CACHED_AT" TIMESTAMP NOT NULL
);        
ALTER TABLE "PUBLIC"."SCM_API_GITLAB_PROJECT_CACHE" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_34" PRIMARY KEY("PROVIDER", "PROJECT_ID");             
-- 1 +/- SELECT COUNT(*) FROM PUBLIC.SCM_API_GITLAB_PROJECT_CACHE;             
INSERT INTO "PUBLIC"."SCM_API_GITLAB_PROJECT_CACHE" VALUES
('gitlab', '86231428', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-08 13:16:26.673792');      
CREATE CACHED TABLE "PUBLIC"."PUSH_ATTESTATIONS"(
    "PUSH_ID" CHARACTER VARYING(36) NOT NULL,
    "TYPE" CHARACTER VARYING(20) NOT NULL,
    "REVIEWER_USERNAME" CHARACTER VARYING(255),
    "REVIEWER_EMAIL" CHARACTER VARYING(255),
    "REASON" CHARACTER VARYING,
    "AUTOMATED" BOOLEAN DEFAULT FALSE NOT NULL,
    "SELF_APPROVAL" BOOLEAN DEFAULT FALSE NOT NULL,
    "TIMESTAMP" TIMESTAMP NOT NULL,
    "ANSWERS" CHARACTER VARYING
);             
ALTER TABLE "PUBLIC"."PUSH_ATTESTATIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B09" PRIMARY KEY("PUSH_ID", "TYPE", "TIMESTAMP"); 
-- 6 +/- SELECT COUNT(*) FROM PUBLIC.PUSH_ATTESTATIONS;        
INSERT INTO "PUBLIC"."PUSH_ATTESTATIONS" VALUES
('2e35354b-3201-4b86-afff-50e7eca07dbb', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'yes', FALSE, FALSE, TIMESTAMP '2026-09-08 13:14:54.863284', '{"policy-compliance":"true","reviewed-content":"true"}'),
('e4c62ba8-143c-4f5a-8eef-584f34a71eb1', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'ok', FALSE, FALSE, TIMESTAMP '2026-09-08 13:15:02.606102', '{"reviewed-content":"true","policy-compliance":"true"}'),
('6c0a3ec7-a4d8-48ee-8f54-269f6f644533', 'REJECTION', 'reviewer', 'reviewer@example.com', 'missing ticket ref', FALSE, FALSE, TIMESTAMP '2026-09-08 13:15:16.317254', NULL),
('f1c0a843-a5e9-4636-8357-7b5d50fd8190', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'lgtm', FALSE, FALSE, TIMESTAMP '2026-09-08 13:15:26.790629', '{"reviewed-content":"true","policy-compliance":"true"}'),
('24c32427-cee8-42ff-84c8-9918b01cc788', 'CANCELLATION', 'dev', NULL, NULL, FALSE, FALSE, TIMESTAMP '2026-09-08 13:15:40.199316', NULL),
('995e7946-c575-4de9-a0cc-6e3f8c0833ff', 'APPROVAL', 'dev', 'fixture-dev@example.com', 'self certify', FALSE, FALSE, TIMESTAMP '2026-09-08 13:15:54.560215', '{"reviewed-content":"true","policy-compliance":"true"}'); 
CREATE CACHED TABLE "PUBLIC"."PROXY_USERS"(
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "PASSWORD_HASH" CHARACTER VARYING(255),
    "ROLES" CHARACTER VARYING(255) DEFAULT 'USER' NOT NULL
);          
ALTER TABLE "PUBLIC"."PROXY_USERS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_4" PRIMARY KEY("USERNAME");             
-- 3 +/- SELECT COUNT(*) FROM PUBLIC.PROXY_USERS;              
INSERT INTO "PUBLIC"."PROXY_USERS" VALUES
('dev', NULL, 'USER'),
('reviewer', NULL, 'USER'),
('admin', NULL, 'USER');          
CREATE CACHED TABLE "PUBLIC"."USER_EMAILS"(
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "EMAIL" CHARACTER VARYING(255) NOT NULL,
    "VERIFIED" BOOLEAN DEFAULT FALSE NOT NULL,
    "AUTH_SOURCE" CHARACTER VARYING(20) DEFAULT 'local' NOT NULL,
    "LOCKED" BOOLEAN DEFAULT FALSE NOT NULL
);       
ALTER TABLE "PUBLIC"."USER_EMAILS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_FB" PRIMARY KEY("USERNAME", "EMAIL");   
-- 6 +/- SELECT COUNT(*) FROM PUBLIC.USER_EMAILS;              
INSERT INTO "PUBLIC"."USER_EMAILS" VALUES
('reviewer', 'reviewer.alt@example.com', FALSE, 'local', FALSE),
('dev', '00000000+fixture-dev@users.noreply.github.com', TRUE, 'github', TRUE),
('dev', 'fixture-extra-1@example.com', TRUE, 'github', TRUE),
('dev', 'fixture-dev@example.com', TRUE, 'github', TRUE),
('dev', 'fixture-extra-2@example.com', TRUE, 'github', TRUE),
('dev', 'fixture-alt@example.com', TRUE, 'github', TRUE);      
CREATE INDEX "PUBLIC"."IDX_USER_EMAILS_EMAIL" ON "PUBLIC"."USER_EMAILS"("EMAIL" NULLS FIRST);  
CREATE CACHED TABLE "PUBLIC"."USER_SCM_IDENTITIES"(
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "SCM_USERNAME" CHARACTER VARYING(255) NOT NULL,
    "VERIFIED" BOOLEAN DEFAULT FALSE NOT NULL
);       
ALTER TABLE "PUBLIC"."USER_SCM_IDENTITIES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_50" PRIMARY KEY("USERNAME", "PROVIDER", "SCM_USERNAME");        
-- 5 +/- SELECT COUNT(*) FROM PUBLIC.USER_SCM_IDENTITIES;      
INSERT INTO "PUBLIC"."USER_SCM_IDENTITIES" VALUES
('reviewer', 'github', 'fixture-reviewer', FALSE),
('dev', 'github', 'fixture-dev', TRUE),
('dev', 'gitlab', 'fixture-dev', TRUE),
('dev', 'codeberg', 'fixture-dev', FALSE),
('dev', 'github', 'fixture-dev-alt', FALSE);   
CREATE CACHED TABLE "PUBLIC"."ACCESS_RULES"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100),
    "ACCESS" CHARACTER VARYING(10) DEFAULT 'ALLOW' NOT NULL,
    "OPERATION" CHARACTER VARYING(10) DEFAULT 'BOTH' NOT NULL,
    "DESCRIPTION" CHARACTER VARYING,
    "ENABLED" BOOLEAN DEFAULT TRUE NOT NULL,
    "RULE_ORDER" INTEGER DEFAULT 100 NOT NULL,
    "SOURCE" CHARACTER VARYING(10) DEFAULT 'DB' NOT NULL,
    "TARGET" CHARACTER VARYING(10) NOT NULL,
    "MATCH_VALUE" CHARACTER VARYING(512) NOT NULL,
    "MATCH_TYPE" CHARACTER VARYING(10) NOT NULL
);         
ALTER TABLE "PUBLIC"."ACCESS_RULES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_4D" PRIMARY KEY("ID"); 
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.ACCESS_RULES;             
CREATE INDEX "PUBLIC"."IDX_ACCESS_RULES_PROVIDER" ON "PUBLIC"."ACCESS_RULES"("PROVIDER" NULLS FIRST);          
CREATE CACHED TABLE "PUBLIC"."FETCH_RECORDS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "TIMESTAMP" TIMESTAMP NOT NULL,
    "PROVIDER" CHARACTER VARYING(100),
    "OWNER" CHARACTER VARYING(255),
    "REPO_NAME" CHARACTER VARYING(255),
    "RESULT" CHARACTER VARYING(10) NOT NULL,
    "PUSH_USERNAME" CHARACTER VARYING(255),
    "RESOLVED_USER" CHARACTER VARYING(255)
);           
ALTER TABLE "PUBLIC"."FETCH_RECORDS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_C0" PRIMARY KEY("ID");
-- 44 +/- SELECT COUNT(*) FROM PUBLIC.FETCH_RECORDS;           
INSERT INTO "PUBLIC"."FETCH_RECORDS" VALUES
('fe75b58b-e717-4e6a-86ad-1cfc7dca0c3f', TIMESTAMP '2026-09-08 13:13:41.158429', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('a119dc8c-d871-4ccb-b7e9-e6b3432e48d0', TIMESTAMP '2026-09-08 13:13:41.333656', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('072001e1-8932-4e35-b452-9901042faf46', TIMESTAMP '2026-09-08 13:13:44.344778', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('642d222d-7d0f-4848-a001-7d91a16e4213', TIMESTAMP '2026-09-08 13:13:44.492229', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('3c30b009-ce5d-4fe3-ad1e-fce24d3bec6d', TIMESTAMP '2026-09-08 13:13:45.964537', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('441d2e8a-917d-4908-b5b4-0997cd0f5e55', TIMESTAMP '2026-09-08 13:13:46.32017', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c5ecd622-3cc7-46c7-80a5-98f5f048f84d', TIMESTAMP '2026-09-08 13:13:47.504387', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d65b1177-f84e-4fe0-aabb-c81d4dac96df', TIMESTAMP '2026-09-08 13:13:47.775149', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d57e3899-ee1c-430f-a0c0-48c92991db6f', TIMESTAMP '2026-09-08 13:13:48.950423', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d04512cc-3f38-4a93-8e98-a23f667debf9', TIMESTAMP '2026-09-08 13:13:49.289421', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('871f7481-c95e-4bce-ab94-59716070b2b2', TIMESTAMP '2026-09-08 13:13:51.539158', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('46e00bef-0ca3-4d2c-ad96-650380d7f673', TIMESTAMP '2026-09-08 13:13:51.688673', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('87951735-ed40-49c8-8eed-f5f9220b806b', TIMESTAMP '2026-09-08 13:13:52.872731', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('931deb18-174c-4195-8e50-0912a48a3883', TIMESTAMP '2026-09-08 13:13:53.280694', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('dc4ddd8a-0261-43e4-9f48-38b16a3d0b7d', TIMESTAMP '2026-09-08 13:13:54.619974', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1adac605-6be2-444b-a7b2-195429778b87', TIMESTAMP '2026-09-08 13:13:54.761417', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('deee7172-8408-4856-8875-6bea58da064e', TIMESTAMP '2026-09-08 13:13:55.9922', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('a06cdc72-9ba3-444d-87b1-f966195b6afd', TIMESTAMP '2026-09-08 13:13:56.122834', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('4e7fe8ce-d7e5-42f3-bf00-4d541d4a19f1', TIMESTAMP '2026-09-08 13:13:57.874788', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c2593d11-242a-472d-9e53-adda8851b323', TIMESTAMP '2026-09-08 13:13:58.013277', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('01bee8a8-e23b-4b99-abeb-cc8173b1858d', TIMESTAMP '2026-09-08 13:13:59.268437', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d64b4b75-839a-418c-b903-22bd705d81ee', TIMESTAMP '2026-09-08 13:13:59.419896', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('8adf2b82-1b6e-47e0-a9c2-200911fde331', TIMESTAMP '2026-09-08 13:14:00.596952', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('2f810e94-a23e-4501-aae8-2cbc8a6b1169', TIMESTAMP '2026-09-08 13:14:00.957619', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('4188a245-2f87-44aa-be1d-71df33f067a2', TIMESTAMP '2026-09-08 13:14:03.515617', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('27fd3370-899c-43bb-8db7-44dac05a717d', TIMESTAMP '2026-09-08 13:14:03.725443', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL);               
INSERT INTO "PUBLIC"."FETCH_RECORDS" VALUES
('2646ead3-ea89-4de1-8103-51bed6c90829', TIMESTAMP '2026-09-08 13:14:06.658004', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d2ec84a9-de8f-4eb7-bec6-666ef18a7513', TIMESTAMP '2026-09-08 13:14:06.798478', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('20585859-5c50-4704-b157-665a8fea9b6c', TIMESTAMP '2026-09-08 13:14:08.57365', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d6145809-4745-4b3c-997f-b76d4651aa4a', TIMESTAMP '2026-09-08 13:14:08.715328', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('00299eff-3a44-46c2-a828-e5fe8d3bf2ac', TIMESTAMP '2026-09-08 13:14:09.980929', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('573690c2-0b70-45b1-ba57-5e4d73873f40', TIMESTAMP '2026-09-08 13:14:10.072826', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('348e8875-a39b-46f0-9450-41d9355381e6', TIMESTAMP '2026-09-08 13:14:12.879826', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d88dda15-04ca-4661-9c26-04816835b584', TIMESTAMP '2026-09-08 13:14:13.522069', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('63f538da-9098-497b-87c3-a67320b84180', TIMESTAMP '2026-09-08 13:14:17.374897', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('356b0524-68c5-402d-b23e-5ac18c175ba8', TIMESTAMP '2026-09-08 13:14:17.524408', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('0340a66d-900a-447e-abe5-b54c28d0a18c', TIMESTAMP '2026-09-08 13:14:19.735605', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1eb3c9c6-94a7-46d8-84bb-acbc3908b445', TIMESTAMP '2026-09-08 13:14:20.295643', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('ee3db273-59f7-42d6-8800-4d0aa3ef51bb', TIMESTAMP '2026-09-08 13:14:21.470914', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('11186b8f-e63f-41d6-ba68-3bff29473879', TIMESTAMP '2026-09-08 13:14:21.611866', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d4104822-4a2a-4953-91b6-da3b44df92ca', TIMESTAMP '2026-09-08 13:14:23.076563', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('2e1e17c0-cd64-4176-9975-9cb16f057e59', TIMESTAMP '2026-09-08 13:14:23.210013', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('84e24d1b-33b8-47ff-abab-9fee320621d3', TIMESTAMP '2026-09-08 13:14:24.656676', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('cf920ff9-c0b0-4692-8286-4294c50e0dcb', TIMESTAMP '2026-09-08 13:14:24.79853', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL);    
CREATE INDEX "PUBLIC"."IDX_FETCH_RECORDS_TIMESTAMP" ON "PUBLIC"."FETCH_RECORDS"("TIMESTAMP" NULLS FIRST);      
CREATE INDEX "PUBLIC"."IDX_FETCH_RECORDS_PROVIDER_REPO" ON "PUBLIC"."FETCH_RECORDS"("PROVIDER" NULLS FIRST, "OWNER" NULLS FIRST, "REPO_NAME" NULLS FIRST);     
CREATE CACHED TABLE "PUBLIC"."USER_SSH_KEYS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "FINGERPRINT" CHARACTER VARYING(255) NOT NULL,
    "PUBLIC_KEY" CHARACTER VARYING NOT NULL,
    "LABEL" CHARACTER VARYING(255),
    "CREATED_AT" TIMESTAMP NOT NULL,
    "LOCKED" BOOLEAN DEFAULT FALSE NOT NULL,
    "AUTH_SOURCE" CHARACTER VARYING(20) DEFAULT 'config' NOT NULL
);              
ALTER TABLE "PUBLIC"."USER_SSH_KEYS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_6" PRIMARY KEY("ID"); 
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.USER_SSH_KEYS;            
CREATE INDEX "PUBLIC"."IDX_USER_SSH_KEYS_USERNAME" ON "PUBLIC"."USER_SSH_KEYS"("USERNAME" NULLS FIRST);        
CREATE CACHED TABLE "PUBLIC"."SPRING_SESSION"(
    "PRIMARY_ID" CHARACTER(36) NOT NULL,
    "SESSION_ID" CHARACTER(36) NOT NULL,
    "CREATION_TIME" BIGINT NOT NULL,
    "LAST_ACCESS_TIME" BIGINT NOT NULL,
    "MAX_INACTIVE_INTERVAL" INTEGER NOT NULL,
    "EXPIRY_TIME" BIGINT NOT NULL,
    "PRINCIPAL_NAME" CHARACTER VARYING(100) DEFAULT NULL
);     
ALTER TABLE "PUBLIC"."SPRING_SESSION" ADD CONSTRAINT "PUBLIC"."SPRING_SESSION_PK" PRIMARY KEY("PRIMARY_ID");   
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.SPRING_SESSION;           
CREATE UNIQUE NULLS DISTINCT INDEX "PUBLIC"."SPRING_SESSION_IX1" ON "PUBLIC"."SPRING_SESSION"("SESSION_ID" NULLS FIRST);       
CREATE INDEX "PUBLIC"."SPRING_SESSION_IX2" ON "PUBLIC"."SPRING_SESSION"("EXPIRY_TIME" NULLS FIRST);            
CREATE INDEX "PUBLIC"."SPRING_SESSION_IX3" ON "PUBLIC"."SPRING_SESSION"("PRINCIPAL_NAME" NULLS FIRST);         
CREATE CACHED TABLE "PUBLIC"."SPRING_SESSION_ATTRIBUTES"(
    "SESSION_PRIMARY_ID" CHARACTER(36) NOT NULL,
    "ATTRIBUTE_NAME" CHARACTER VARYING(200) NOT NULL,
    "ATTRIBUTE_BYTES" BINARY VARYING NOT NULL
);              
ALTER TABLE "PUBLIC"."SPRING_SESSION_ATTRIBUTES" ADD CONSTRAINT "PUBLIC"."SPRING_SESSION_ATTRIBUTES_PK" PRIMARY KEY("SESSION_PRIMARY_ID", "ATTRIBUTE_NAME");   
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.SPRING_SESSION_ATTRIBUTES;
CREATE CACHED TABLE "PUBLIC"."REPO_PERMISSIONS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "OPERATION" CHARACTER VARYING(20) DEFAULT 'PUSH' NOT NULL,
    "SOURCE" CHARACTER VARYING(10) DEFAULT 'DB' NOT NULL,
    "TARGET" CHARACTER VARYING(10) DEFAULT 'SLUG' NOT NULL,
    "MATCH_VALUE" CHARACTER VARYING(512) NOT NULL,
    "MATCH_TYPE" CHARACTER VARYING(10) NOT NULL
);           
ALTER TABLE "PUBLIC"."REPO_PERMISSIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_47" PRIMARY KEY("ID");             
-- 9 +/- SELECT COUNT(*) FROM PUBLIC.REPO_PERMISSIONS;         
INSERT INTO "PUBLIC"."REPO_PERMISSIONS" VALUES
('30326603-73f5-4f2c-9ff5-4b87372662e2', 'dev', 'github', 'PUSH', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('38ac696c-ea24-463d-bd3f-21152c389980', 'dev', 'github', 'SELF_CERTIFY', 'CONFIG', 'SLUG', '/fixture-dev/fogwall-fixture', 'LITERAL'),
('43c63653-a814-4ca2-9e4e-b28184e49212', 'dev', 'gitea', 'PUSH', 'CONFIG', 'OWNER', 'fixture-dev', 'LITERAL'),
('8146605b-1e71-4530-9200-e1951402c120', 'dev', 'github', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('59d2bbee-c703-44eb-ad95-ada6de27c7fc', 'dev', 'gitlab', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('8b606d2b-bccf-4f5c-8c5b-dc4f7a3c2f9e', 'dev', 'codeberg', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('7979c65d-29aa-489a-8fd3-9fb8b58733da', 'dev', 'gitea', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('8f6c6f22-bc13-4cec-8059-1b1d53b34b6a', 'reviewer', 'github', 'REVIEW', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('095451bb-30f2-4087-99bb-503af1be4eb3', 'reviewer', 'gitlab', 'PUSH_AND_REVIEW', 'CONFIG', 'SLUG', '/fixture-dev/fogwall-fixture', 'LITERAL');  
CREATE INDEX "PUBLIC"."IDX_REPO_PERMISSIONS_USERNAME" ON "PUBLIC"."REPO_PERMISSIONS"("USERNAME" NULLS FIRST);  
CREATE INDEX "PUBLIC"."IDX_REPO_PERMISSIONS_PROVIDER" ON "PUBLIC"."REPO_PERMISSIONS"("PROVIDER" NULLS FIRST);  
CREATE CACHED TABLE "PUBLIC"."SSH_KEY_SOURCES"(
    "SSH_KEY_ID" CHARACTER VARYING(36) NOT NULL,
    "AUTH_SOURCE" CHARACTER VARYING(20) NOT NULL
);           
ALTER TABLE "PUBLIC"."SSH_KEY_SOURCES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_8E4" PRIMARY KEY("SSH_KEY_ID", "AUTH_SOURCE");      
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.SSH_KEY_SOURCES;          
CREATE CACHED TABLE "PUBLIC"."SSH_FINGERPRINT_CACHE"(
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "SCM_LOGIN" CHARACTER VARYING(255) NOT NULL,
    "FINGERPRINTS" CHARACTER VARYING NOT NULL,
    "CACHED_AT" TIMESTAMP NOT NULL
);    
ALTER TABLE "PUBLIC"."SSH_FINGERPRINT_CACHE" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_D" PRIMARY KEY("PROVIDER", "SCM_LOGIN");      
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.SSH_FINGERPRINT_CACHE;    
CREATE CACHED TABLE "PUBLIC"."PERMISSION_GROUPS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "NAME" CHARACTER VARYING(255) NOT NULL,
    "DESCRIPTION" CHARACTER VARYING(512),
    "SOURCE" CHARACTER VARYING(10) DEFAULT 'DB' NOT NULL
);   
ALTER TABLE "PUBLIC"."PERMISSION_GROUPS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B1" PRIMARY KEY("ID");            
-- 2 +/- SELECT COUNT(*) FROM PUBLIC.PERMISSION_GROUPS;        
INSERT INTO "PUBLIC"."PERMISSION_GROUPS" VALUES
('45c7db17-6786-4156-bcb4-1f2a19bcdf9e', 'platform-reviewers', 'Reviewers for the fixture-dev org', 'CONFIG'),
('87b603eb-ae17-4f8a-8d41-d8993bb8f179', 'gitlab-contributors', 'Push access to GitLab test repos', 'CONFIG');  
CREATE CACHED TABLE "PUBLIC"."GROUP_PERMISSIONS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "GROUP_ID" CHARACTER VARYING(36) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "TARGET" CHARACTER VARYING(20) DEFAULT 'SLUG' NOT NULL,
    "MATCH_VALUE" CHARACTER VARYING(512) NOT NULL,
    "MATCH_TYPE" CHARACTER VARYING(10) DEFAULT 'GLOB' NOT NULL,
    "OPERATION" CHARACTER VARYING(20) DEFAULT 'PUSH' NOT NULL
);      
ALTER TABLE "PUBLIC"."GROUP_PERMISSIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_8" PRIMARY KEY("ID");             
-- 2 +/- SELECT COUNT(*) FROM PUBLIC.GROUP_PERMISSIONS;        
INSERT INTO "PUBLIC"."GROUP_PERMISSIONS" VALUES
('6e0a65ad-c37a-4ffb-8f0c-e3fd6fe77a90', '45c7db17-6786-4156-bcb4-1f2a19bcdf9e', 'github', 'SLUG', '/fixture-dev/.*', 'REGEX', 'REVIEW'),
('0c3e222b-1678-462d-8d5a-d89d7d053e15', '87b603eb-ae17-4f8a-8d41-d8993bb8f179', 'gitlab', 'SLUG', '/fixture-dev/*', 'GLOB', 'PUSH');
CREATE INDEX "PUBLIC"."IDX_GROUP_PERMISSIONS_GROUP_ID" ON "PUBLIC"."GROUP_PERMISSIONS"("GROUP_ID" NULLS FIRST);
CREATE INDEX "PUBLIC"."IDX_GROUP_PERMISSIONS_PROVIDER" ON "PUBLIC"."GROUP_PERMISSIONS"("PROVIDER" NULLS FIRST);
CREATE CACHED TABLE "PUBLIC"."GROUP_MEMBERS"(
    "GROUP_ID" CHARACTER VARYING(36) NOT NULL,
    "USERNAME" CHARACTER VARYING(255) NOT NULL
); 
ALTER TABLE "PUBLIC"."GROUP_MEMBERS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F180" PRIMARY KEY("GROUP_ID", "USERNAME");            
-- 3 +/- SELECT COUNT(*) FROM PUBLIC.GROUP_MEMBERS;            
INSERT INTO "PUBLIC"."GROUP_MEMBERS" VALUES
('45c7db17-6786-4156-bcb4-1f2a19bcdf9e', 'reviewer'),
('45c7db17-6786-4156-bcb4-1f2a19bcdf9e', 'admin'),
('87b603eb-ae17-4f8a-8d41-d8993bb8f179', 'dev');          
CREATE INDEX "PUBLIC"."IDX_GROUP_MEMBERS_USERNAME" ON "PUBLIC"."GROUP_MEMBERS"("USERNAME" NULLS FIRST);        
CREATE CACHED TABLE "PUBLIC"."USER_SCM_TOKENS"(
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "ACCESS_TOKEN" BINARY VARYING NOT NULL,
    "REFRESH_TOKEN" BINARY VARYING,
    "SCOPES" CHARACTER VARYING(512),
    "EXPIRES_AT" TIMESTAMP,
    "AUTHORIZED_AT" TIMESTAMP NOT NULL
);     
ALTER TABLE "PUBLIC"."USER_SCM_TOKENS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_878" PRIMARY KEY("USERNAME", "PROVIDER");           
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.USER_SCM_TOKENS;          
CREATE CACHED TABLE "PUBLIC"."EMAIL_SOURCES"(
    "USERNAME" CHARACTER VARYING(255) NOT NULL,
    "EMAIL" CHARACTER VARYING(255) NOT NULL,
    "AUTH_SOURCE" CHARACTER VARYING(20) NOT NULL
); 
ALTER TABLE "PUBLIC"."EMAIL_SOURCES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_53" PRIMARY KEY("USERNAME", "EMAIL", "AUTH_SOURCE");  
-- 7 +/- SELECT COUNT(*) FROM PUBLIC.EMAIL_SOURCES;            
INSERT INTO "PUBLIC"."EMAIL_SOURCES" VALUES
('dev', '00000000+fixture-dev@users.noreply.github.com', 'github'),
('dev', 'fixture-extra-1@example.com', 'github'),
('dev', 'fixture-dev@example.com', 'github'),
('dev', 'fixture-extra-2@example.com', 'github'),
('dev', 'fixture-alt@example.com', 'github'),
('dev', 'fixture-alt@example.com', 'gitlab'),
('dev', 'fixture-extra-2@example.com', 'gitlab');          
CREATE CACHED TABLE "PUBLIC"."PUSH_COMMITS"(
    "PUSH_ID" CHARACTER VARYING(36) NOT NULL,
    "SHA" CHARACTER VARYING(40) NOT NULL,
    "PARENT_SHA" CHARACTER VARYING(40),
    "AUTHOR_NAME" CHARACTER VARYING(255),
    "AUTHOR_EMAIL" CHARACTER VARYING(255),
    "COMMITTER_NAME" CHARACTER VARYING(255),
    "COMMITTER_EMAIL" CHARACTER VARYING(255),
    "MESSAGE" CHARACTER VARYING,
    "COMMIT_DATE" TIMESTAMP,
    "SIGNATURE" CHARACTER VARYING,
    "SIGNED_OFF_BY" CHARACTER VARYING,
    "CO_AUTHORED_BY" CHARACTER VARYING
); 
ALTER TABLE "PUBLIC"."PUSH_COMMITS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_AE" PRIMARY KEY("PUSH_ID", "SHA");     
-- 28 +/- SELECT COUNT(*) FROM PUBLIC.PUSH_COMMITS;            
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('42a89c6b-009b-488d-a6df-e13166043608', '32dac5f8468842f9a9ce0b3de761b44177784e0e', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'noreply@example.com', 'Fixture Developer', 'noreply@example.com', U&'feat: this commit has a noreply author\000a\000aSigned-off-by: Fixture Developer <noreply@example.com>\000a', TIMESTAMP '2026-09-08 13:13:41', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCRQAKCRA3V+o4iAJ3\000a5714AQCBPKkorXWSCCQNrRG/yclqjeLd4v5e6DR9q/uAnxA4XQD/YBYpdR60+z4I\000aCgx3C6SOErtAEsfEgVaSeqUAoX0zhQA=\000a=5OIS\000a-----END PGP SIGNATURE-----', 'Fixture Developer <noreply@example.com>', NULL),
('3f50e643-3734-4189-98bc-e8cc27fe5ba1', '2d890c0a726f4b8deb90c4c6e0f69acad4f93bff', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'developer@internal.corp.net', 'Fixture Developer', 'developer@internal.corp.net', U&'feat: this commit comes from an unapproved domain\000a\000aSigned-off-by: Fixture Developer <developer@internal.corp.net>\000a', TIMESTAMP '2026-09-08 13:13:44', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCSAAKCRA3V+o4iAJ3\000a5/GXAP9aftFyMoFA5HW5fws1S6V1MOB/KW6y81sXBuaepEyqOgD/Zoja/Dk4WnaE\000aGasgnInmW3ldnWBN2tM37Gs83SvBAQQ=\000a=TtqW\000a-----END PGP SIGNATURE-----', 'Fixture Developer <developer@internal.corp.net>', NULL),
('03d80189-5127-49a9-b4f7-0e591511ed8a', '27cb677ac6493c9e47f318cb59018496d67c5ac4', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'WIP: still working on this feature\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:13:46', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCSgAKCRA3V+o4iAJ3\000a5w0tAP92qEJy7sg+0eKl9Kx0jwIsa4hB55EjYb/FsP2EZaiPAgEAw+1bQf2uAvxG\000asP3lyA6J+fvtuyyrSEF6j5TssjfmcAM=\000a=7TEo\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('acb0963c-42fb-455f-870f-64cfc8c7f0b2', '25ffe92c984225cdbc51009feeb7259196dc57b3', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: rotate token=[REDACTED] in CI config\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:13:47', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCSwAKCRA3V+o4iAJ3\000a5w9YAQDsjW0SH5onVLCKCQNEQA024pg0zWRQnJS4QM4Io/JvmwD/SB1FOWDp+a6Q\000ahqOoVdom0v25SWs/uj4gUTO/rbkRHgQ=\000a=xn1f\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('a31078aa-e342-4a3b-bba0-f877ff5bd61a', '8801e9cba03fd4e2b635290ecea85d7156bcda6e', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add deployment credentials\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:13:49', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCTQAKCRA3V+o4iAJ3\000a519JAQCRg40pjjeLl7FFGKzJcbt+zDZJWcrKTYsBokrdXcoKTgEArHlrqxB57czy\000aH81DHnysgU8FbEqAvCT6ru5hLNLb3Qw=\000a=bATc\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('71bcc23b-4525-4456-92bc-7e8733b5bb2f', '16d53f0b0f328954f0f513acb0c809e97daa5d87', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add upstream config\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:13:51', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCTwAKCRA3V+o4iAJ3\000a5yUoAP9wv5oDzfGLCXQTqqbYtwuq/tPXdXqSo+EYdPANHThP3wD/cp6mSzN3F+Dh\000a4MrtdSgFX0SXVrrsi11JyVcp8oqH2wc=\000a=2yhT\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('ad381ddb-55a4-4853-bee4-5fb273a343aa', '49d17b15216699672ade6f11c87377f1cf9a4f0e', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add deployment script\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:13:53', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCUQAKCRA3V+o4iAJ3\000a50h+AP48CDhc7YrWhH/SD7iDwQaPrv9KUhpLxSXMeFnFDv53WQD7BVeHie4/hgFO\000aZEHyPj0njjqGBuduMLxw59GGlCcaCwU=\000a=8nnH\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);          
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('0489fd19-40a0-4aed-96ae-394780b1b51d', 'be9750a4ff5798fb660755d1759da892a6b50845', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: forgot to sign off\000a', TIMESTAMP '2026-09-08 13:13:54', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCUgAKCRA3V+o4iAJ3\000a5z0WAQDn5boAxjE7Zt1IGBSt40pFzjSvLDz3+s0sswqwohP+WgD7Bf3O5cPdyIax\000aT4eTLVOstxwNk9aX9G9u+1l6/qV5oQs=\000a=6YGm\000a-----END PGP SIGNATURE-----', NULL, NULL),
('b6476888-7a69-4906-8079-5c0a88a9b34c', '54be71ff722464a757f0a34b76dd315701e9d07c', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: signed off by the wrong person\000a\000aSigned-off-by: Someone Else <someone.else@example.com>\000a', TIMESTAMP '2026-09-08 13:13:56', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCVAAKCRA3V+o4iAJ3\000a5+voAP9NWZ7I0u0Cw6/dRDCxZzlIaZBeLDOeNmpcCV0V5tXvbwD+Ma1Rrag5Wj6w\000aLDu8rww6ON4gAUYCRfAPfQy2w1efBQg=\000a=B2hL\000a-----END PGP SIGNATURE-----', 'Someone Else <someone.else@example.com>', NULL),
('0d985749-7e23-41a2-b992-be254742fae0', '77ffb76ca65fd610262c71508229c9b6643dfef3', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: paired with an outside contractor\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000aCo-authored-by: Contractor <contractor@outside.example.net>\000a', TIMESTAMP '2026-09-08 13:13:58', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCVgAKCRA3V+o4iAJ3\000a59GQAQCiTvHNMeW/IH/WhnNiCGy/njo9Av5PqNDyNBfpOtUa5AEA9eOv5POMU2Ik\000adg6nU6VrU95tVDBlEIBBeAdHtFuOjwQ=\000a=/A3x\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', 'Contractor <contractor@outside.example.net>'),
('59f76466-cee9-48d4-bac5-9dc955575a79', '4bd51d66b5f8cefd3831fff9030069a6aeaac972', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: pair-programmed with an allow-listed co-author\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000aCo-authored-by: Claude <noreply@anthropic.com>\000aCo-authored-by: Pair Partner <pair@example.com>\000a', TIMESTAMP '2026-09-08 13:13:59', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCVwAKCRA3V+o4iAJ3\000a57igAP0SsfAWIq+4SiYFZbnYXYyuIidsjITGIGO+ucsVEOvNVQEAktL624ynauqM\000aDGntsa0g6Y+T6WPia7U3ZQem8SdiaQw=\000a=Wd5f\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', U&'Claude <noreply@anthropic.com>\000aPair Partner <pair@example.com>'),
('ed117cf9-0789-4341-9561-cf551eff7d42', '807e55ad96e8fe485489f4726ffdc49ae75d637c', '09b048f2d69dc7218dbe7eab5bde0b13cca29d0d', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 6 \2014 missing DCO sign-off\000a', TIMESTAMP '2026-09-08 13:14:01', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWQAKCRA3V+o4iAJ3\000a50H1AQC3mo0mNfHTnAsLFniN4llVluXAsrsQEfLkjqV0hCb/JAD/eg3WNJ7/tKI9\000a9uDIEmctbph0AA1nOJK6EkSu3xbnyAA=\000a=9egg\000a-----END PGP SIGNATURE-----', NULL, NULL),
('ed117cf9-0789-4341-9561-cf551eff7d42', '09b048f2d69dc7218dbe7eab5bde0b13cca29d0d', '255629fd85330484863ef76a4793fdda0260548a', 'Fixture Developer', 'unregistered@example.com', 'Fixture Developer', 'unregistered@example.com', U&'test: commit 5 \2014 unregistered commit email\000a\000aSigned-off-by: Fixture Developer <unregistered@example.com>\000a', TIMESTAMP '2026-09-08 13:14:01', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWQAKCRA3V+o4iAJ3\000a58+rAQCyyoWCXmoKuXMhmDdsnWeaBf0h5JmOBFZNdCsIE3OSDgEArLR6p+ceEx7A\000aesAi8mfBVhc1WbjsdPshLHpAGOh5Ygs=\000a=Lj3f\000a-----END PGP SIGNATURE-----', 'Fixture Developer <unregistered@example.com>', NULL);       
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('ed117cf9-0789-4341-9561-cf551eff7d42', '255629fd85330484863ef76a4793fdda0260548a', '51eaa604f44000a6adfd6f4b3a655a3cfd6da5ea', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 4 \2014 blocked hostname in diff\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:01', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWQAKCRA3V+o4iAJ3\000a5z4YAQDZYMxCmoCuDyzSu6+2slH8oMY4BKlGFXWOhoPKoRIsZgD+Pdtk2kBug0vB\000aBx5tzEzs0ZqZ48ttggw0oBLbxhMJJQo=\000a=ymnX\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('ed117cf9-0789-4341-9561-cf551eff7d42', '51eaa604f44000a6adfd6f4b3a655a3cfd6da5ea', '02f29a5dbc9d55db92560fadfd8efcd2862d2d56', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 3 \2014 github pat in diff\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:01', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWQAKCRA3V+o4iAJ3\000a5+cpAQD6TgZyOD07hZphwrN681Hs1SmI8WTDQDHG7yRc1yu3aQD/SdrD9YpszR7J\000alERUiq30nqrlsLfv+Vzs3gRRByxamQA=\000a=267s\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('ed117cf9-0789-4341-9561-cf551eff7d42', '02f29a5dbc9d55db92560fadfd8efcd2862d2d56', 'd67604644266556f2b42b7811cd2d8bf66433ac9', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'WIP: commit 2 \2014 bad commit message\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:01', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWQAKCRA3V+o4iAJ3\000a5yyuAPwN8TkMJvSDo5Xw6pR5MzUT/hk5ih2Se7kBytVyO479mwD7B7KAo5ozOarL\000aQMByAmgdaYVsN/vhB+kcNBzY9IMBwA0=\000a=UkLX\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('ed117cf9-0789-4341-9561-cf551eff7d42', 'd67604644266556f2b42b7811cd2d8bf66433ac9', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'noreply@example.com', 'Fixture Developer', 'noreply@example.com', U&'test: commit 1 \2014 noreply author email\000a\000aSigned-off-by: Fixture Developer <noreply@example.com>\000a', TIMESTAMP '2026-09-08 13:14:01', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWQAKCRA3V+o4iAJ3\000a5+X9AP4kwclVAoKyyW/FFtDq07sPNoMB0mZrBI2BdMCZVXsDKgEA7YlINBz6wjyu\000awn3FV8nJ+6nRkdkEnX28KZduMSvFcwo=\000a=0/3J\000a-----END PGP SIGNATURE-----', 'Fixture Developer <noreply@example.com>', NULL),
('035b2f93-5eb4-46b2-a916-213f5943e2f6', 'aca8eb091d41eb1a10fe22e537e2d5995b6f232e', 'a8a9137892c26e8ede72adc3292c456c98e571fc', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: identity resolution \2014 codeberg unresolved\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:03', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCWwAKCRA3V+o4iAJ3\000a53r7AP4/NiK8mIfRieIRm22VWU7KcrdVLuuLDwoMHbDm9LGXUwEA/Dg6vZICoXCd\000a6H1b452L9C58r8IdSWcIlP+xnp8hogU=\000a=oQsg\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('0f36835e-186b-4729-955d-5deac7a5b658', '051becec3708b748bd374994b66c0432b4e0da6f', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: add release notes stub\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:06', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCXgAKCRA3V+o4iAJ3\000a568tAP9e/sJobkOgU6FXATYV9YyUvGOu2t+/o6WKE/RYlFgbRAEAlwtj9pkNqY6r\000aw0dy5RmSXYSs7/GBqBst9yKivmQ7FQQ=\000a=dijt\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('14396ba7-ccde-4191-a213-c7b0c891b680', 'b0a09aaef017063e4ac29cbde621c8a7f4417edd', 'db04347f994c51c3d9c2395db0a27ce8e5b9dbfd', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: touch readme via fogwall\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:10', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCYgAKCRA3V+o4iAJ3\000a58WcAP9ciHpXMTv1he/afK7aYwUpnuE2vOxGOwbY3Fai7jkcrQD/eAasr7x1qEKI\000aVPm7eLPxYJnOMARRhJ8wYWhiEg+LkgM=\000a=wM+F\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);             
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('13c11e61-6ce9-4f71-8c95-6b34a08f3f97', '9903d7974c466c216109dd743cbe00381742c491', 'dacd8fbfbda0ede99f37aca894d964e87bd60573', 'Fixture Developer', 'unregistered@example.com', 'Fixture Developer', 'unregistered@example.com', U&'test: identity resolution \2014 gitlab resolved, email unregistered\000a\000aSigned-off-by: Fixture Developer <unregistered@example.com>\000a', TIMESTAMP '2026-09-08 13:14:14', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCZgAKCRA3V+o4iAJ3\000a500bAQD/5GXPbM4aP1b6f9XASty38W+kar9Lt1QxGaVdMXO0LAD+Ma63O4IcPzsY\000anEDKq5QjMCMkpX9fDN3ZCoZ+ItJqpAA=\000a=6J7X\000a-----END PGP SIGNATURE-----', 'Fixture Developer <unregistered@example.com>', NULL),
('2e35354b-3201-4b86-afff-50e7eca07dbb', 'f0fdc1f79e410b52bf4304d231b13df2df500272', 'ddffb0e23f501a79a0f92c31d02edcbbc803ce1b', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'fix(gamma): third of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:17', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCaQAKCRA3V+o4iAJ3\000a54TvAQCSHgmgqlGHZKZqb3t9S60aeh75BWSuVlTTePigKaUOOQD+Nz8+miNilxrJ\000au9mGAEy9/mUnTr9qSgDyTq/rGp3D4Qc=\000a=czxg\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('2e35354b-3201-4b86-afff-50e7eca07dbb', 'ddffb0e23f501a79a0f92c31d02edcbbc803ce1b', '44a0afc1a183212fd12181c10bf8dedb3343cef1', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat(beta): second of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:17', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCaQAKCRA3V+o4iAJ3\000a53PdAQCy+dEDmPK1UOy789Uc7RT38JsOmyVs/EeFT+O3dSWNAQD+Ku5V35ujwWhk\000aYHhC/P0YLkPtY4IG0a5RL2MvhGmPmgk=\000a=Athw\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('2e35354b-3201-4b86-afff-50e7eca07dbb', '44a0afc1a183212fd12181c10bf8dedb3343cef1', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat(alpha): first of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:17', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCaQAKCRA3V+o4iAJ3\000a51quAP4zwOfXHnhBS0lcufggcZtjKe4kPTAYrt7KZltdT77EKQEAkwv2J/v3CONm\000ahU1vGRd4IbNshMFImgdSSuInr3SUSgI=\000a=Zvmj\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('6c0a3ec7-a4d8-48ee-8f54-269f6f644533', '4b67355311ecce88c39b7a719f7fc34d3a485689', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: enable experimental flag\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:21', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCbQAKCRA3V+o4iAJ3\000a55zvAP963gEyNJl0BfYzXB0BvD3ws2bhMeUBJTXvVRK2aWKNFQD+ImzM8EtgWiwe\000a69wkubbjL4cej+4gj396Jb3uzR/3MAw=\000a=1pvm\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('24c32427-cee8-42ff-84c8-9918b01cc788', '7f370e25726e8fc6ac14d09ae8c614e3b258382b', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: exploratory change, withdrawn\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:23', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCbwAKCRA3V+o4iAJ3\000a589XAP9cuV1vaAAE2OHmypFBw8k+ebo/sB4H5krnBFs1hHrEvwEAwGugaPINZdrj\000auW6HDk7kFGfd2z93FXZizZN2yljnmAY=\000a=rexN\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('995e7946-c575-4de9-a0cc-6e3f8c0833ff', '6b879777c725c7d6bd8f3d003acf1665092b273d', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: answer the most common question\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:25', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCcQAKCRA3V+o4iAJ3\000a5yxZAP94/7RNCrok7tYwpsF9VOQXfMNB2P5OCS8mqs1o8DLhEwEA+0sHlxAX3XTf\000a2bKWrhafJghXQoIZ3k97V6gzkc9bsAY=\000a=2a3V\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);               
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('f1c0a843-a5e9-4636-8357-7b5d50fd8190', '08af8057382e3da737b1a97118b415301ff7485f', '19f581f3cdbd01f8697953b14e0c93ea82a0df70', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: pushed over the SSH transport\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-08 13:14:27', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqBCcwAKCRA3V+o4iAJ3\000a51NmAP49CvShi1fqDLs9RYnLDU4XsBXxfqzdooJOzA6sNKgA/wEAnhN4lr+gou8u\000a3Jd24oAe0DdzRmx1pnnkkFb607C9SAU=\000a=3CTB\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);  
CREATE INDEX "PUBLIC"."IDX_PUSH_COMMITS_PUSH_ID" ON "PUBLIC"."PUSH_COMMITS"("PUSH_ID" NULLS FIRST);            
CREATE CACHED TABLE "PUBLIC"."SCM_API_ACTION_RECORDS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "TIMESTAMP" TIMESTAMP NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "SCM_USERNAME" CHARACTER VARYING(255),
    "RESOLVED_USER" CHARACTER VARYING(255),
    "REPO_OWNER" CHARACTER VARYING(255),
    "REPO_NAME" CHARACTER VARYING(255),
    "MUTATION_FIELD" CHARACTER VARYING(100),
    "NODE_ID" CHARACTER VARYING(255),
    "NODE_TYPE" CHARACTER VARYING(30),
    "STATUS" CHARACTER VARYING(20) NOT NULL,
    "REASON" CHARACTER VARYING,
    "VARIABLES_JSON" CHARACTER VARYING,
    "USER_AGENT" CHARACTER VARYING(512),
    "CLIENT_TYPE" CHARACTER VARYING(32),
    "CLIENT_VERSION" CHARACTER VARYING(64),
    "UPSTREAM_STATUS" INTEGER,
    "PROPOSAL_ID" CHARACTER VARYING(36)
);             
ALTER TABLE "PUBLIC"."SCM_API_ACTION_RECORDS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F8" PRIMARY KEY("ID");       
-- 28 +/- SELECT COUNT(*) FROM PUBLIC.SCM_API_ACTION_RECORDS;  
INSERT INTO "PUBLIC"."SCM_API_ACTION_RECORDS" VALUES
('3bbc245b-e740-4ac9-b8a5-a34db0a764c4', TIMESTAMP '2026-09-08 13:16:11.360002', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'createPullRequest', 'R_kgDOUSmvJw', 'REPOSITORY', 'FORWARDED', NULL, '{"input":{"baseRefName":"main","body":"Opened through fogwall with gh.","draft":false,"headRefName":"fixture/gh-766404","maintainerCanModify":true,"repositoryId":"R_kgDOUSmvJw","title":"Proposed via gh"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'c7d34fc9-f462-4e2a-945c-7ffb098d05f0'),
('cfe7d449-a86a-4bdc-bb64-5dbed99bf0df', TIMESTAMP '2026-09-08 13:16:13.497576', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'updatePullRequest', 'PR_kwDOUSmvJ88AAAABCtZgQA', 'PULL_REQUEST', 'FORWARDED', NULL, '{"input":{"pullRequestId":"PR_kwDOUSmvJ88AAAABCtZgQA","title":"Proposed via gh (edited)"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'c7d34fc9-f462-4e2a-945c-7ffb098d05f0'),
('8b69fb57-f676-48c5-ac54-74429e53a361', TIMESTAMP '2026-09-08 13:16:15.102325', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'closePullRequest', 'PR_kwDOUSmvJ88AAAABCtZgQA', 'PULL_REQUEST', 'FORWARDED', NULL, '{"input":{"pullRequestId":"PR_kwDOUSmvJ88AAAABCtZgQA"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'c7d34fc9-f462-4e2a-945c-7ffb098d05f0'),
('45d4a123-e75a-4453-bfa6-feee2997b55b', TIMESTAMP '2026-09-08 13:16:16.54716', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'createIssue', 'R_kgDOUSmvJw', 'REPOSITORY', 'FORWARDED', NULL, '{"input":{"body":"Opened through fogwall with gh.","repositoryId":"R_kgDOUSmvJw","title":"Reported via gh"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'c9f70153-26b4-420e-a7dc-bb94245a2fd1'),
('95b39f2d-fe60-44bc-aefb-b3de333277ac', TIMESTAMP '2026-09-08 13:16:18.380017', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'updateIssue', 'I_kwDOUSmvJ88AAAABQUGLYg', 'ISSUE', 'FORWARDED', NULL, '{"input":{"id":"I_kwDOUSmvJ88AAAABQUGLYg","body":"Edited through fogwall with gh."}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'c9f70153-26b4-420e-a7dc-bb94245a2fd1'),
('a7d9dffe-b246-4cff-a478-85077c7e7c10', TIMESTAMP '2026-09-08 13:16:20.02983', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'closeIssue', 'I_kwDOUSmvJ88AAAABQUGLYg', 'ISSUE', 'FORWARDED', NULL, '{"input":{"issueId":"I_kwDOUSmvJ88AAAABQUGLYg"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'c9f70153-26b4-420e-a7dc-bb94245a2fd1'),
('8c7daee9-9fec-4c9b-a926-bf571148a6a0', TIMESTAMP '2026-09-08 13:16:20.744979', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'createIssue', 'R_kgDOUSmvJw', 'REPOSITORY', 'REJECTED', U&'Content rejected: secret detected in proposal content: [aws-access-token]\000a  match:  [REDACTED]; secret detected in proposal content: [generic-api-key]\000a  match:  aws_secret_access_key = [REDACTED]"; secret detected in proposal content: [generic-api-key]\000a  match:  aws_secret_access_key = [REDACTED]', NULL, 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', NULL, NULL),
('3730409a-0b62-4a43-aee3-4ba7cb745b55', TIMESTAMP '2026-09-08 13:16:27.74611', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'merge_requests.create', NULL, NULL, 'FORWARDED', NULL, '{"title":"Proposed via glab","description":"Opened through fogwall with glab.","source_branch":"fixture/glab-780752","target_branch":"main","labels":"","target_project_id":86231428}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 201, 'e95ee459-ede1-42e5-976b-5543af5734e7'),
('b05253dd-a84a-4c90-b3d9-595c992b00bc', TIMESTAMP '2026-09-08 13:16:29.131553', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'merge_requests.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"Proposed via glab (edited)"}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, 'e95ee459-ede1-42e5-976b-5543af5734e7'),
('6e5d1618-b636-4edb-9c28-ff1bc6b6f93a', TIMESTAMP '2026-09-08 13:16:30.741568', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'merge_requests.update', NULL, NULL, 'FORWARDED', NULL, '{"state_event":"close"}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, 'e95ee459-ede1-42e5-976b-5543af5734e7');
INSERT INTO "PUBLIC"."SCM_API_ACTION_RECORDS" VALUES
('873311f2-1e30-4f7b-8a13-d32ce779a44b', TIMESTAMP '2026-09-08 13:16:32.40892', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'FORWARDED', NULL, '{"title":"Reported via glab","description":"Opened through fogwall with glab.","labels":""}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 201, '32f0d555-d05a-46db-8b50-c79844e993f8'),
('116811ba-bb0d-498d-bf16-7e09b7eedfab', TIMESTAMP '2026-09-08 13:16:33.820568', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"description":"Edited through fogwall with glab."}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, '32f0d555-d05a-46db-8b50-c79844e993f8'),
('b36f4383-2e8f-48e7-b8a1-c8e7d9b42770', TIMESTAMP '2026-09-08 13:16:35.479809', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"state_event":"close"}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, '32f0d555-d05a-46db-8b50-c79844e993f8'),
('c332276e-8594-4302-84fa-c7fc99b5201a', TIMESTAMP '2026-09-08 13:16:36.214027', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'REJECTED', 'Content rejected: blocked term: "internal.corp.example.com" in description', NULL, 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', NULL, NULL),
('47a73540-0f1f-41f1-bad8-becd870fb835', TIMESTAMP '2026-09-08 13:16:49.240231', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.create', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"base":"main","body":"Opened through fogwall with fj.","due_date":null,"head":"fixture/fj-796225","labels":null,"milestone":null,"title":"Proposed via fj"}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, '1f86a03b-241e-493d-bf72-26a9c6505edb'),
('f002df37-667e-49f7-acb6-8481f10203dc', TIMESTAMP '2026-09-08 13:16:50.02148', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":null,"due_date":null,"milestone":null,"ref":null,"state":null,"title":"Proposed via fj (edited)","unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, '1f86a03b-241e-493d-bf72-26a9c6505edb'),
('190a9a75-abe6-411e-a6ef-1cb1d04c706c', TIMESTAMP '2026-09-08 13:16:50.943015', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":null,"due_date":null,"milestone":null,"ref":null,"state":"closed","title":null,"unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, '1f86a03b-241e-493d-bf72-26a9c6505edb'),
('9b0daed8-b180-44b5-b74f-2cef5768eb33', TIMESTAMP '2026-09-08 13:16:51.947714', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":"Opened through fogwall with fj.","closed":null,"due_date":null,"labels":null,"milestone":null,"ref":null,"title":"Reported via fj"}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, 'a443fa75-abbf-453e-89fa-578e0cacfdba'),
('65cf03d2-799d-468d-98af-9446a52e4bbb', TIMESTAMP '2026-09-08 13:16:52.583388', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":"Edited through fogwall with fj.","due_date":null,"milestone":null,"ref":null,"state":null,"title":null,"unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, 'a443fa75-abbf-453e-89fa-578e0cacfdba');        
INSERT INTO "PUBLIC"."SCM_API_ACTION_RECORDS" VALUES
('c320eda2-8b1d-465b-a852-706f4c6c86a3', TIMESTAMP '2026-09-08 13:16:53.296477', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":null,"due_date":null,"milestone":null,"ref":null,"state":"closed","title":null,"unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, 'a443fa75-abbf-453e-89fa-578e0cacfdba'),
('59ea1044-113b-434a-b54d-16e5a41d62e9', TIMESTAMP '2026-09-08 13:16:54.074182', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'REJECTED', 'Content rejected: possible IBAN (Generic) detected in proposal content', NULL, 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', NULL, NULL),
('7f264e7f-4143-46b3-bb02-886b5fa30ca0', TIMESTAMP '2026-09-08 13:16:57.439151', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.create', NULL, NULL, 'FORWARDED', NULL, '{"head":"fixture/tea-814079","base":"main","title":"Proposed via tea","body":"Opened through fogwall with tea.","assignee":"","assignees":[""],"reviewers":null,"team_reviewers":null,"milestone":0,"labels":[],"due_date":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, '3ea13635-a238-4f33-894b-5f70f7f4b689'),
('699d2f15-c943-4b24-9a62-a49830a3351d', TIMESTAMP '2026-09-08 13:16:58.04804', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"Proposed via tea (edited)","body":null,"base":"","assignee":"","assignees":null,"milestone":0,"labels":null,"state":null,"due_date":null,"unset_due_date":null,"allow_maintainer_edit":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, '3ea13635-a238-4f33-894b-5f70f7f4b689'),
('70174e30-05f1-46e2-94fc-d063dc824417', TIMESTAMP '2026-09-08 13:16:58.81753', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"","body":null,"base":"","assignee":"","assignees":null,"milestone":0,"labels":null,"state":"closed","due_date":null,"unset_due_date":null,"allow_maintainer_edit":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, '3ea13635-a238-4f33-894b-5f70f7f4b689'),
('8df35566-b35f-4a39-9a1a-505a821e4b7a', TIMESTAMP '2026-09-08 13:16:59.851595', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'FORWARDED', NULL, '{"title":"Reported via tea","body":"Opened through fogwall with tea.","ref":"","assignees":[""],"due_date":null,"milestone":0,"labels":[],"closed":false}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, 'db8281c1-2135-473f-a803-f6fb71c4c811'),
('fb605466-3f81-4b96-ad34-78f3363932fe', TIMESTAMP '2026-09-08 13:17:00.536864', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"","body":"Edited through fogwall with tea.","ref":null,"assignees":null,"milestone":null,"state":null,"due_date":null,"unset_due_date":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, 'db8281c1-2135-473f-a803-f6fb71c4c811'),
('86500a95-4230-4c98-8149-b609b3cc4043', TIMESTAMP '2026-09-08 13:17:01.35802', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"","body":null,"ref":null,"assignees":null,"milestone":null,"state":"closed","due_date":null,"unset_due_date":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, 'db8281c1-2135-473f-a803-f6fb71c4c811'),
('83b466ae-b5dc-41e1-93bd-cd9522164320', TIMESTAMP '2026-09-08 13:17:02.07967', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'REJECTED', 'Content rejected: blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in body', NULL, 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', NULL, NULL);     
CREATE INDEX "PUBLIC"."IDX_SCM_API_ACTION_RECORDS_RESOLVED_USER" ON "PUBLIC"."SCM_API_ACTION_RECORDS"("RESOLVED_USER" NULLS FIRST);            
CREATE INDEX "PUBLIC"."IDX_SCM_API_ACTION_RECORDS_TIMESTAMP" ON "PUBLIC"."SCM_API_ACTION_RECORDS"("TIMESTAMP" NULLS FIRST);    
CREATE CACHED TABLE "PUBLIC"."SCM_API_PROPOSALS"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "REPO_OWNER" CHARACTER VARYING(255) NOT NULL,
    "REPO_NAME" CHARACTER VARYING(255) NOT NULL,
    "KIND" CHARACTER VARYING(20) NOT NULL,
    "PROPOSAL_NUMBER" INTEGER NOT NULL,
    "URL" CHARACTER VARYING(1024),
    "NODE_ID" CHARACTER VARYING(255),
    "TITLE" CHARACTER VARYING(1024),
    "STATE" CHARACTER VARYING(20) NOT NULL,
    "CREATED_BY" CHARACTER VARYING(255),
    "CREATED_BY_SCM_USERNAME" CHARACTER VARYING(255),
    "CREATED_AT" TIMESTAMP NOT NULL,
    "UPDATED_AT" TIMESTAMP NOT NULL,
    "CREATED_ACTION_ID" CHARACTER VARYING(36),
    "LAST_ACTION_ID" CHARACTER VARYING(36)
);               
ALTER TABLE "PUBLIC"."SCM_API_PROPOSALS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_9" PRIMARY KEY("ID");             
-- 8 +/- SELECT COUNT(*) FROM PUBLIC.SCM_API_PROPOSALS;        
INSERT INTO "PUBLIC"."SCM_API_PROPOSALS" VALUES
('c7d34fc9-f462-4e2a-945c-7ffb098d05f0', 'github', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://github.com/fixture-dev/fogwall-fixture/pull/1', 'PR_kwDOUSmvJ88AAAABCtZgQA', 'Proposed via gh (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:11.359024', TIMESTAMP '2026-09-08 13:16:15.101549', '3bbc245b-e740-4ac9-b8a5-a34db0a764c4', '8b69fb57-f676-48c5-ac54-74429e53a361'),
('c9f70153-26b4-420e-a7dc-bb94245a2fd1', 'github', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 2, 'https://github.com/fixture-dev/fogwall-fixture/issues/2', 'I_kwDOUSmvJ88AAAABQUGLYg', 'Reported via gh', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:16.546724', TIMESTAMP '2026-09-08 13:16:20.029217', '45d4a123-e75a-4453-bfa6-feee2997b55b', 'a7d9dffe-b246-4cff-a478-85077c7e7c10'),
('e95ee459-ede1-42e5-976b-5543af5734e7', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://gitlab.com/fixture-dev/fogwall-fixture/-/merge_requests/1', NULL, 'Proposed via glab (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:27.745273', TIMESTAMP '2026-09-08 13:16:30.740696', '3730409a-0b62-4a43-aee3-4ba7cb745b55', '6e5d1618-b636-4edb-9c28-ff1bc6b6f93a'),
('32f0d555-d05a-46db-8b50-c79844e993f8', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 1, 'https://gitlab.com/fixture-dev/fogwall-fixture/-/work_items/1', NULL, 'Reported via glab', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:32.408238', TIMESTAMP '2026-09-08 13:16:35.479296', '873311f2-1e30-4f7b-8a13-d32ce779a44b', 'b36f4383-2e8f-48e7-b8a1-c8e7d9b42770'),
('1f86a03b-241e-493d-bf72-26a9c6505edb', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://codeberg.org/fixture-dev/fogwall-fixture/pulls/1', NULL, 'Proposed via fj (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:49.239589', TIMESTAMP '2026-09-08 13:16:50.942526', '47a73540-0f1f-41f1-bad8-becd870fb835', '190a9a75-abe6-411e-a6ef-1cb1d04c706c'),
('a443fa75-abbf-453e-89fa-578e0cacfdba', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 2, 'https://codeberg.org/fixture-dev/fogwall-fixture/issues/2', NULL, 'Reported via fj', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:51.946858', TIMESTAMP '2026-09-08 13:16:53.296228', '9b0daed8-b180-44b5-b74f-2cef5768eb33', 'c320eda2-8b1d-465b-a852-706f4c6c86a3'),
('3ea13635-a238-4f33-894b-5f70f7f4b689', 'gitea', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://gitea.com/fixture-dev/fogwall-fixture/pulls/1', NULL, 'Proposed via tea (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:57.438761', TIMESTAMP '2026-09-08 13:16:58.817048', '7f264e7f-4143-46b3-bb02-886b5fa30ca0', '70174e30-05f1-46e2-94fc-d063dc824417'),
('db8281c1-2135-473f-a803-f6fb71c4c811', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 2, 'https://gitea.com/fixture-dev/fogwall-fixture/issues/2', NULL, 'Reported via tea', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-08 13:16:59.851135', TIMESTAMP '2026-09-08 13:17:01.357378', '8df35566-b35f-4a39-9a1a-505a821e4b7a', '86500a95-4230-4c98-8149-b609b3cc4043');             
CREATE INDEX "PUBLIC"."IDX_SCM_API_PROPOSALS_NODE_ID" ON "PUBLIC"."SCM_API_PROPOSALS"("PROVIDER" NULLS FIRST, "NODE_ID" NULLS FIRST);          
CREATE CACHED TABLE "PUBLIC"."SCM_TOKEN_CACHE"(
    "TOKEN_HASH" CHARACTER VARYING(128) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "PROXY_USERNAME" CHARACTER VARYING(255) NOT NULL,
    "CACHED_AT" TIMESTAMP NOT NULL,
    "SCM_LOGIN" CHARACTER VARYING(255)
);          
ALTER TABLE "PUBLIC"."SCM_TOKEN_CACHE" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_49" PRIMARY KEY("TOKEN_HASH", "PROVIDER");          
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.SCM_TOKEN_CACHE;          
ALTER TABLE "PUBLIC"."USER_EMAILS" ADD CONSTRAINT "PUBLIC"."UQ_USER_EMAILS_EMAIL" UNIQUE NULLS DISTINCT ("EMAIL");             
ALTER TABLE "PUBLIC"."USER_SCM_IDENTITIES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_50C" UNIQUE NULLS DISTINCT ("PROVIDER", "SCM_USERNAME");        
ALTER TABLE "PUBLIC"."SCM_API_PROPOSALS" ADD CONSTRAINT "PUBLIC"."UQ_SCM_API_PROPOSALS_TARGET" UNIQUE NULLS DISTINCT ("PROVIDER", "REPO_OWNER", "REPO_NAME", "KIND", "PROPOSAL_NUMBER");       
ALTER TABLE "PUBLIC"."USER_SSH_KEYS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_6B8" UNIQUE NULLS DISTINCT ("FINGERPRINT");           
ALTER TABLE "PUBLIC"."PERMISSION_GROUPS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B16" UNIQUE NULLS DISTINCT ("NAME");              
ALTER TABLE "PUBLIC"."SSH_KEY_SOURCES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_8E" FOREIGN KEY("SSH_KEY_ID") REFERENCES "PUBLIC"."USER_SSH_KEYS"("ID") ON DELETE CASCADE NOCHECK;  
ALTER TABLE "PUBLIC"."USER_SSH_KEYS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_6B" FOREIGN KEY("USERNAME") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE CASCADE NOCHECK;  
ALTER TABLE "PUBLIC"."PUSH_COMMITS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_A" FOREIGN KEY("PUSH_ID") REFERENCES "PUBLIC"."PUSH_RECORDS"("ID") ON DELETE CASCADE NOCHECK;          
ALTER TABLE "PUBLIC"."USER_SCM_TOKENS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_87" FOREIGN KEY("USERNAME") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE CASCADE NOCHECK;
ALTER TABLE "PUBLIC"."USER_SCM_IDENTITIES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_5" FOREIGN KEY("USERNAME") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE CASCADE NOCHECK;             
ALTER TABLE "PUBLIC"."SPRING_SESSION_ATTRIBUTES" ADD CONSTRAINT "PUBLIC"."SPRING_SESSION_ATTRIBUTES_FK" FOREIGN KEY("SESSION_PRIMARY_ID") REFERENCES "PUBLIC"."SPRING_SESSION"("PRIMARY_ID") ON DELETE CASCADE NOCHECK;        
ALTER TABLE "PUBLIC"."REPO_PERMISSIONS" ADD CONSTRAINT "PUBLIC"."FK_REPO_PERMISSIONS_USERNAME" FOREIGN KEY("USERNAME") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE CASCADE NOCHECK;
ALTER TABLE "PUBLIC"."FETCH_RECORDS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_C01" FOREIGN KEY("RESOLVED_USER") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE SET NULL NOCHECK;           
ALTER TABLE "PUBLIC"."PUSH_STEPS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_C7" FOREIGN KEY("PUSH_ID") REFERENCES "PUBLIC"."PUSH_RECORDS"("ID") ON DELETE CASCADE NOCHECK;           
ALTER TABLE "PUBLIC"."GROUP_MEMBERS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F1" FOREIGN KEY("GROUP_ID") REFERENCES "PUBLIC"."PERMISSION_GROUPS"("ID") ON DELETE CASCADE NOCHECK;  
ALTER TABLE "PUBLIC"."GROUP_PERMISSIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_80" FOREIGN KEY("GROUP_ID") REFERENCES "PUBLIC"."PERMISSION_GROUPS"("ID") ON DELETE CASCADE NOCHECK;              
ALTER TABLE "PUBLIC"."GROUP_MEMBERS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F18" FOREIGN KEY("USERNAME") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE CASCADE NOCHECK; 
ALTER TABLE "PUBLIC"."PUSH_ATTESTATIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B0" FOREIGN KEY("PUSH_ID") REFERENCES "PUBLIC"."PUSH_RECORDS"("ID") ON DELETE CASCADE NOCHECK;    
ALTER TABLE "PUBLIC"."EMAIL_SOURCES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_53E" FOREIGN KEY("USERNAME", "EMAIL") REFERENCES "PUBLIC"."USER_EMAILS"("USERNAME", "EMAIL") ON DELETE CASCADE NOCHECK;               
ALTER TABLE "PUBLIC"."USER_EMAILS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F" FOREIGN KEY("USERNAME") REFERENCES "PUBLIC"."PROXY_USERS"("USERNAME") ON DELETE CASCADE NOCHECK;     
