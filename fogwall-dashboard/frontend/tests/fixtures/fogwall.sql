-- H2 2.4.240; 
;              
CREATE USER IF NOT EXISTS "" SALT '' HASH '' ADMIN;            
CREATE CACHED TABLE "PUBLIC"."SCHEMA_MIGRATIONS"(
    "VERSION" CHARACTER VARYING(20) NOT NULL,
    "DESCRIPTION" CHARACTER VARYING(255) NOT NULL,
    "APPLIED_AT" TIMESTAMP NOT NULL
);      
ALTER TABLE "PUBLIC"."SCHEMA_MIGRATIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_3" PRIMARY KEY("VERSION");        
-- 17 +/- SELECT COUNT(*) FROM PUBLIC.SCHEMA_MIGRATIONS;       
INSERT INTO "PUBLIC"."SCHEMA_MIGRATIONS" VALUES
('1', 'initial schema', TIMESTAMP '2026-09-12 11:12:24.111171'),
('2', 'provider id format', TIMESTAMP '2026-09-12 11:12:24.117492'),
('3', 'email unique constraint', TIMESTAMP '2026-09-12 11:12:24.119242'),
('4', 'spring session tables', TIMESTAMP '2026-09-12 11:12:24.127084'),
('5', 'unified rule shape', TIMESTAMP '2026-09-12 11:12:24.180198'),
('6', 'repo permissions FK', TIMESTAMP '2026-09-12 11:12:24.183519'),
('7', 'rename operations to operation', TIMESTAMP '2026-09-12 11:12:24.185563'),
('8', 'user ssh keys', TIMESTAMP '2026-09-12 11:12:24.188719'),
('9', 'permission groups', TIMESTAMP '2026-09-12 11:12:24.193261'),
('10', 'scm oauth tokens', TIMESTAMP '2026-09-12 11:12:24.194948'),
('11', 'ssh key locked flag and auth source', TIMESTAMP '2026-09-12 11:12:24.202174'),
('12', 'ssh key sources', TIMESTAMP '2026-09-12 11:12:24.204719'),
('13', 'email sources', TIMESTAMP '2026-09-12 11:12:24.20737'),
('14', 'push commit co-authored-by trailers', TIMESTAMP '2026-09-12 11:12:24.210754'),
('15', 'scm api proxy', TIMESTAMP '2026-09-12 11:12:24.215433'),
('16', 'scm token cache scm login', TIMESTAMP '2026-09-12 11:12:24.217946'),
('17', 'scm api merge', TIMESTAMP '2026-09-12 11:12:24.221231');    
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
('f4155503-361e-453a-afdc-7134dc3f488c', TIMESTAMP '2026-09-12 11:13:12.849839', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/author-noreply-991145', '0000000000000000000000000000000000000000', 'e73491d5d6d1fd03243fcb14ce01608e263a96e6', NULL, 'Fixture Developer', 'noreply@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', TIMESTAMP '2026-09-12 11:13:17.293927', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/author-domain-996070', '0000000000000000000000000000000000000000', '5056db0c97662a318c5d9d96b96e6cc239ac6f3b', NULL, 'Fixture Developer', 'developer@internal.corp.net', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('95218505-5d18-4deb-a818-bf64b4d8ebdd', TIMESTAMP '2026-09-12 11:13:19.213968', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/message-wip-997989', '0000000000000000000000000000000000000000', '35dd731508157d682aead2504e2feb0e8bf015b0', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('3356cb56-d321-4851-b7d5-0f9584e6d1da', TIMESTAMP '2026-09-12 11:13:20.889683', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/message-pattern-999909', '0000000000000000000000000000000000000000', '746dece9575c65daa8d6c4b090fc597ac590bcf4', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('ecef2b91-2c4f-47dd-b685-39bf856dc783', TIMESTAMP '2026-09-12 11:13:23.788435', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/secret-aws-002740', '0000000000000000000000000000000000000000', '029f78c87db59e14da8e3404247e1dc953f796dc', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('0a9541ee-f94b-44cb-b4c0-9fc09bca1421', TIMESTAMP '2026-09-12 11:13:25.670375', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/diff-literal-004463', '0000000000000000000000000000000000000000', '6647aa6e91965c59c5f097af6bff76968bef60ac', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('4c4022a2-303e-447b-9454-7d0668f39bc0', TIMESTAMP '2026-09-12 11:13:27.733185', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/diff-pattern-006338', '0000000000000000000000000000000000000000', '10c8241c765d3afdcb81b1ac0ab892876f035e99', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('9f83faf1-9327-46e0-ae96-06bf143f0a57', TIMESTAMP '2026-09-12 11:13:31.066304', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/no-signoff-010071', '0000000000000000000000000000000000000000', '2a2c31d9add6690ff310b60b79cf04d67f778306', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL);        
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('349b4522-a93b-4e06-a254-a4af50c0dd1f', TIMESTAMP '2026-09-12 11:13:32.764074', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/signoff-mismatch-011727', '0000000000000000000000000000000000000000', '697ab075fe256229375bf2fb28ee4bd4fc69046f', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', TIMESTAMP '2026-09-12 11:13:34.458089', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/coauthor-denied-013435', '0000000000000000000000000000000000000000', '918a9799e901a7731a48a771f9ebbd473bbb8fbc', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('360cb445-29a9-45c7-adcc-34abd4403922', TIMESTAMP '2026-09-12 11:13:37.219756', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/coauthor-ok-016201', '0000000000000000000000000000000000000000', 'ae5d47b13e70e41d66fbe7b678fc5ad3c3ab2342', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('119f4b18-ac37-4e70-8b98-4df8964d7da4', TIMESTAMP '2026-09-12 11:13:39.859427', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/multi-fail-017901', '0000000000000000000000000000000000000000', '15d5a7010abe0e146564ea3f9e77a51881b3382c', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '8 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('053840be-43a1-4fe9-bedf-dd3f3adeeba3', TIMESTAMP '2026-09-12 11:13:42.544843', '/fixture-dev/fogwall-fixture', 'https://codeberg.org/fixture-dev/fogwall-fixture', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/unmapped-020572', '0000000000000000000000000000000000000000', '23958d0c6e6b79013a71c97059b8e5a1efc590e4', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, NULL, NULL, NULL, 'PUSH', 'REJECTED', NULL, 'User not authorized', FALSE, FALSE, NULL, NULL),
('ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', TIMESTAMP '2026-09-12 11:13:46.225516', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/pending-branch-025254', '0000000000000000000000000000000000000000', 'a5ba885b7c9e00f5423e3b838450c24d6c500b4e', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('4a480344-a5fb-436f-9b1a-01d23cab9d67', TIMESTAMP '2026-09-12 11:13:48.837273', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/tags/v9.9.9-fixture-1789226028', '0000000000000000000000000000000000000000', 'ec043a0c396f20513807e4635bcb320ca72a378d', NULL, NULL, NULL, NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', TIMESTAMP '2026-09-12 11:13:50.987976', '/fixture-dev/fogwall-fixture', 'https://gitea.com/fixture-dev/fogwall-fixture', 'gitea', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/gitea-029502', '0000000000000000000000000000000000000000', '3568d60be4e65a34b71eedd0d39ba696544ce968', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL);              
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', TIMESTAMP '2026-09-12 11:13:55.892104', '/fixture-dev/fogwall-fixture', 'https://gitlab.com/fixture-dev/fogwall-fixture', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/gitlab-warn-033455', '0000000000000000000000000000000000000000', '5947450c2a6886eea1d712af49a4925108f1962f', NULL, 'Fixture Developer', 'unregistered@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('87bc4724-4ab6-4902-b669-6cbec10c3608', TIMESTAMP '2026-09-12 11:13:59.856108', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/multi-commit-038538', '0000000000000000000000000000000000000000', '0522d2197e3cf7a0ffc59ae715fb184b0e1a5293', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-12 11:17:31.623199'),
('17789b25-690f-42b3-aab8-eabfd63f865f', TIMESTAMP '2026-09-12 11:14:02.625601', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/tags/lightweight-fixture-1789226042', '0000000000000000000000000000000000000000', 'ac8e296fcd938115d628e1c08cf5659d4a714863', NULL, NULL, NULL, NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-12 11:17:33.909676'),
('58b6d15e-a348-4d3d-b3b2-7f46519954d6', TIMESTAMP '2026-09-12 11:14:04.212268', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/reviewer-reject-043259', '0000000000000000000000000000000000000000', 'ac3d5877f7878d303df1f1c4a382d0e80e6dd7e4', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('505d24a3-8d13-4059-bfc4-319fa3f8fa13', TIMESTAMP '2026-09-12 11:14:05.875669', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/canceled-044890', '0000000000000000000000000000000000000000', 'd725fd8c70cb567f8a69c4a80a390576590ad748', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'CANCELED', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('d2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', TIMESTAMP '2026-09-12 11:14:07.490927', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/self-certify-046533', '0000000000000000000000000000000000000000', '828992dbe71db0a701a51c81407f01d42adb5ad6', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-12 11:17:36.315125'),
('a8ff12a0-f954-46f3-81ad-fe248d1a61df', TIMESTAMP '2026-09-12 11:14:11.546883', '/fixture-dev/fogwall-fixture', 'ssh://git@github.com/fixture-dev/fogwall-fixture.git', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/ssh-server-049311', '0000000000000000000000000000000000000000', '3a4d02167e721e4eff6b3cb22c44d8e0a02817bc', 'feat: pushed over the SSH transport', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', NULL, 'dev', NULL, 'SSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-12 11:17:04.312881');        
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
('0942d6c2-90fe-4ed2-9a89-d90552477cf0', 'f4155503-361e-453a-afdc-7134dc3f488c', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:12.857013'),
('e73888a1-1ed6-4bed-825a-58b83efed02c', 'f4155503-361e-453a-afdc-7134dc3f488c', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:14.945338'),
('1e4e0c87-014f-4b1a-8492-e770c1403376', 'f4155503-361e-453a-afdc-7134dc3f488c', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:14.945973'),
('6cbca9fc-1078-4f0a-b03b-c4bae720fc3e', 'f4155503-361e-453a-afdc-7134dc3f488c', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.143735'),
('54a83677-6c3a-40ea-8670-737258e0b010', 'f4155503-361e-453a-afdc-7134dc3f488c', 'commit-attribution', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.149138'),
('c6e98d63-7db2-43e6-b59e-bb734b91950a', 'f4155503-361e-453a-afdc-7134dc3f488c', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.149252'),
('1ff773fa-30e2-4915-85c6-60891ccf277d', 'f4155503-361e-453a-afdc-7134dc3f488c', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.151316'),
('abb87482-1ffa-46cf-a478-a8e8c51f3e9c', 'f4155503-361e-453a-afdc-7134dc3f488c', 'author-email', 250, 'FAIL', U&'\274c\fe0f  author email (noreply@example.com): blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)', NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.15384'),
('72ac5094-1874-4472-a710-6ab08e687566', 'f4155503-361e-453a-afdc-7134dc3f488c', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.155264'),
('5fbfaa92-ac6c-4763-991c-f89cd64ba000', 'f4155503-361e-453a-afdc-7134dc3f488c', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.155784'),
('8dfbe6b5-5802-4066-b122-99a3b41b692b', 'f4155503-361e-453a-afdc-7134dc3f488c', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.184967'),
('0c4496d8-c2af-4510-9453-3f6d3a73f3bb', 'f4155503-361e-453a-afdc-7134dc3f488c', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.232548'),
('927d2aca-09bd-44e8-86b4-26220b13ae4b', 'f4155503-361e-453a-afdc-7134dc3f488c', 'diff', 280, 'PASS', U&'diff --git a/notes/noreply.txt b/notes/noreply.txt\000anew file mode 100644\000aindex 0000000..ed902f8\000a--- /dev/null\000a+++ b/notes/noreply.txt\000a@@ -0,0 +1 @@\000a+feat: this commit has a noreply author - 2026-09-12T15:13:12Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.23567'),
('b3f5728f-d790-4aee-88f2-29f7d073b060', 'f4155503-361e-453a-afdc-7134dc3f488c', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.238456'),
('a1764482-b5c7-439d-b641-f9c13d25568f', 'f4155503-361e-453a-afdc-7134dc3f488c', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:15.238552'),
('8149c7b3-3456-402b-babc-d29d4f12bff5', 'f4155503-361e-453a-afdc-7134dc3f488c', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:16.014741'),
('b7f946a8-06e2-48d4-bd67-f13235a93056', 'f4155503-361e-453a-afdc-7134dc3f488c', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:16.029452'),
('95015141-7a3d-4b7f-95cf-cab8990e48f6', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.296468'),
('507ecb5c-6edf-487b-9be3-faae89dc6d34', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.306659'),
('2aadf4cd-fb58-4303-a9e9-1f6cdec0dd7f', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.30731');          
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('2e28f724-f191-4366-907b-578fe7397f04', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.31171'),
('2a77bcd1-1086-42f8-b687-84d6a6bbcb85', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'commit-attribution', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.313452'),
('9fe6c9ca-8adb-4bdd-a6e7-77786fe8157a', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.313526'),
('ba64deca-0412-456f-90cf-83d5430b75e5', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.314873'),
('a7cfe489-b6d2-4825-adb7-202dd9f0dccb', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'author-email', 250, 'FAIL', U&'\274c\fe0f  author email (developer@internal.corp.net): not in allowlist\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'not in allowlist', NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.315394'),
('fcf36d65-4068-4b85-aef2-a0b9ef0faf64', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.315495'),
('53719c59-9fce-400a-b381-35010e4a80d1', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.315547'),
('f83d5472-b981-4ddc-9dcf-411365fcad3e', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.323714'),
('ab388622-a46b-49b3-b6bb-5965eeb15aa7', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.330335'),
('cbade871-36d3-4d11-97dc-52f4e0423f64', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'diff', 280, 'PASS', U&'diff --git a/notes/domain.txt b/notes/domain.txt\000anew file mode 100644\000aindex 0000000..416504f\000a--- /dev/null\000a+++ b/notes/domain.txt\000a@@ -0,0 +1 @@\000a+feat: this commit comes from an unapproved domain - 2026-09-12T15:13:16Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.3335'),
('25018fcb-0094-44a3-86fa-f8bdb32ad3d7', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.336556'),
('aeaecc86-93f2-4cfe-9c0e-4177c7d0462d', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.336648'),
('fdde537e-1d50-442b-8d74-38158212129e', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.962342'),
('197f36ce-f8e3-4d9c-85f3-81ee08ef49d3', 'b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:17.972463'),
('1f832be9-aae7-4cd0-8e34-2509b5b7b397', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.216116'),
('c39230a1-1f80-4107-839c-b6bfee7df532', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.223444'),
('6b62c7b9-44ac-4223-bdcd-bb524a90a164', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.223918'),
('c9a2d99b-bc11-4ba2-bb4e-198afb81bbc6', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.228084'),
('f799eb60-8ce1-436a-bac6-2594e495f771', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.229293'),
('2dd8df52-d43e-4a59-9266-97149f62a81d', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.229332');      
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('c86d7e61-be87-4336-a39c-6a0c9594a522', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.230248'),
('5f461236-a503-450e-a05e-cd310a391d6e', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.230341'),
('1f97d284-3fd6-446b-a717-98036ee3649a', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.23039'),
('79adbb00-422c-4bfd-87dc-1d45c6f5df00', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'commit-message', 260, 'FAIL', U&'\274c\fe0f  WIP: still working on this feature: contains blocked term: "WIP"\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'contains blocked term: "WIP"', NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.231143'),
('54e62704-ce94-45b9-9420-cd0b637a2408', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.238441'),
('563db6d1-dcc2-4f61-aae2-3b01211e1a54', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.242886'),
('0e3ca5ad-042a-40f5-8f30-117a25fd71ba', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'diff', 280, 'PASS', U&'diff --git a/notes/wip.txt b/notes/wip.txt\000anew file mode 100644\000aindex 0000000..1a62001\000a--- /dev/null\000a+++ b/notes/wip.txt\000a@@ -0,0 +1 @@\000a+WIP: still working on this feature - 2026-09-12T15:13:18Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.245429'),
('ac666f24-8c99-4d7b-acdf-f9d84c24f19a', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.247512'),
('91c8fb01-3fe9-4afd-933b-ddf3a6a680ee', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.247579'),
('0e489210-0cad-40f2-896a-13309372606e', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.886788'),
('f4cb7064-60b3-4ac0-ac59-c38421eb2bab', '95218505-5d18-4deb-a818-bf64b4d8ebdd', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:19.894221'),
('2dc420b9-adff-4437-bc91-7c8c9268028c', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:20.891809'),
('cc237b62-7215-471c-88cd-7a998015290a', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.043735'),
('f8dfbe47-2111-4737-8fb7-7fca4f0f7fc9', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.044306'),
('cd11ca47-759a-4306-8bf3-191092eea5b1', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.048254'),
('720568c6-086f-4073-9bca-d3749afd50a6', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.049177'),
('5c2e1a2b-6d5e-4b84-a415-420836eb2cdb', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.049211'),
('18872ad3-12f9-42f1-af50-4c2c8d5dea1a', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.050049'),
('49f4b2e1-a68e-4c9f-ad80-de621401141a', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.05012'),
('6a62d115-d74e-48f5-9c25-5239b1a6e59e', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.050165'),
('51bce2d0-b6f9-468d-9c65-69666300fba4', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'commit-message', 260, 'FAIL', U&'\274c\fe0f  chore: rotate token=[REDACTED] in CI config: matches blocked pattern: (?i)(password|secret|token)\\s*[=:]\\s*\\S+\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'matches blocked pattern: (?i)(password|secret|token)\s*[=:]\s*\S+', NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.050697');           
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('f64330c9-7146-4dc2-8823-9bbcfb13fc64', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.056817'),
('73582d89-c70f-40cb-9a7d-d1c0c1d13063', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.061485'),
('3ccb7ff8-a0e1-4de4-9082-86ac5233c1a3', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'diff', 280, 'PASS', U&'diff --git a/notes/rotate.txt b/notes/rotate.txt\000anew file mode 100644\000aindex 0000000..fd28063\000a--- /dev/null\000a+++ b/notes/rotate.txt\000a@@ -0,0 +1 @@\000a+chore: rotate token=[REDACTED] in CI config - 2026-09-12T15:13:20Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.06389'),
('c16f7273-eb78-42dc-9124-d9438cb911f6', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.065851'),
('6d26bb6a-edce-4d0c-89f2-d513cd178bd1', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.065907'),
('fbd00c6a-5fd2-4f13-8d2f-3dcee2959c6a', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'secret-scan', 340, 'FAIL', U&'\274c\fe0f  [generic-api-key]  notes/rotate.txt:1\000a  commit: 746dece\000a  match:  token=[REDACTED] \000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[generic-api-key]  notes/rotate.txt:1\000a  commit: 746dece\000a  match:  token=[REDACTED] ', NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.712211'),
('79328d66-e0dd-40c4-ac12-a4bf2086e019', '3356cb56-d321-4851-b7d5-0f9584e6d1da', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:22.719913'),
('19e0b4cc-45bc-495e-8382-8817e30a8a0c', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.790383'),
('52c4ab89-5aa1-4934-a5e9-12c249dc7cf2', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.79706'),
('fd52daec-7aa8-439a-8a0b-df915a239ead', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.797496'),
('aad46e4f-2143-48b2-8a6e-9271568f29a7', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.800603'),
('76c80aea-1879-459d-8c7e-dbedc0737fde', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.801333'),
('e7c49f02-1235-4f53-9167-2c9089071ccf', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.801365'),
('2e6c0ced-9373-4317-a260-6c13e4e28f62', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.802171'),
('b2c96b9e-7b8c-4080-94ca-3313ce2b65f7', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.802239'),
('36eddbd9-bce0-4042-be2e-60771598253a', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.802284'),
('ed587472-cc6e-4919-a875-794da8323e8a', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.802318'),
('ef6166f5-d6e3-409c-ace1-f931d649c6a4', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.807305'),
('6c794224-33a3-403c-8ef9-c651aa96b8af', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.811014'),
('9a942dba-b60b-4818-9a16-7f6a38ac4244', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'diff', 280, 'PASS', U&'diff --git a/aws-credentials b/aws-credentials\000anew file mode 100644\000aindex 0000000..53c17b0\000a--- /dev/null\000a+++ b/aws-credentials\000a@@ -0,0 +1,3 @@\000a+[default]\000a+aws_access_key_id = [REDACTED]\000a+aws_secret_access_key = [REDACTED]\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.81309');               
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('87166063-4644-49d9-9a4a-fc5252104cdd', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.814885'),
('202f86c6-8ef8-4a4a-8efa-a8ff357ddb33', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:23.814941'),
('e2157293-5abd-4847-bea2-ee910d376887', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'secret-scan', 340, 'FAIL', U&'\274c\fe0f  [generic-api-key]  aws-credentials:3\000a  commit: 029f78c\000a  match:  aws_secret_access_key = [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[generic-api-key]  aws-credentials:3\000a  commit: 029f78c\000a  match:  aws_secret_access_key = [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-12 11:13:24.439475'),
('a8ea0429-2268-44fe-9b87-706db231ec10', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'secret-scan', 340, 'FAIL', U&'\274c\fe0f  [aws-access-token]  aws-credentials:2\000a  commit: 029f78c\000a  match:  [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[aws-access-token]  aws-credentials:2\000a  commit: 029f78c\000a  match:  [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-12 11:13:24.439558'),
('d94c4e74-21bd-4d2d-9756-ec7ccb3a604f', 'ecef2b91-2c4f-47dd-b685-39bf856dc783', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:24.446758'),
('c53d1681-3079-48f1-902a-a4ae55a7bd84', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.672759'),
('6fc58da9-e688-451b-9967-856b39e3499c', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.679572'),
('dfa979d1-8d91-42a4-a19a-df2bb3fa591d', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.679956'),
('20c5e770-d807-40e5-ad45-8a64f5b3fbc6', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.683094'),
('216550d5-a895-469d-be88-31734b91cc91', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.683807'),
('75802aed-a1a9-4e84-a018-6840c219367c', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.683835'),
('80a45f5b-bfc3-4353-9386-fa5711102022', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.684604'),
('ed0a3c29-87d9-45fe-9ee0-599d5db25692', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.684672'),
('9aacfe69-786d-458e-a7e9-5f996ea28c6d', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.68492'),
('98a62dc5-64bc-4c36-99a6-a96f882dbec8', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.684979'),
('eb8c8f09-9c6c-41b3-8bae-db571718a29b', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.688683'),
('68e176b7-6690-4bde-a3ef-7514e2eef85a', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.692275'),
('2cd65312-9ee1-4158-9f6e-61eccefce346', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'diff', 280, 'PASS', U&'diff --git a/config.yml b/config.yml\000anew file mode 100644\000aindex 0000000..0d0e7b4\000a--- /dev/null\000a+++ b/config.yml\000a@@ -0,0 +1,3 @@\000a+upstream:\000a+  api: https://internal.corp.example.com/api/v1\000a+  timeout: 30\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.694118'),
('1c1d46a6-543f-4ec8-a636-ee853358feea', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'diff-scan', 300, 'FAIL', U&'blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.695565');     
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('8f9c1152-4d27-49b9-b115-889ec477263b', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'diff-scan', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.695591'),
('60aafb7f-a319-401f-b4ab-b8a8c14f2bbf', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:25.697532'),
('557cfa7e-3c8b-43ff-a018-b7c586c3ddcc', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:26.310711'),
('7a9c1b6e-61e6-4c8f-b524-efd2238aa859', '0a9541ee-f94b-44cb-b4c0-9fc09bca1421', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:26.318114'),
('5ced0d88-8994-4869-8389-1304bb5d0272', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:27.735201'),
('c688e931-6d24-4c2c-bcda-2c3b6bfa68ad', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.407767'),
('c9a1aa28-55b6-4ad0-bbec-85f7655211bc', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.408531'),
('7f9e140b-5aca-4a84-aa95-e0a332f24a5b', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.411924'),
('7a570b9f-8334-4c6d-86e7-c7b304595618', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.412621'),
('d558c244-5a5b-40c4-9694-2b425f302c44', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.412653'),
('6893335e-4485-41b7-a38e-c0f11ba27375', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.413476'),
('5d6192fd-1f28-4359-952a-6e29c63c2e86', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.413542'),
('4b7ed2ea-6512-4d19-9b23-ae0809ec78a1', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.413581'),
('8c2a8dc2-0f30-4751-9fc2-cf27d46cf481', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.413613'),
('903ff12a-bc17-459a-a3c7-021e8c7efef5', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.417002'),
('7d034288-2187-4bb7-a337-6499c20e83aa', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.420871'),
('f66baaf5-39cf-46e4-a3de-9715c61c5094', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'diff', 280, 'PASS', U&'diff --git a/deploy.sh b/deploy.sh\000anew file mode 100644\000aindex 0000000..3e0ea35\000a--- /dev/null\000a+++ b/deploy.sh\000a@@ -0,0 +1,2 @@\000a+#!/bin/bash\000a+curl -X POST http://ci.corp.example.com/deploy -d ''{"version": "1.2.3"}''\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.422814'),
('b2937a96-8d1f-458b-bb9f-35f45f62bdcb', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'diff-scan', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in deploy.sh\000a  curl -X POST http://ci.corp.example.com/deploy -d ''{"version": "1.2.3"}''', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in deploy.sh', NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.423289'),
('e9ee3e19-26f5-4574-8172-a8299d1279dc', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:29.425152'),
('460faf33-13ee-40cc-9994-2ff32255a664', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:30.050077');            
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('0ef90a50-792c-4653-a2d9-70160c004e00', '4c4022a2-303e-447b-9454-7d0668f39bc0', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:30.055911'),
('6895c932-fcdb-4725-bba8-8c90cc36aede', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.068135'),
('c126e48f-31dc-40de-be8b-5214993ce7ac', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.074354'),
('7edea7de-091f-46fc-bb7e-d1078d9669b9', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.074738'),
('0917f501-d4ed-4f4a-a4b4-ed4f70addb5c', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.077423'),
('9ce11f23-9a7d-4832-ad35-4846f65da668', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.078085'),
('b0d2c500-79e2-41be-9cb6-32a7b4a852df', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.078111'),
('2cba490a-d7e9-4949-b7fe-1dcea7604bb6', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.07892'),
('05c82708-7cf2-412b-9d38-a35e4481cdc7', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.078981'),
('cf36c23c-049c-4c0c-a7a4-8c7aeb24f8f4', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'trailers', 255, 'FAIL', U&'\274c\fe0f  commit 2a2c31d has no Signed-off-by trailer\000a  \2192 This repository requires the Developer Certificate of Origin (DCO) sign-off.\000a  \2192 Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase --signoff <base> for a range).', 'missing Signed-off-by (2a2c31d)', NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.079571'),
('20c219e5-eae0-4ef2-81b9-3608b5a33aed', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.079614'),
('1b9dbe79-8699-42c6-8741-12034c927458', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.082302'),
('b1eef617-99c8-4e02-a3bb-b031c13151ce', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.085388'),
('ee506e6d-ef84-4a4c-947d-5b3bf3f0ed19', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'diff', 280, 'PASS', U&'diff --git a/notes/unsigned.txt b/notes/unsigned.txt\000anew file mode 100644\000aindex 0000000..a705806\000a--- /dev/null\000a+++ b/notes/unsigned.txt\000a@@ -0,0 +1 @@\000a+feat: forgot to sign off - 2026-09-12T15:13:30Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.086841'),
('0d5c0b09-7f65-445e-a1c9-80d4fefd7313', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.088191'),
('a0a6eb89-d03c-4675-9a6a-2565dfac8eca', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.088237'),
('41d6e50f-770f-4268-bd1e-d59a0c30c9f3', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.705316'),
('d731c19a-c813-466f-b3bf-00f8fb8b0e5e', '9f83faf1-9327-46e0-ae96-06bf143f0a57', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:31.713385'),
('4318684f-e110-40a4-9367-b80a7d2082f9', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.765732'),
('cf4171fa-8fe1-49e5-95db-40eb94ee9b73', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.771767'),
('73f91d16-d085-4702-ac12-b1c247b8c3e4', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.772134');           
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('0ab4b19e-90a8-4a93-aa10-4c4acdf141ad', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.774846'),
('156c8b63-443b-492f-b2ef-71a7be05ec12', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.775647'),
('48c2b441-375a-4654-801a-ac33332a1108', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.775675'),
('41bed257-dcf7-41f2-9c58-f979a70feeba', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.776324'),
('b0a9d32e-bb4b-4810-86ee-8496c3d6be3a', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.77639'),
('a37898b4-72b1-42e7-b0e8-c5aefd68cdea', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'trailers', 255, 'FAIL', U&'\274c\fe0f  commit 697ab07 has no Signed-off-by matching its author <fixture-dev@example.com>\000a  \2192 The DCO requires you to sign off your own work: a Signed-off-by whose email equals the commit author.\000a  \2192 Fix: git config user.email to your author email, then git commit --amend --signoff.', 'Signed-off-by does not match author (697ab07)', NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.776881'),
('aff07a34-8d12-4698-810d-978dc051213d', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.776927'),
('70db1844-b662-4aab-8def-bd7ac82bf019', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.77932'),
('d4a7c80f-996a-421f-a1c1-04ece48bc307', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.782551'),
('e7a8292f-c5cd-4e7d-b032-1e367f45a307', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'diff', 280, 'PASS', U&'diff --git a/notes/mismatch.txt b/notes/mismatch.txt\000anew file mode 100644\000aindex 0000000..8ccb2ab\000a--- /dev/null\000a+++ b/notes/mismatch.txt\000a@@ -0,0 +1 @@\000a+feat: signed off by the wrong person - 2026-09-12T15:13:32Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.784272'),
('257ce15e-b9ee-4af5-8ec2-9a0307159b19', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.786001'),
('d313e35e-7e37-435e-bc0a-c112da6dfeee', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:32.786049'),
('9abf0e9f-b0d2-4171-a363-b17fe18ffbf9', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:33.409192'),
('a82127d1-0bb6-4d24-ba58-8d9713e58791', '349b4522-a93b-4e06-a254-a4af50c0dd1f', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:33.417266'),
('7841cc90-61fd-449a-9590-04544503a221', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:34.459886'),
('ef434913-aab4-4063-adce-9d87a196b963', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.552197'),
('21affea2-8375-4cd0-9d1b-9bb6e9253763', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.552715'),
('1278df4e-b502-445d-b3b5-7b92b53c99d0', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.555721'),
('eae84257-e562-49ed-b48b-4d806b0d235c', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.556303'),
('5d3cec8d-29c0-4342-96ee-3ad2a0c39368', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.556324');              
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('584b711b-141b-418c-8973-408b9361c316', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.556992'),
('8e5bd6e1-0bfc-46b1-baf1-6a5fd6894925', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.557056'),
('5210e28a-4558-4fa5-b850-6114fad442b1', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'trailers', 255, 'FAIL', U&'\274c\fe0f  commit 918a979 Co-authored-by (Contractor <contractor@outside.example.net>): not in allowlist\000a  \2192 Co-authors must be permitted by policy (allowed domain / not a blocked address).\000a  \2192 Fix: remove the disallowed Co-authored-by line, or use an approved co-author identity.', 'Co-authored-by not allowed (918a979)', NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.557653'),
('78262dbf-b6c4-43a1-9dee-c50b8b3ff7aa', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.557707'),
('c6c729c7-5fc6-422d-94b7-1be550b13c25', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.560663'),
('d1d3c317-7e63-407c-a1cc-dad5878baa35', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.563841'),
('c645ff5b-83b0-42a5-8af2-33f97943e55d', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'diff', 280, 'PASS', U&'diff --git a/notes/coauthor.txt b/notes/coauthor.txt\000anew file mode 100644\000aindex 0000000..71697e2\000a--- /dev/null\000a+++ b/notes/coauthor.txt\000a@@ -0,0 +1 @@\000a+feat: paired with an outside contractor - 2026-09-12T15:13:33Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.565537'),
('6c710e03-2449-4d8e-91c7-4747d6a884ec', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.567175'),
('d2f77c02-e901-48b6-b284-58bbcfda193d', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:35.567213'),
('fdbe620f-759b-4c3f-afe6-d1544674f30f', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:36.17786'),
('c7264b94-604d-406f-b381-0b2ef2c08d67', '59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:36.184946'),
('e472d00a-5c91-4b04-bd48-943fe6e33344', '360cb445-29a9-45c7-adcc-34abd4403922', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.221493'),
('6e013935-b6ce-432a-8c9c-79b3a9706cfa', '360cb445-29a9-45c7-adcc-34abd4403922', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.227649'),
('c86e04f1-ad8a-4531-accb-8c97e70fc2e8', '360cb445-29a9-45c7-adcc-34abd4403922', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.227971'),
('c48834e6-01df-4160-bddd-7fa93e39de16', '360cb445-29a9-45c7-adcc-34abd4403922', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.230582'),
('73b20b79-4912-4998-a47d-532b87e69d92', '360cb445-29a9-45c7-adcc-34abd4403922', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.231125'),
('5a5ee577-8ce4-4e46-b040-64f91317f6a3', '360cb445-29a9-45c7-adcc-34abd4403922', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.23115'),
('edeee37a-418c-4fa8-bbd3-10c02ce143b7', '360cb445-29a9-45c7-adcc-34abd4403922', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.231784'),
('71774ebb-59a1-4120-b185-f2462111828e', '360cb445-29a9-45c7-adcc-34abd4403922', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.231842'),
('5e7bd00f-c321-4bda-9339-9b75d3770a94', '360cb445-29a9-45c7-adcc-34abd4403922', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.231895'),
('498e8632-09b5-4ebb-8a08-13ad595ba7b0', '360cb445-29a9-45c7-adcc-34abd4403922', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.231931');    
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('07d7471e-f5e3-4eba-b704-7905eae5b574', '360cb445-29a9-45c7-adcc-34abd4403922', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.234074'),
('07ed5032-5d85-4526-bec1-51c351b5019c', '360cb445-29a9-45c7-adcc-34abd4403922', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.236712'),
('af61161c-8df2-4139-a2ef-095ba3bbf222', '360cb445-29a9-45c7-adcc-34abd4403922', 'diff', 280, 'PASS', U&'diff --git a/notes/pair.txt b/notes/pair.txt\000anew file mode 100644\000aindex 0000000..08df729\000a--- /dev/null\000a+++ b/notes/pair.txt\000a@@ -0,0 +1 @@\000a+feat: pair-programmed with an allow-listed co-author - 2026-09-12T15:13:36Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.238544'),
('f3764f08-72e0-4466-b58e-3d0e7d538460', '360cb445-29a9-45c7-adcc-34abd4403922', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.239951'),
('bad90dab-218e-4941-835f-bb09d8368326', '360cb445-29a9-45c7-adcc-34abd4403922', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.239992'),
('bfb9921b-4e5d-4060-9ed3-15180e8966de', '360cb445-29a9-45c7-adcc-34abd4403922', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.880388'),
('336403d9-41d1-4eb7-8920-b19209b1e7cb', '360cb445-29a9-45c7-adcc-34abd4403922', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.886638'),
('9021f503-7b07-4144-b67d-90eb016ffe21', '360cb445-29a9-45c7-adcc-34abd4403922', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:37.886713'),
('26e7733a-0964-4742-aaf7-53eb0320cef1', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.861114'),
('38adc9a9-b66a-482b-a8eb-460b56b61565', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.871797'),
('83d55630-5ded-4600-bbad-23c10255fe04', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.872248'),
('2960a291-a515-468c-a10f-3877750cec09', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.875304'),
('0734c318-f9f5-4dfb-887b-457932cada9a', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'commit-attribution', 160, 'WARN', U&'2 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.876235'),
('d7872998-4bf6-472e-a7f1-2ac7a410133d', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.876263'),
('f44171ee-b000-4358-ba4d-54765f35cacb', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.877195'),
('d37ec7b9-857d-474e-a02a-f40417bd3dee', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'author-email', 250, 'FAIL', U&'\274c\fe0f  author email (noreply@example.com): blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.877511'),
('4285146f-1b8f-4e18-9023-877ec44c2720', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'trailers', 255, 'FAIL', U&'\274c\fe0f  commit 15d5a70 has no Signed-off-by trailer\000a  \2192 This repository requires the Developer Certificate of Origin (DCO) sign-off.\000a  \2192 Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase --signoff <base> for a range).', 'missing Signed-off-by (15d5a70)', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.877672'),
('9ca59909-246c-4026-8120-39d863f8d055', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'commit-message', 260, 'FAIL', U&'\274c\fe0f  WIP: commit 2 \2014 bad commit message: contains blocked term: "WIP"\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'contains blocked term: "WIP"', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.878179');   
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('887a3e0f-60ad-423b-9417-22a1e1f4464a', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.883543'),
('f4edae0c-6223-4b19-9c0c-d1c16b98e350', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.893242'),
('b29d8363-94e2-427c-b2d1-b09852be31ea', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'diff', 280, 'PASS', U&'diff --git a/ci-config.env b/ci-config.env\000anew file mode 100644\000aindex 0000000..e2977e7\000a--- /dev/null\000a+++ b/ci-config.env\000a@@ -0,0 +1 @@\000a+GITHUB_TOKEN=[REDACTED]\000adiff --git a/config.yml b/config.yml\000anew file mode 100644\000aindex 0000000..bb7ce43\000a--- /dev/null\000a+++ b/config.yml\000a@@ -0,0 +1,2 @@\000a+upstream:\000a+  api: https://internal.corp.example.com/api/v1\000adiff --git a/multi/1.txt b/multi/1.txt\000anew file mode 100644\000aindex 0000000..c0f5c9d\000a--- /dev/null\000a+++ b/multi/1.txt\000a@@ -0,0 +1 @@\000a+test: commit 1 \2014 noreply author email - 2026-09-12T15:13:38Z\000adiff --git a/multi/2.txt b/multi/2.txt\000anew file mode 100644\000aindex 0000000..43e76b4\000a--- /dev/null\000a+++ b/multi/2.txt\000a@@ -0,0 +1 @@\000a+WIP: commit 2 \2014 bad commit message - 2026-09-12T15:13:38Z\000adiff --git a/multi/5.txt b/multi/5.txt\000anew file mode 100644\000aindex 0000000..0a4a352\000a--- /dev/null\000a+++ b/multi/5.txt\000a@@ -0,0 +1 @@\000a+test: commit 5 \2014 unregistered commit email - 2026-09-12T15:13:39Z\000adiff --git a/multi/6.txt b/multi/6.txt\000anew file mode 100644\000aindex 0000000..3f087fe\000a--- /dev/null\000a+++ b/multi/6.txt\000a@@ -0,0 +1 @@\000a+test: commit 6 \2014 missing DCO sign-off - 2026-09-12T15:13:39Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.896049'),
('c12447e6-e80b-4156-b3fd-cf487f5d6b31', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'diff-scan', 300, 'FAIL', U&'blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.896599'),
('e24a813a-5b88-4263-8207-53abba6cb132', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'diff-scan', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.896617'),
('35dd472e-ee3b-416b-9b24-cd2c51191ce3', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'diff-scan', 300, 'FAIL', U&'commit 78558a1: blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', '78558a1: blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.901968'),
('6d0a7b90-ca57-4cb1-b09d-bc94c03a6462', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'diff-scan', 300, 'FAIL', U&'commit 78558a1: blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', '78558a1: blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.901978'),
('bb071ced-ac90-4931-84ae-0f5119d8b44b', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:39.904282'),
('924497c1-bd35-490b-bdd3-822adc486987', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'secret-scan', 340, 'FAIL', U&'\274c\fe0f  [github-pat]  ci-config.env:1\000a  commit: 0979fb2\000a  match:  [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[github-pat]  ci-config.env:1\000a  commit: 0979fb2\000a  match:  [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-12 11:13:40.54557'),
('d20f46f1-757e-4722-bae6-566c79eeb59e', '119f4b18-ac37-4e70-8b98-4df8964d7da4', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:40.552273');        
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('3dbd7135-909a-4c58-b13f-98aaf1150247', '053840be-43a1-4fe9-bedf-dd3f3adeeba3', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:42.546427'),
('58b69ac8-82e2-4ff2-b895-c4f013e87f37', '053840be-43a1-4fe9-bedf-dd3f3adeeba3', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:44.51185'),
('30776998-9197-4885-bd2c-eeda84ae2d5f', '053840be-43a1-4fe9-bedf-dd3f3adeeba3', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:44.512251'),
('6e34822c-32f9-4be1-b863-b24f811b1bd5', '053840be-43a1-4fe9-bedf-dd3f3adeeba3', 'push-permission', 150, 'FAIL', U&'\000a\26d4\fe0f  Push Blocked - Unauthorized\000a\000a\274c\fe0f  dev is not allowed to push to:\000a   \+01f517\fe0f  https://codeberg.org/fixture-dev/fogwall-fixture\000a', 'User not authorized', NULL, NULL, TIMESTAMP '2026-09-12 11:13:45.23898'),
('4cc89395-6106-4f60-87d2-686e475bbf06', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:46.227215'),
('cba055fa-f86a-4270-a5b3-d9094710b9e4', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.393005'),
('a8fca982-aa57-4b83-a6f5-87d3ad875db5', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.393438'),
('ab0350ae-b2f4-4363-8935-a660771c692e', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.395763'),
('8d5c8770-0274-4d68-b436-2dbfb9fd8445', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.396258'),
('f991d7d6-a42c-4c01-a724-e609fb3f5e56', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.396283'),
('b37f4f60-14aa-4af8-8505-ebad08df2b90', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.396946'),
('64768f85-b22e-4c1e-a098-9f973e46704c', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.397005'),
('6998cb08-b5c8-4cd4-b541-ca41841ab7ae', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.397051'),
('7704d3fb-5009-405f-970d-1dd47b401f24', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.397083'),
('a5ba2f7e-c12b-432c-8833-5b1e56ba26b3', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.399041'),
('70847e2f-b90c-4811-91b4-d6859d70d3c9', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.401691'),
('f515af6e-4b5e-41a5-b209-09ef8ffb145f', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'diff', 280, 'PASS', U&'diff --git a/docs/release-notes.md b/docs/release-notes.md\000anew file mode 100644\000aindex 0000000..669de3b\000a--- /dev/null\000a+++ b/docs/release-notes.md\000a@@ -0,0 +1 @@\000a+docs: add release notes stub - 2026-09-12T15:13:45Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.402923'),
('90453191-4d58-41ce-89ae-24a90b9fc9e7', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.404181'),
('5d1891a0-05e9-431a-a32f-83e04187bcd7', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:47.40421'),
('a1f3f2dd-3744-4fbe-af97-15919a4b5ef9', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.026138'),
('c72d997c-3d48-43fd-b46c-dfc290f0ae31', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.030277');  
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('296eb133-01e2-4b5e-b6d7-2c31cee43eb5', 'ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.030324'),
('63dfbdcd-c2a7-48d9-af17-7bbf891185dd', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.838989'),
('ae97c95b-6215-426a-906a-1da9998b9d1d', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.845911'),
('8c51c562-9b15-4bf3-bd9e-e65a8390366d', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.846263'),
('75168758-7671-48d9-8ea4-13d2ebb14d5f', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.848455'),
('51adafb9-7b47-4030-bb0a-fc3e2e32a8c8', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.848503'),
('91d68eb3-9a29-4103-b3bf-11bf12bae5d9', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.848532'),
('d541a087-3c39-41d9-9aa2-3ed997bc496c', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.849116'),
('26559eb6-b2dc-441d-8393-782287c62e6d', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.849146'),
('735c3590-2cab-49c4-b7f5-de6a04ca5ccb', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.849163'),
('9fa02c9b-b67e-4050-86d2-471616cefb2c', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.849238'),
('b07bc398-705c-449c-b9cb-3b7e00f66653', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.851725'),
('f153654b-3a61-425c-8981-3870d5c25e1a', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.85177'),
('55ffe893-e97a-4bb3-a19d-a320d55dcfd1', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.851786'),
('587c0669-fa5e-4be3-8576-7773e28f0b3d', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:48.851801'),
('59461fbf-3883-464e-9264-1139bf7c2286', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:49.485132'),
('8d007a97-5230-4cf1-908b-4ab94b1f1b05', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:49.485208'),
('935edbbd-4cdb-4f2d-969e-813045fe13be', '4a480344-a5fb-436f-9b1a-01d23cab9d67', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:49.485229'),
('c823c14e-75f7-4331-8d16-db4811080003', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:50.989557'),
('d287bcbb-78df-469a-83d2-83872f3a861b', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.035429'),
('32b81671-740b-4bce-96a1-078a858a4ac2', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.035812'),
('ec459d75-6b91-491d-b6e5-1d4557959278', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.795663'),
('bde941d8-0aa2-4e86-9908-a66c99a2e6c6', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.797294'),
('7b785e7f-a0a1-4b50-8600-22628d3339d2', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.797324');               
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('982bb818-74a5-4594-8677-83859df1f362', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.798154'),
('0961f6ac-7e0f-4093-a112-cde2784c7ef1', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.798217'),
('37f476f9-7a90-4d1b-8e66-1b53616b48e8', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.798251'),
('d8ad7126-cf82-4c39-8858-a8565430cfa5', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.798291'),
('e7f881fd-9312-4bc4-a325-499fff41f07d', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.800667'),
('9ac79a5e-e4f6-4fe8-90f6-1b788e456da1', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.80407'),
('90159f71-32bb-42da-bda7-0bb6101f6ca9', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'diff', 280, 'PASS', U&'diff --git a/README.md b/README.md\000aindex 139e965..9d2c752 100644\000a--- a/README.md\000a+++ b/README.md\000a@@ -1,3 +1,3 @@\000a # fogwall-fixture\000a \000a-fogwall UI fixture capture \2014 safe to delete\000a\\ No newline at end of file\000a+fogwall UI fixture capture \2014 safe to deletedocs: touch readme via fogwall - 2026-09-12T15:13:50Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.805644'),
('c01550ad-b836-455a-ad2b-6553ba801d33', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.806925'),
('3ed4cce4-d128-421f-96f2-cf11d348cdc4', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:52.80696'),
('10d773d8-495f-484a-bc9a-88cfc8e77b6d', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:53.427124'),
('57ea6e3f-b314-436c-a978-015080dc70af', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:53.433894'),
('6c78c972-0556-4fe6-b6a0-ca1ecce38672', 'e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:53.433947'),
('5e6a14fc-4557-4d44-bb38-51a316167539', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:55.893723'),
('43cbd6d3-b9a1-4e8a-894e-03003a878a31', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.713767'),
('b303f868-adc5-4512-b15f-7ed2446c73d8', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.714158'),
('5a732a1b-885e-437c-bf73-95bad71534e7', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.868542'),
('66233128-f5f5-4753-9f52-2da717be1d9a', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'commit-attribution', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.870354'),
('6370e533-35fb-4a0c-b93b-9b1a54489702', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.870385'),
('7a89914d-0d46-428f-91a1-79b68881dffb', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.871172'),
('7b613324-1cb2-470f-a82f-7944f2ddf60e', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.871235'),
('2315eaac-a2fb-44f4-bbf4-0674d21604d3', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.87127');         
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('78aad9bc-a3e0-4d28-b380-7acd757dd0f9', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.871378'),
('ba585659-9b75-476e-afd9-3683a0ac2284', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.873206'),
('92419066-3f40-4a67-9851-fa651896d8fd', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.875947'),
('b95e94e7-4c07-45f6-b3db-89277a816704', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'diff', 280, 'PASS', U&'diff --git a/notes/gitlab.txt b/notes/gitlab.txt\000anew file mode 100644\000aindex 0000000..94ae504\000a--- /dev/null\000a+++ b/notes/gitlab.txt\000a@@ -0,0 +1 @@\000a+test: identity resolution \2014 gitlab resolved, email unregistered - 2026-09-12T15:13:55Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.877308'),
('07e81435-c998-46f1-ba63-1a346d492d88', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.878597'),
('d7f8a0cf-67da-4ec2-a627-5bfeb9d7b7fa', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:57.878628'),
('c04d8c6c-c0d7-4c2a-9f0e-02638f72a39b', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:58.512058'),
('15814eb6-8ec3-4ca7-a89a-14f86d55afee', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:58.517186'),
('1df21560-cd3d-471a-b450-989cebca23ad', 'f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:58.517226'),
('f60bc1f6-c9fd-4f81-a437-8fb7ba05e197', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:13:59.857642'),
('43b90053-9a38-49b9-9131-3ba892ad4ede', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.958648'),
('71d2d21d-f57d-4193-9061-6541c44ec7fb', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.959068'),
('8f0707de-7619-48a9-b53d-bec5d624afb6', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.961338'),
('a4e90412-0fc5-4883-89c8-7580de2be41a', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.961855'),
('c33ddcb5-a28d-4249-8bb3-65894d90848e', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.961876'),
('ee2885e9-0646-46fe-adc0-3b6c1b834635', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.962585'),
('1057753d-efa1-4441-aeca-d7aa156c08c1', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.96265'),
('11ec59b9-2d3f-4ac1-8224-add5a8563e74', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.962702'),
('1af06a8f-5ff2-4fb0-adcb-44626928e852', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.962743'),
('10daa05c-977e-4e87-8e56-902d7685e69e', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.96482'),
('a4f22970-139c-411f-b895-b1cc0b41ada4', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.969636'),
('7501d459-e84e-408e-a788-93d9b4fa454c', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'diff', 280, 'PASS', U&'diff --git a/src/alpha.txt b/src/alpha.txt\000anew file mode 100644\000aindex 0000000..f06cf9b\000a--- /dev/null\000a+++ b/src/alpha.txt\000a@@ -0,0 +1 @@\000a+feat(alpha): first of three - 2026-09-12T15:13:59Z\000adiff --git a/src/beta.txt b/src/beta.txt\000anew file mode 100644\000aindex 0000000..ea070b9\000a--- /dev/null\000a+++ b/src/beta.txt\000a@@ -0,0 +1 @@\000a+feat(beta): second of three - 2026-09-12T15:13:59Z\000adiff --git a/src/gamma.txt b/src/gamma.txt\000anew file mode 100644\000aindex 0000000..532fee2\000a--- /dev/null\000a+++ b/src/gamma.txt\000a@@ -0,0 +1 @@\000a+fix(gamma): third of three - 2026-09-12T15:13:59Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.971272');          
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('db985b68-3770-4529-9d99-60a2721109d5', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.975189'),
('e830b75b-0562-4566-8a9e-e60e9a0bd2c5', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:00.975232'),
('9ccc9cdd-f1db-4a59-bcf9-42a1d85f352f', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:01.600868'),
('e340b48d-97e6-4e25-9799-496d8cf9ea47', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:01.606885'),
('41192732-d06a-44ec-895c-6b455bb957dc', '87bc4724-4ab6-4902-b669-6cbec10c3608', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:01.606918'),
('042c8162-3acf-4ab3-bc9c-489d0e31d6a8', '17789b25-690f-42b3-aab8-eabfd63f865f', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.627154'),
('eb848e2a-8b12-4cf2-8083-c111aa0076a9', '17789b25-690f-42b3-aab8-eabfd63f865f', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.631834'),
('0078480c-83b2-4c4a-b281-194d8241280b', '17789b25-690f-42b3-aab8-eabfd63f865f', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.632135'),
('cd7e1716-4d71-4e38-9d30-edd5acf1e73d', '17789b25-690f-42b3-aab8-eabfd63f865f', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634066'),
('6d6e2c69-6d50-4cac-9d1d-67a6ad5a57fb', '17789b25-690f-42b3-aab8-eabfd63f865f', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634097'),
('6925bf95-fe9e-44f9-93e7-7b07329de67e', '17789b25-690f-42b3-aab8-eabfd63f865f', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634107'),
('9c635ec5-59c5-44f8-bb71-5e140ed49d65', '17789b25-690f-42b3-aab8-eabfd63f865f', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634624'),
('a75ca5ba-60a5-4ffa-bfae-285b6f2bba13', '17789b25-690f-42b3-aab8-eabfd63f865f', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634652'),
('f783119c-799c-47e4-b79f-55fa2bee3c4f', '17789b25-690f-42b3-aab8-eabfd63f865f', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634661'),
('d3acb808-f3ab-4544-a7b1-d87bea5d6b19', '17789b25-690f-42b3-aab8-eabfd63f865f', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634673'),
('67791f89-07c3-455a-87ad-ce638f3ad96b', '17789b25-690f-42b3-aab8-eabfd63f865f', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634704'),
('c99e0c66-31c1-4fda-93d2-1ad40dc192f0', '17789b25-690f-42b3-aab8-eabfd63f865f', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634722'),
('8c00491a-5796-44de-ab15-9dbbfe582d36', '17789b25-690f-42b3-aab8-eabfd63f865f', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.63473'),
('e1084750-a946-4e21-99cc-9c8a7d36156a', '17789b25-690f-42b3-aab8-eabfd63f865f', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:02.634738'),
('2e14501d-490e-4862-b605-3112986b070a', '17789b25-690f-42b3-aab8-eabfd63f865f', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:03.243356'),
('dac0c843-9153-47ff-ac29-c3c711878a6a', '17789b25-690f-42b3-aab8-eabfd63f865f', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:03.243476'),
('80ae88e6-df9d-4ca1-8e3b-c2ea2ab540d8', '17789b25-690f-42b3-aab8-eabfd63f865f', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:03.2435'),
('ff75eaf1-c1f4-4667-89b5-8af70659e169', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.214204'),
('ad88d85d-102e-49dc-9f63-67d36f4b4df9', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.220969'); 
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('05c42d4a-665f-4922-aeaa-b29b6b754ff0', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.221303'),
('5dd8b971-27f7-4bd7-ad6f-b76e01452dbd', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.223482'),
('06c1bbc4-375a-420b-8087-b73da16dd01d', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.223927'),
('218d56ca-ed10-4321-82d4-1d3dc11a76d3', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.223947'),
('1c1f7e8b-4d84-482f-9ae2-ad3d2f3a9be6', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.22452'),
('c748a0d3-3811-4ed8-81ca-f5003d4ff050', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.224571'),
('a4ca9a0c-8662-4cc0-a104-4e329750aa86', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.224601'),
('aa8a6fb4-430f-45fb-9db6-48156a88e107', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.224629'),
('87ea1898-be15-4423-9351-72188a0a2b4b', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.226838'),
('eaf5c0d8-e813-47e3-9017-16993f4b9547', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.229185'),
('d6d74f25-b355-46a6-9b15-733975f41cf5', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'diff', 280, 'PASS', U&'diff --git a/config/feature-flags.yml b/config/feature-flags.yml\000anew file mode 100644\000aindex 0000000..ebe9861\000a--- /dev/null\000a+++ b/config/feature-flags.yml\000a@@ -0,0 +1 @@\000a+feat: enable experimental flag - 2026-09-12T15:14:03Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.230284'),
('b9b134dc-9d28-4178-a00d-adb3eebfc9cc', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.231335'),
('f756ecf3-6b2c-4f91-8026-cb42d94f220e', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.231371'),
('a2e7c016-0ea1-4554-812b-ddbf180612c9', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.873833'),
('b4636bc3-6bdb-4a45-8c07-5023883db25b', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.877225'),
('66dd52c4-72c1-4c73-a8ce-a821e20b105b', '58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:04.87725'),
('d0d96b9f-ac17-4853-8ca3-a0540cb25b9e', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.877264'),
('a39a8701-9411-4a99-b2ef-f0dd4be665f5', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.882595'),
('dddb7176-49d1-4d14-980c-228b0c87924c', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.882899'),
('3bbaf430-6bdb-4a0a-b046-722e74485108', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.884979'),
('193127a3-d9a6-4267-8b61-c865d80caace', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.885519'),
('b2c728bf-ee95-409c-8df7-3c6ff5c40fcf', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.885545');         
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('09e4c9d0-cdd8-47a8-9f65-89f67f8a8a40', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.886153'),
('512370c7-96ee-42fa-985c-270da6055262', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.886216'),
('ad6cf56e-02df-4d5d-93bc-4235c0cacabc', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.886248'),
('b72e9e7c-fb15-4230-895d-14b9633bee58', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.886278'),
('34bcca4e-8b17-4f23-9891-c6112749d901', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.888133'),
('771978c9-8995-4608-88da-1b8ce86d9c52', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.890423'),
('fc72424d-466e-483d-bc38-739e49e16222', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'diff', 280, 'PASS', U&'diff --git a/scratch.txt b/scratch.txt\000anew file mode 100644\000aindex 0000000..53a1048\000a--- /dev/null\000a+++ b/scratch.txt\000a@@ -0,0 +1 @@\000a+chore: exploratory change, withdrawn - 2026-09-12T15:14:05Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.891553'),
('ae3306f2-7683-481e-9272-97bd2ec6567a', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.892629'),
('dbc4c988-3f02-44ca-a422-797bbe61b4b4', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:05.892668'),
('3cd0a2f3-df0c-456e-a683-7f95df7e4822', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:06.514138'),
('a4460269-e074-4ea1-ab1d-b924fac7b372', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:06.518979'),
('11accf74-6116-4a0f-a31a-60b9e5d93f04', '505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:06.519012'),
('c8b11f6c-aa29-4a72-9566-69fc543dcc35', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'allow-approved-push', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:07.492494'),
('d0ebe626-d6a6-4a0c-acda-216f2eceb0fd', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'enrich-commits', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.652237'),
('b5f79dfb-572d-4d41-bda6-98c8c98a5bac', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'url-rule', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.652654'),
('3b75f879-1de2-4835-9e2c-1ef1789567bc', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'push-permission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.654901'),
('4638e92a-99c9-429e-a504-70b18ab79fc5', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'commit-attribution', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.655344'),
('409facd7-c99c-4f84-8aae-0fdcee0ff7ae', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'empty-branch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.655363'),
('216d2d43-c7d0-42c4-995f-eb14135feba5', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'hidden-commits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.655916'),
('aa337be3-800f-4a50-8478-cd68092ee950', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'author-email', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.655973'),
('6f6b0df4-0f7d-440b-bb6a-a5e5ef24906c', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'trailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.656032'),
('459aa2cf-739b-42b0-b148-49614ef8d673', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'commit-message', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.656064');             
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('c616f232-fb39-4769-8548-5828e6160373', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'content-pattern-message', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.657849'),
('ad1cf6a7-a533-4cdf-a0fd-acabb287dba0', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'binary-blob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.660239'),
('188d2b09-bb54-4145-99b4-093b7cd93e15', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'diff', 280, 'PASS', U&'diff --git a/docs/faq.md b/docs/faq.md\000anew file mode 100644\000aindex 0000000..89c39fa\000a--- /dev/null\000a+++ b/docs/faq.md\000a@@ -0,0 +1 @@\000a+docs: answer the most common question - 2026-09-12T15:14:07Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.661363'),
('e76bb4e9-aa89-4b6a-9e7d-046c724ccb83', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'diff-scan', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.662426'),
('bf86a4a9-5f9b-4fcc-a0b2-65f505b912d9', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'gpg-signature', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:08.662453'),
('594a782c-f972-426b-a3dc-6a30ff4751f5', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'secret-scan', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:09.283819'),
('034fa618-f3b7-4c0b-bfb7-95cab4f96e69', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'content-pattern-diff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:09.289591'),
('21b0b19a-4478-4b02-b769-979ee718a460', 'd2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'validation-summary', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:09.289628'),
('0c85c682-7369-4d28-af9f-fb0eeb4aaa4a', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'url-rule', 100, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:11.592814'),
('853ce821-00e0-470c-8c56-b9ac018d822e', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'push-permission', 150, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.12648'),
('6277335e-b37b-4c91-98cd-688a03eb3837', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'commit-attribution', 160, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.127454'),
('a7692852-6eb0-4a24-b7a0-3a1e5c673c4b', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'prior-push-enrichment', 195, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.127522'),
('5088cf7c-c478-48a5-a531-b0cb5387d612', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'empty-branch', 210, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.129648'),
('0eaefcb4-4748-435d-9a97-58e4b2a4834b', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'hidden-commits', 220, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.131951'),
('1e968866-2e67-45d4-9267-41e5e66f0225', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'author-email', 250, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.132652'),
('0da26b17-6d2b-4710-b3a3-c0117ceef4a6', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'trailers', 255, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.132943'),
('bb83a9b3-087e-4920-b96b-3ff36905785c', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'commit-message', 260, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.13348'),
('2c203ee8-4b0a-4d61-b78a-fecd8feb1638', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'content-pattern-message', 265, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.136573'),
('813558a4-93d4-428a-ba62-c74797bb2b8a', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'commit-inspection', 270, 'PASS', NULL, NULL, NULL, U&'CREATE refs/heads/fixture/ssh-server-049311 0000000 -> 3a4d021\000a---LOG---\000aNew branch - tip commit by Fixture Developer <fixture-dev@example.com>', TIMESTAMP '2026-09-12 11:14:12.137022'),
('2b4f95bf-c4a9-4bf0-a8e5-a3b069e8bb24', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'diff', 280, 'PASS', U&'diff --git a/docs/ssh-transport.md b/docs/ssh-transport.md\000anew file mode 100644\000aindex 0000000..8d127c0\000a--- /dev/null\000a+++ b/docs/ssh-transport.md\000a@@ -0,0 +1 @@\000a+feat: pushed over the SSH transport - 2026-09-12T15:14:11Z\000a', NULL, NULL, U&'ref: refs/heads/fixture/ssh-server-049311\000a---LOG---\000arange: 0000000000000000000000000000000000000000..3a4d02167e721e4eff6b3cb22c44d8e0a02817bc\000a---LOG---\000alines: 7\000a---LOG---\000atype: auto', TIMESTAMP '2026-09-12 11:14:12.139305'); 
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('0e3176e2-070b-49d7-ac25-944789fed65a', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'binary-blob', 290, 'PASS', NULL, NULL, NULL, 'PASS: aggregate: refs/heads/fixture/ssh-server-049311', TIMESTAMP '2026-09-12 11:14:12.142321'),
('8fb1aebb-93a1-4166-a39f-b69c42fab430', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'diff-scan', 300, 'PASS', NULL, NULL, NULL, 'PASS: aggregate diff', TIMESTAMP '2026-09-12 11:14:12.144229'),
('fb10d989-9bb2-4123-8d8c-96dda9c5ca36', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'gpg-signature', 320, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.144435'),
('999e90ec-6a1d-4b41-8034-16608c58f5be', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'secret-scan', 340, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.789975'),
('8f5285fc-999e-43fa-a188-8517d1bffe7a', 'a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'content-pattern-diff', 345, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-12 11:14:12.793248');   
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
('github', 'R_kgDOUYEH0A', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-12 11:17:42.093143'),
('github', 'PR_kwDOUYEH0M8AAAABDQ04uQ', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-12 11:17:44.369502'),
('github', 'I_kwDOUYEH0M8AAAABQ-bhiw', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-12 11:17:49.109464');      
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
('gitlab', '86388993', 'fixture-dev', 'fogwall-fixture', TIMESTAMP '2026-09-12 11:17:57.987593');      
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
('87bc4724-4ab6-4902-b669-6cbec10c3608', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'yes', FALSE, FALSE, TIMESTAMP '2026-09-12 11:16:33.273548', '{"reviewed-content":"true","policy-compliance":"true"}'),
('17789b25-690f-42b3-aab8-eabfd63f865f', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'yes', FALSE, FALSE, TIMESTAMP '2026-09-12 11:16:44.54589', '{"reviewed-content":"true","policy-compliance":"true"}'),
('58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'REJECTION', 'reviewer', 'reviewer@example.com', 'no', FALSE, FALSE, TIMESTAMP '2026-09-12 11:16:52.124489', NULL),
('a8ff12a0-f954-46f3-81ad-fe248d1a61df', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'yes', FALSE, FALSE, TIMESTAMP '2026-09-12 11:16:59.814555', '{"reviewed-content":"true","policy-compliance":"true"}'),
('505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'CANCELLATION', 'dev', NULL, NULL, FALSE, FALSE, TIMESTAMP '2026-09-12 11:17:21.603371', NULL),
('d2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', 'APPROVAL', 'dev', 'fixture-dev@example.com', 'yes', FALSE, FALSE, TIMESTAMP '2026-09-12 11:17:28.926677', '{"reviewed-content":"true","policy-compliance":"true"}');           
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
('dev', 'github', 'fixture-dev-alt', FALSE),
('dev', 'codeberg', 'fixture-dev', FALSE);   
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
('63da4e1a-ac28-431d-881d-f4ca7ecd20c2', TIMESTAMP '2026-09-12 11:13:11.814663', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1cbba4a3-d783-4712-a5a0-374185d58d60', TIMESTAMP '2026-09-12 11:13:11.979703', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('833628d5-b486-4b30-a820-3210f065cbc1', TIMESTAMP '2026-09-12 11:13:16.26199', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1d14741d-b104-4bd3-9952-c01ce473ca48', TIMESTAMP '2026-09-12 11:13:16.532661', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('20b50ebd-fb3f-4778-9c91-f1766d39af87', TIMESTAMP '2026-09-12 11:13:18.282108', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('32e70f2c-f4bc-4586-a241-1fba6e6ac832', TIMESTAMP '2026-09-12 11:13:18.49059', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('eed0cede-0a80-43d2-94f9-cbfda655e078', TIMESTAMP '2026-09-12 11:13:20.105574', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('afc931cf-9851-46b4-92e5-47b80a8acd22', TIMESTAMP '2026-09-12 11:13:20.266574', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('2fa09eaf-314d-467a-98da-e02f1fbc2b7f', TIMESTAMP '2026-09-12 11:13:22.943875', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c2f10a22-73c7-4cae-8f1d-7fc694e6c252', TIMESTAMP '2026-09-12 11:13:23.094437', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d11f69e2-6731-435d-87e3-f87c7aa678f5', TIMESTAMP '2026-09-12 11:13:24.655338', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d54381ef-6371-4b9e-9cb4-8897eb3a74d7', TIMESTAMP '2026-09-12 11:13:24.804905', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('14f73ada-b651-4dc0-bc27-db279046c802', TIMESTAMP '2026-09-12 11:13:26.679352', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('fe5c47d3-f00c-48fe-b756-c9743e7b7808', TIMESTAMP '2026-09-12 11:13:26.888358', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('cc44c50b-97d8-4cfc-8cec-4edf4c39d3b3', TIMESTAMP '2026-09-12 11:13:30.276913', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('b476339f-02e5-4fc9-ab51-7d2c5310a4d8', TIMESTAMP '2026-09-12 11:13:30.416436', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('027537d2-5037-42d3-b3b2-7a342fdf13de', TIMESTAMP '2026-09-12 11:13:31.910255', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('05df9c24-4977-4c13-abcb-05e1e559ad56', TIMESTAMP '2026-09-12 11:13:32.0568', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('a3f0a921-315b-4954-a181-d1d69436eeea', TIMESTAMP '2026-09-12 11:13:33.635361', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('e0f765d9-833e-4e77-b117-5651bf118680', TIMESTAMP '2026-09-12 11:13:33.781877', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('566b4a38-74a9-4939-9f61-8038a49b225f', TIMESTAMP '2026-09-12 11:13:36.378803', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('750f5d95-8873-4d97-b0ba-75dec3a949ff', TIMESTAMP '2026-09-12 11:13:36.549451', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('402edfc2-737c-4bd4-95a2-6b09d8783f76', TIMESTAMP '2026-09-12 11:13:38.079391', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c661c39e-d8a1-4aea-ae1c-4612ef0c40d3', TIMESTAMP '2026-09-12 11:13:38.33946', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('08d3db22-1a4a-4e48-91c4-2cb5962db586', TIMESTAMP '2026-09-12 11:13:41.597522', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d1a5e65e-4824-4ba2-9200-a17629c6b58b', TIMESTAMP '2026-09-12 11:13:41.769316', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL); 
INSERT INTO "PUBLIC"."FETCH_RECORDS" VALUES
('738ad6ec-fe7a-4cda-afd1-d52d77be0106', TIMESTAMP '2026-09-12 11:13:45.442545', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1e990210-97a2-40c4-81d5-7431fda51e6a', TIMESTAMP '2026-09-12 11:13:45.576204', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('567fed40-3d3e-4547-a0ee-e915af80b427', TIMESTAMP '2026-09-12 11:13:48.228296', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('31685722-9ce4-4e16-ae03-049396da6a09', TIMESTAMP '2026-09-12 11:13:48.372925', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1034c63d-1fb9-4335-b9bf-9c2a29595697', TIMESTAMP '2026-09-12 11:13:50.382938', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('54770aec-b37a-4501-92d3-6a81bfd35520', TIMESTAMP '2026-09-12 11:13:50.487445', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('f8b4f0bb-e832-4d60-a650-0128e30f6951', TIMESTAMP '2026-09-12 11:13:54.342062', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('edf5bb28-8f06-41a5-b186-1ff7f13646e7', TIMESTAMP '2026-09-12 11:13:54.735222', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('e234f246-1002-4282-b799-c293f05cef69', TIMESTAMP '2026-09-12 11:13:58.724132', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('dbd1a5b9-708f-474e-91f4-1290dcfe99f8', TIMESTAMP '2026-09-12 11:13:58.858262', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d966dffc-7972-4bc6-9343-1064539aa3ad', TIMESTAMP '2026-09-12 11:14:01.793761', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('9fdbd1a0-c931-4303-8242-561af232b65f', TIMESTAMP '2026-09-12 11:14:01.929141', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('5184d4a3-587f-485d-ab0b-9b72c380b6af', TIMESTAMP '2026-09-12 11:14:03.424522', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('6c09b5db-413c-4db1-9aed-d3f56999b208', TIMESTAMP '2026-09-12 11:14:03.578236', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('4f7d3054-5a5b-4718-8f2c-fd4afada10d6', TIMESTAMP '2026-09-12 11:14:05.081616', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('767c495e-8aeb-4824-9cb6-ec9bc4c2f592', TIMESTAMP '2026-09-12 11:14:05.229882', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('7bfdf719-c676-4bc5-b1a4-e19119dfdd66', TIMESTAMP '2026-09-12 11:14:06.703891', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('f481a2c7-ce22-424e-9dad-1e69c67210ef', TIMESTAMP '2026-09-12 11:14:06.843538', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL);  
CREATE INDEX "PUBLIC"."IDX_FETCH_RECORDS_TIMESTAMP" ON "PUBLIC"."FETCH_RECORDS"("TIMESTAMP" NULLS FIRST);      
CREATE INDEX "PUBLIC"."IDX_FETCH_RECORDS_PROVIDER_REPO" ON "PUBLIC"."FETCH_RECORDS"("PROVIDER" NULLS FIRST, "OWNER" NULLS FIRST, "REPO_NAME" NULLS FIRST);     
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
    "ENTITY_ID" CHARACTER VARYING(36),
    "MERGE_COMMIT_SHA" CHARACTER VARYING(64)
); 
ALTER TABLE "PUBLIC"."SCM_API_ACTION_RECORDS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F8" PRIMARY KEY("ID");       
-- 28 +/- SELECT COUNT(*) FROM PUBLIC.SCM_API_ACTION_RECORDS;  
INSERT INTO "PUBLIC"."SCM_API_ACTION_RECORDS" VALUES
('543b8aa4-664e-492b-9572-162a11cb8078', TIMESTAMP '2026-09-12 11:17:43.591875', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'createPullRequest', 'R_kgDOUYEH0A', 'REPOSITORY', 'FORWARDED', NULL, '{"input":{"baseRefName":"main","body":"Opened through fogwall with gh.","draft":false,"headRefName":"fixture/gh-258692","maintainerCanModify":true,"repositoryId":"R_kgDOUYEH0A","title":"Proposed via gh"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'a888ba0d-3b84-4109-8687-c8f51bfba382', NULL),
('b01185d2-12a8-470c-86c9-96ccad95b04f', TIMESTAMP '2026-09-12 11:17:45.635171', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'updatePullRequest', 'PR_kwDOUYEH0M8AAAABDQ04uQ', 'PULL_REQUEST', 'FORWARDED', NULL, '{"input":{"pullRequestId":"PR_kwDOUYEH0M8AAAABDQ04uQ","title":"Proposed via gh (edited)"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'a888ba0d-3b84-4109-8687-c8f51bfba382', NULL),
('a007ab33-75bf-4d81-b9e2-b3e65f856955', TIMESTAMP '2026-09-12 11:17:47.162424', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'closePullRequest', 'PR_kwDOUYEH0M8AAAABDQ04uQ', 'PULL_REQUEST', 'FORWARDED', NULL, '{"input":{"pullRequestId":"PR_kwDOUYEH0M8AAAABDQ04uQ"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'a888ba0d-3b84-4109-8687-c8f51bfba382', NULL),
('c44382a5-2525-41cb-b149-bdb27550dbac', TIMESTAMP '2026-09-12 11:17:48.514322', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'createIssue', 'R_kgDOUYEH0A', 'REPOSITORY', 'FORWARDED', NULL, '{"input":{"body":"Opened through fogwall with gh.","repositoryId":"R_kgDOUYEH0A","title":"Reported via gh"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'd11e2239-05a1-4a86-badf-81a2044914fc', NULL),
('aea8c66d-46f5-4085-8f20-4d7c051f1b3a', TIMESTAMP '2026-09-12 11:17:50.270787', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'updateIssue', 'I_kwDOUYEH0M8AAAABQ-bhiw', 'ISSUE', 'FORWARDED', NULL, '{"input":{"id":"I_kwDOUYEH0M8AAAABQ-bhiw","body":"Edited through fogwall with gh."}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'd11e2239-05a1-4a86-badf-81a2044914fc', NULL),
('76eaa939-c420-4f2c-8ce6-beb1ff51a9a5', TIMESTAMP '2026-09-12 11:17:51.887184', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'closeIssue', 'I_kwDOUYEH0M8AAAABQ-bhiw', 'ISSUE', 'FORWARDED', NULL, '{"input":{"issueId":"I_kwDOUYEH0M8AAAABQ-bhiw"}}', 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', 200, 'd11e2239-05a1-4a86-badf-81a2044914fc', NULL),
('bba9aaac-c6bd-459e-9531-47feb05fa53b', TIMESTAMP '2026-09-12 11:17:52.930818', 'github', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'createIssue', 'R_kgDOUYEH0A', 'REPOSITORY', 'REJECTED', U&'Content rejected: secret detected in submitted content: [generic-api-key]\000a  match:  aws_secret_access_key = [REDACTED]"; secret detected in submitted content: [generic-api-key]\000a  match:  aws_secret_access_key = [REDACTED]; secret detected in submitted content: [aws-access-token]\000a  match:  [REDACTED]', NULL, 'GitHub CLI 2.100.0', 'GH_CLI', '2.100.0', NULL, NULL, NULL),
('f4e50b68-954d-485c-b2a9-a1955ecfbc92', TIMESTAMP '2026-09-12 11:17:59.027311', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'merge_requests.create', NULL, NULL, 'FORWARDED', NULL, '{"title":"Proposed via glab","description":"Opened through fogwall with glab.","source_branch":"fixture/glab-272943","target_branch":"main","labels":"","target_project_id":86388993}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 201, '2370119e-8596-40b7-a9f5-48665eff9210', NULL),
('4f25ccd2-8c8e-4ca4-943a-8299e80df81a', TIMESTAMP '2026-09-12 11:18:00.610796', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'merge_requests.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"Proposed via glab (edited)"}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, '2370119e-8596-40b7-a9f5-48665eff9210', NULL);            
INSERT INTO "PUBLIC"."SCM_API_ACTION_RECORDS" VALUES
('2abac44c-fcb5-4290-b927-44c7af43305e', TIMESTAMP '2026-09-12 11:18:02.083037', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'merge_requests.update', NULL, NULL, 'FORWARDED', NULL, '{"state_event":"close"}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, '2370119e-8596-40b7-a9f5-48665eff9210', NULL),
('0e15bc5e-2aa1-4dea-ade8-5a49b0bc80fe', TIMESTAMP '2026-09-12 11:18:03.578001', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'FORWARDED', NULL, '{"title":"Reported via glab","description":"Opened through fogwall with glab.","labels":""}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 201, '76d8144c-db56-46cb-94cb-dc08a2ac5ccc', NULL),
('c73d3bf1-5eb6-414f-a887-ba7bb952958a', TIMESTAMP '2026-09-12 11:18:05.155602', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"description":"Edited through fogwall with glab."}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, '76d8144c-db56-46cb-94cb-dc08a2ac5ccc', NULL),
('3d2de88d-ca4b-471b-acdb-27d2059c6985', TIMESTAMP '2026-09-12 11:18:06.793329', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"state_event":"close"}', 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', 200, '76d8144c-db56-46cb-94cb-dc08a2ac5ccc', NULL),
('fee2d8db-0ab6-484d-aafd-ceb11d703449', TIMESTAMP '2026-09-12 11:18:07.837109', 'gitlab', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'REJECTED', 'Content rejected: blocked term: "internal.corp.example.com" in description', NULL, 'glab/v1.116.0 (linux, amd64)', 'GLAB_CLI', '1.116.0', NULL, NULL, NULL),
('c3dc989d-a91f-4563-8ecf-e9e7a1cb2385', TIMESTAMP '2026-09-12 11:18:24.139944', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.create', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"base":"main","body":"Opened through fogwall with fj.","due_date":null,"head":"fixture/fj-287855","labels":null,"milestone":null,"title":"Proposed via fj"}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, '817a0f19-afdc-466d-8cbb-def466da1323', NULL),
('87d46548-adca-4b4a-9f7a-6adb79af9947', TIMESTAMP '2026-09-12 11:18:25.20439', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":null,"due_date":null,"milestone":null,"ref":null,"state":null,"title":"Proposed via fj (edited)","unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, '817a0f19-afdc-466d-8cbb-def466da1323', NULL),
('8dd8c100-c95b-43ee-9424-2171b8d2ab5d', TIMESTAMP '2026-09-12 11:18:26.309811', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":null,"due_date":null,"milestone":null,"ref":null,"state":"closed","title":null,"unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, '817a0f19-afdc-466d-8cbb-def466da1323', NULL),
('f1cb43ad-1f58-46d5-9501-0c12cec6e273', TIMESTAMP '2026-09-12 11:18:31.513358', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":"Opened through fogwall with fj.","closed":null,"due_date":null,"labels":null,"milestone":null,"ref":null,"title":"Reported via fj"}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, 'd26b25cc-5988-4720-a108-94abd2056b94', NULL),
('aae32ba7-adc4-47ab-85be-af1b1f538c9b', TIMESTAMP '2026-09-12 11:18:32.738953', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":"Edited through fogwall with fj.","due_date":null,"milestone":null,"ref":null,"state":null,"title":null,"unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, 'd26b25cc-5988-4720-a108-94abd2056b94', NULL);   
INSERT INTO "PUBLIC"."SCM_API_ACTION_RECORDS" VALUES
('d268fb77-283c-475f-9d98-b60ee3d1be12', TIMESTAMP '2026-09-12 11:18:33.703729', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"assignee":null,"assignees":null,"body":null,"due_date":null,"milestone":null,"ref":null,"state":"closed","title":null,"unset_due_date":null,"updated_at":null}', 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', 201, 'd26b25cc-5988-4720-a108-94abd2056b94', NULL),
('1ffa09e8-0439-45fb-acf6-40befc6888af', TIMESTAMP '2026-09-12 11:18:34.900257', 'codeberg', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'REJECTED', 'Content rejected: possible IBAN (Generic) detected in submitted content', NULL, 'forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)', 'FJ_CLI', '0.6.0', NULL, NULL, NULL),
('0fdc86c0-ab15-4bdd-aac2-09aeff2bbee0', TIMESTAMP '2026-09-12 11:18:39.084264', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.create', NULL, NULL, 'FORWARDED', NULL, '{"head":"fixture/tea-314909","base":"main","title":"Proposed via tea","body":"Opened through fogwall with tea.","assignee":"","assignees":[""],"reviewers":null,"team_reviewers":null,"milestone":0,"labels":[],"due_date":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, '91669dc7-2d58-4828-86dc-5fbab5632ca1', NULL),
('6f499cb2-a7ab-4b24-89ad-4104951cafe1', TIMESTAMP '2026-09-12 11:18:40.026563', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"Proposed via tea (edited)","body":null,"base":"","assignee":"","assignees":null,"milestone":0,"labels":null,"state":null,"due_date":null,"unset_due_date":null,"allow_maintainer_edit":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, '91669dc7-2d58-4828-86dc-5fbab5632ca1', NULL),
('f38a6d8b-3592-43f3-8533-416965551806', TIMESTAMP '2026-09-12 11:18:41.255169', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'pulls.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"","body":null,"base":"","assignee":"","assignees":null,"milestone":0,"labels":null,"state":"closed","due_date":null,"unset_due_date":null,"allow_maintainer_edit":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, '91669dc7-2d58-4828-86dc-5fbab5632ca1', NULL),
('7bef0820-b151-4fb2-9bed-d120919dd6f1', TIMESTAMP '2026-09-12 11:18:42.746266', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'FORWARDED', NULL, '{"title":"Reported via tea","body":"Opened through fogwall with tea.","ref":"","assignees":[""],"due_date":null,"milestone":0,"labels":[],"closed":false}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, 'd54916b9-d26d-47ec-81a4-17f05cb440ec', NULL),
('676582b5-b753-4930-ad38-a9476dfa9d9f', TIMESTAMP '2026-09-12 11:18:43.698151', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"","body":"Edited through fogwall with tea.","ref":null,"assignees":null,"milestone":null,"state":null,"due_date":null,"unset_due_date":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, 'd54916b9-d26d-47ec-81a4-17f05cb440ec', NULL),
('dedec869-cc6b-4c6a-9050-5222b9ee4ca7', TIMESTAMP '2026-09-12 11:18:44.772101', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.update', NULL, NULL, 'FORWARDED', NULL, '{"title":"","body":null,"ref":null,"assignees":null,"milestone":null,"state":"closed","due_date":null,"unset_due_date":null}', 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', 201, 'd54916b9-d26d-47ec-81a4-17f05cb440ec', NULL),
('832288b5-289a-452b-8e06-cf2f0abd3333', TIMESTAMP '2026-09-12 11:18:45.76824', 'gitea', 'fixture-dev', 'dev', 'fixture-dev', 'fogwall-fixture', 'issues.create', NULL, NULL, 'REJECTED', 'Content rejected: blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in body', NULL, 'tea/0.15.1 (linux/amd64) go-sdk/v1.2.0', 'TEA_CLI', '0.15.1', NULL, NULL, NULL);           
CREATE INDEX "PUBLIC"."IDX_SCM_API_ACTION_RECORDS_RESOLVED_USER" ON "PUBLIC"."SCM_API_ACTION_RECORDS"("RESOLVED_USER" NULLS FIRST);            
CREATE INDEX "PUBLIC"."IDX_SCM_API_ACTION_RECORDS_TIMESTAMP" ON "PUBLIC"."SCM_API_ACTION_RECORDS"("TIMESTAMP" NULLS FIRST);    
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
('119ce45e-69e8-4458-8a80-9d6979fd2762', 'dev', 'github', 'PUSH', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('d3fc61cd-b37e-4a76-9d66-43eee0803737', 'dev', 'github', 'SELF_CERTIFY', 'CONFIG', 'SLUG', '/fixture-dev/fogwall-fixture', 'LITERAL'),
('69c86dbe-6642-4baf-8ea0-ef236e407789', 'dev', 'gitea', 'PUSH', 'CONFIG', 'OWNER', 'fixture-dev', 'LITERAL'),
('24ec2d58-24db-46c8-b680-d575a4d48207', 'dev', 'github', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('eabf0870-a3cd-4ba9-9d5a-6616d7fc012e', 'dev', 'gitlab', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('2fc7d53a-7297-442e-aaa4-3b4e8712ae4f', 'dev', 'codeberg', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('d865ef33-2777-4219-aa51-b7170faf410a', 'dev', 'gitea', 'PROPOSE', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('b0de746c-b0d3-414d-9f0b-88c6e480de76', 'reviewer', 'github', 'REVIEW', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('d31827c1-2a88-4d4c-b7a0-c6104d0fbc88', 'reviewer', 'gitlab', 'PUSH_AND_REVIEW', 'CONFIG', 'SLUG', '/fixture-dev/fogwall-fixture', 'LITERAL');  
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
('8abea890-e737-48e5-8218-24d6be990ab8', 'platform-reviewers', 'Reviewers for the fixture-dev org', 'CONFIG'),
('da80ce0f-ca82-451a-a6ea-fe9d552970d9', 'gitlab-contributors', 'Push access to GitLab test repos', 'CONFIG');  
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
('d1656efb-37ff-4a02-8062-b28f43f2452a', '8abea890-e737-48e5-8218-24d6be990ab8', 'github', 'SLUG', '/fixture-dev/.*', 'REGEX', 'REVIEW'),
('7f401140-b5e2-43cd-b3b5-247fa5ce0f5a', 'da80ce0f-ca82-451a-a6ea-fe9d552970d9', 'gitlab', 'SLUG', '/fixture-dev/*', 'GLOB', 'PUSH');
CREATE INDEX "PUBLIC"."IDX_GROUP_PERMISSIONS_GROUP_ID" ON "PUBLIC"."GROUP_PERMISSIONS"("GROUP_ID" NULLS FIRST);
CREATE INDEX "PUBLIC"."IDX_GROUP_PERMISSIONS_PROVIDER" ON "PUBLIC"."GROUP_PERMISSIONS"("PROVIDER" NULLS FIRST);
CREATE CACHED TABLE "PUBLIC"."GROUP_MEMBERS"(
    "GROUP_ID" CHARACTER VARYING(36) NOT NULL,
    "USERNAME" CHARACTER VARYING(255) NOT NULL
); 
ALTER TABLE "PUBLIC"."GROUP_MEMBERS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F180" PRIMARY KEY("GROUP_ID", "USERNAME");            
-- 3 +/- SELECT COUNT(*) FROM PUBLIC.GROUP_MEMBERS;            
INSERT INTO "PUBLIC"."GROUP_MEMBERS" VALUES
('8abea890-e737-48e5-8218-24d6be990ab8', 'reviewer'),
('8abea890-e737-48e5-8218-24d6be990ab8', 'admin'),
('da80ce0f-ca82-451a-a6ea-fe9d552970d9', 'dev');          
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
('f4155503-361e-453a-afdc-7134dc3f488c', 'e73491d5d6d1fd03243fcb14ce01608e263a96e6', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'noreply@example.com', 'Fixture Developer', 'noreply@example.com', U&'feat: this commit has a noreply author\000a\000aSigned-off-by: Fixture Developer <noreply@example.com>\000a', TIMESTAMP '2026-09-12 11:13:12', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsCAAKCRA3V+o4iAJ3\000a59SUAQC6UFnR7TnLcHl7KyX/dIkZCyDhSfH98A5iOa2K7eTxlAEA+O6zFrM0qHdw\000a7yc6u4oFrXKfquoOwjZM4u3gBPrBWwk=\000a=esGK\000a-----END PGP SIGNATURE-----', 'Fixture Developer <noreply@example.com>', NULL),
('b17296e0-7c11-4e44-9c01-28f6ff1f3fdd', '5056db0c97662a318c5d9d96b96e6cc239ac6f3b', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'developer@internal.corp.net', 'Fixture Developer', 'developer@internal.corp.net', U&'feat: this commit comes from an unapproved domain\000a\000aSigned-off-by: Fixture Developer <developer@internal.corp.net>\000a', TIMESTAMP '2026-09-12 11:13:16', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsDAAKCRA3V+o4iAJ3\000a56iEAQCYlbeNW1cBqJFNwOG+sBaqO+zLz5yyprR8QbbrjOB4/QD/Za5/wIqeQPl3\000aKDVRCUl61ei7oqori/TjORLUHr4s6A0=\000a=Qr+c\000a-----END PGP SIGNATURE-----', 'Fixture Developer <developer@internal.corp.net>', NULL),
('95218505-5d18-4deb-a818-bf64b4d8ebdd', '35dd731508157d682aead2504e2feb0e8bf015b0', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'WIP: still working on this feature\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:18', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsDgAKCRA3V+o4iAJ3\000a5963AQDWslp7CDp0U6SADFTG5H680WN38loQhyUY+HssyPelGwEA7MeyBOs+oxkW\000a8/w/XSDh4kJokQUwRliUmjPMR+vsGAY=\000a=tdyM\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('3356cb56-d321-4851-b7d5-0f9584e6d1da', '746dece9575c65daa8d6c4b090fc597ac590bcf4', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: rotate token=[REDACTED] in CI config\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:20', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsEAAKCRA3V+o4iAJ3\000a50owAQCTeyyQc44zAjGj3MyRp4b2bv/uhCQpy0xIScb3hfDbpwEA+EoF1mgQ1DjA\000adTvn1jAonz+Soa24RAzhhbMcetmE5Qg=\000a=MxFW\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('ecef2b91-2c4f-47dd-b685-39bf856dc783', '029f78c87db59e14da8e3404247e1dc953f796dc', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add deployment credentials\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:23', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsEwAKCRA3V+o4iAJ3\000a5999APwPSO75vUnSzSF3Guo85yXGX3UM+xt2U5wi4AsenRkH6wD8Du+Lkb+W2Dub\000atQk/19DAeEJpnSJAeObrQC7lL1TqJwE=\000a=8II9\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('0a9541ee-f94b-44cb-b4c0-9fc09bca1421', '6647aa6e91965c59c5f097af6bff76968bef60ac', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add upstream config\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:24', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsFAAKCRA3V+o4iAJ3\000a51upAP9wVHAWVrGw3NCf93neK783ZBj776zf2TXEOz9vg3XS0AEAoBOBytvJjDca\000aoMpm6SpvZ73Ql9MX6nxzaEmpjvwAhwk=\000a=Br4v\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('4c4022a2-303e-447b-9454-7d0668f39bc0', '10c8241c765d3afdcb81b1ac0ab892876f035e99', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add deployment script\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:27', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsFwAKCRA3V+o4iAJ3\000a5y7lAQCAw0vjEhdlUkuMybK1zdpNFLPmRmobOgOKVEYiUaTpSQEArk1EnvvZXhHC\000afYiopMML8iWWl5fzmyBWyl+dEYunoQs=\000a=aCub\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);          
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('9f83faf1-9327-46e0-ae96-06bf143f0a57', '2a2c31d9add6690ff310b60b79cf04d67f778306', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: forgot to sign off\000a', TIMESTAMP '2026-09-12 11:13:30', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsGgAKCRA3V+o4iAJ3\000a58+sAQDf53QAy1+KUY8Q9+66jny8YE624PucMl64ZchWk9xrkAD/ZiCp+cu83MsX\000aOvMYSpx/uMWiNlEctVHtcQHvIVpVYgI=\000a=1cWQ\000a-----END PGP SIGNATURE-----', NULL, NULL),
('349b4522-a93b-4e06-a254-a4af50c0dd1f', '697ab075fe256229375bf2fb28ee4bd4fc69046f', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: signed off by the wrong person\000a\000aSigned-off-by: Someone Else <someone.else@example.com>\000a', TIMESTAMP '2026-09-12 11:13:32', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsHAAKCRA3V+o4iAJ3\000a5yVYAP0dWl1mJMCiq8ZDZXF6wv8oTfhtulEuzYmah0Q1V4PhMQD9G6Ou/2Suist4\000aEpACskCWk3qjM0Jg6oTdH/CTTJA33g4=\000a=UbwD\000a-----END PGP SIGNATURE-----', 'Someone Else <someone.else@example.com>', NULL),
('59cc56fb-cdaf-4a76-a97c-5f7d8f59214c', '918a9799e901a7731a48a771f9ebbd473bbb8fbc', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: paired with an outside contractor\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000aCo-authored-by: Contractor <contractor@outside.example.net>\000a', TIMESTAMP '2026-09-12 11:13:33', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsHQAKCRA3V+o4iAJ3\000a582VAP0SsWlvHHGnxLQkFuoaTpXnHaooklrtwlFus+OCAaSeewD/dkiDlRjaTkyM\000awI6h7JTyiYFQFzrNAxNusS+8AhD83Qo=\000a=79uj\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', 'Contractor <contractor@outside.example.net>'),
('360cb445-29a9-45c7-adcc-34abd4403922', 'ae5d47b13e70e41d66fbe7b678fc5ad3c3ab2342', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: pair-programmed with an allow-listed co-author\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000aCo-authored-by: Claude <noreply@anthropic.com>\000aCo-authored-by: Pair Partner <pair@example.com>\000a', TIMESTAMP '2026-09-12 11:13:36', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIAAKCRA3V+o4iAJ3\000a53TlAQDTv1FDlCOxpLsvNpt5lCpplAKYzcJGveFph5IQEqdcgAD+N+fuoQmGE6fP\000aq3u5Mq62ZGhIi8Rz1UphzgJlXJBC8gg=\000a=65SF\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', U&'Claude <noreply@anthropic.com>\000aPair Partner <pair@example.com>'),
('119f4b18-ac37-4e70-8b98-4df8964d7da4', '15d5a7010abe0e146564ea3f9e77a51881b3382c', 'dc3d91dd324448ac9d9ae507a478e2100774ed78', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 6 \2014 missing DCO sign-off\000a', TIMESTAMP '2026-09-12 11:13:39', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIwAKCRA3V+o4iAJ3\000a59HhAP9pPSwcZNaLvbBVbCyd8jMEsiuxcZ/wRausGjak5C7ibgEAurxHzABAaYac\000awwcNMebuCnku9Lm3ogmoNSxQsqmUlQU=\000a=fe4y\000a-----END PGP SIGNATURE-----', NULL, NULL),
('119f4b18-ac37-4e70-8b98-4df8964d7da4', 'dc3d91dd324448ac9d9ae507a478e2100774ed78', '78558a19dd666095146727aa943e4dc6a6cf51c7', 'Fixture Developer', 'unregistered@example.com', 'Fixture Developer', 'unregistered@example.com', U&'test: commit 5 \2014 unregistered commit email\000a\000aSigned-off-by: Fixture Developer <unregistered@example.com>\000a', TIMESTAMP '2026-09-12 11:13:39', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIwAKCRA3V+o4iAJ3\000a54CuAQC3PPEsPQSWL5lT4RC6DTDMW7y+Ug7xNK4FZsE33tcPKgEA4MlNpEinrAlc\000aIiXf4njVcO8wYVwHPVnLTEGd+wx4wQ4=\000a=8NCc\000a-----END PGP SIGNATURE-----', 'Fixture Developer <unregistered@example.com>', NULL);       
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('119f4b18-ac37-4e70-8b98-4df8964d7da4', '78558a19dd666095146727aa943e4dc6a6cf51c7', '0979fb20c5f62daac2adceda93e4c139f514cbc6', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 4 \2014 blocked hostname in diff\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:39', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIwAKCRA3V+o4iAJ3\000a5+7vAP9zkluxcdU68H70r7QV8QRTvbEMUQvc2ULAvpVUFYThUAD/S7jbJMG0OFtJ\000aKf55XJ4EZ5vOsMYNVB/pxz7gvdh2Qw0=\000a=JY3q\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('119f4b18-ac37-4e70-8b98-4df8964d7da4', '0979fb20c5f62daac2adceda93e4c139f514cbc6', 'bd7b8a2c8049e502c6d8ffd7c103ff9f8255fd13', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 3 \2014 github pat in diff\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:38', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIgAKCRA3V+o4iAJ3\000a5+ufAP0f9XuS1T9YZw70WVfBg2MvE0d3b70m1iSj+NHxn1WXHAEA2fYTFhCJcve/\000aJ7aXj2JARd37EbOuSRpKvFshRJ2lOwQ=\000a=YQyZ\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('119f4b18-ac37-4e70-8b98-4df8964d7da4', 'bd7b8a2c8049e502c6d8ffd7c103ff9f8255fd13', 'eb00f2286ac82896ad4c7a704fe2c5758069c23f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'WIP: commit 2 \2014 bad commit message\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:38', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIgAKCRA3V+o4iAJ3\000a5/NfAP9eCy7biluR9z/4mjs2LdPRIRd+O1uoFIGf+4Ve6sGMzgEA3o4L8ehrs4XS\000aSbBx6rqGsFEqrvQmqIsx8JlpmV/oqAI=\000a=S5It\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('119f4b18-ac37-4e70-8b98-4df8964d7da4', 'eb00f2286ac82896ad4c7a704fe2c5758069c23f', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'noreply@example.com', 'Fixture Developer', 'noreply@example.com', U&'test: commit 1 \2014 noreply author email\000a\000aSigned-off-by: Fixture Developer <noreply@example.com>\000a', TIMESTAMP '2026-09-12 11:13:38', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsIgAKCRA3V+o4iAJ3\000a5z2hAQDmec8Wen0cvc7ucdz0TXCI95xXh5P8BPXfzZWEtVLh8gEA6HB0FZMzYSZ2\000a2ofdB0gibYkHBQu4OAxQRQWFgh0klwU=\000a=UY/l\000a-----END PGP SIGNATURE-----', 'Fixture Developer <noreply@example.com>', NULL),
('053840be-43a1-4fe9-bedf-dd3f3adeeba3', '23958d0c6e6b79013a71c97059b8e5a1efc590e4', '4c6c6c4f6d161643f4108d3e09a3bd9a85b5fb67', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: identity resolution \2014 codeberg unresolved\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:41', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsJQAKCRA3V+o4iAJ3\000a57VqAQDQZPmnb1qkM+sU4MMIDAc/AQ2RiEojQdigG953ZVtxUwEAmT7eXC4IWyT0\000aNYvqkT8USCRjRHqqTf5ZDzGe2YRTsgs=\000a=1ovc\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('ac3dbc7e-fb2d-4fc0-8427-b1830a045c2e', 'a5ba885b7c9e00f5423e3b838450c24d6c500b4e', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: add release notes stub\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:45', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsKQAKCRA3V+o4iAJ3\000a5+7FAQCDGcTgLtdMrExJFU2dQdbBRqxn1uIgCKBZrzSyAjVZ1AD9HMJMem8HlcPQ\000aNdKzAVKukfoAqluqRJpzVvO0Bt/bwg0=\000a=tVeL\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('e21b2f4f-b1b7-4386-bd1c-8a42191ffc2e', '3568d60be4e65a34b71eedd0d39ba696544ce968', 'ad84b642be6bcb0496adb3cef02642732ff95d54', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: touch readme via fogwall\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:50', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsLgAKCRA3V+o4iAJ3\000a588aAPwJkauUHUJbmSL59J3RuaQDD+mxJ8w1cUws2hP2uhJsYQD/bhvaKi7yQ+hU\000aPUkBJIVuI46LHE6CgeqBNhr2N9750QM=\000a=rK6y\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);             
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('f81fa4af-01c7-403e-8f76-7f7d5bd0ab8a', '5947450c2a6886eea1d712af49a4925108f1962f', 'dabea7fbbe44a20b4c8090d0d9db9ccc26d68c2c', 'Fixture Developer', 'unregistered@example.com', 'Fixture Developer', 'unregistered@example.com', U&'test: identity resolution \2014 gitlab resolved, email unregistered\000a\000aSigned-off-by: Fixture Developer <unregistered@example.com>\000a', TIMESTAMP '2026-09-12 11:13:55', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHQEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsMwAKCRA3V+o4iAJ3\000a5+1gAP94SQ2hlo7mQsq+czqFtIYGBz8faoxCZ/PGodI1B/XTvwD3dXhZ4QTqgxDs\000aAmcHbbHMqXn0cHeIMzKIv7+LZemgBw==\000a=T/35\000a-----END PGP SIGNATURE-----', 'Fixture Developer <unregistered@example.com>', NULL),
('87bc4724-4ab6-4902-b669-6cbec10c3608', '0522d2197e3cf7a0ffc59ae715fb184b0e1a5293', 'c600222a21646e45342bf19fe77ac6d90a113cc3', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'fix(gamma): third of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:59', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsNwAKCRA3V+o4iAJ3\000a50hOAP9nRRBM7UnOKuKHqifJWmxmmzrX5dbMOietbRy54NUW6wEApWYKcNxIk8e9\000anvhLrdJAP8Ud/7lX1TL+DNGNpO7tigw=\000a=s3kn\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('87bc4724-4ab6-4902-b669-6cbec10c3608', 'c600222a21646e45342bf19fe77ac6d90a113cc3', '242aca1d97c35c38f537d67cc1d2eb41c65b26bd', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat(beta): second of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:59', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsNwAKCRA3V+o4iAJ3\000a54hwAP4g4cLKZV4M4w8ofMYZ8mnz3vzXWc782RQBW3uRDaT1fwD9Eg5ZUB/IjNR2\000a7UMXzvNvzJm0i31TEFBREhnlHqEC9A8=\000a=KdlL\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('87bc4724-4ab6-4902-b669-6cbec10c3608', '242aca1d97c35c38f537d67cc1d2eb41c65b26bd', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat(alpha): first of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:13:59', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsNwAKCRA3V+o4iAJ3\000a540OAP9qIrr9dZT0Tsxe3ExnmHEbPBgcrUV4Gf66JOmkyv985QEA4x0oaPSdBKtz\000acquEthFWwG2seTjcrV96+9iqR8aElgc=\000a=YSdC\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('58b6d15e-a348-4d3d-b3b2-7f46519954d6', 'ac3d5877f7878d303df1f1c4a382d0e80e6dd7e4', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: enable experimental flag\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:14:03', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsOwAKCRA3V+o4iAJ3\000a5wp0AQCaer+FAQW0o5AJA4qyLgdPGvZGvMwprDEDcY7nWHveCQEAh0gcqZ4Xi8pw\000a07WHQgGnY1lYIm0xx9yXHQYhFDfwWAM=\000a=lexA\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('505d24a3-8d13-4059-bfc4-319fa3f8fa13', 'd725fd8c70cb567f8a69c4a80a390576590ad748', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: exploratory change, withdrawn\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:14:05', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsPQAKCRA3V+o4iAJ3\000a5wLOAQDj5+Z7yVHJlb2WvZHnurOZhtChbF+96d2anirZ6EVxDgD/aSz2IHMhDIK3\000akCZrnnWn468JpzQ4l6dnqaJtt39GQQo=\000a=AxBT\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('d2e798dc-b3c7-4da7-9c7d-bafc87e1ec1f', '828992dbe71db0a701a51c81407f01d42adb5ad6', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: answer the most common question\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:14:07', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsPwAKCRA3V+o4iAJ3\000a5xraAP9JPJpLxr/jlD7emCznJl07XkOEePmB7b4k7rvfFeXZNQEAyzQfsnFhy4GM\000a7DhyTBaQhXcxBBDt5qRyqETNoEwATwQ=\000a=bP7D\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);               
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('a8ff12a0-f954-46f3-81ad-fe248d1a61df', '3a4d02167e721e4eff6b3cb22c44d8e0a02817bc', 'ac8e296fcd938115d628e1c08cf5659d4a714863', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: pushed over the SSH transport\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-12 11:14:11', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaqVsQwAKCRA3V+o4iAJ3\000a51vnAQCGG6xwiHta12JokI5aGOsvR2ELp5858g2KWEjmmAfZSgD/WvG0cR5M4lYK\000ahgOL4E3nl6OY6wwl67eaoXD3NPV/swU=\000a=u/7j\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);  
CREATE INDEX "PUBLIC"."IDX_PUSH_COMMITS_PUSH_ID" ON "PUBLIC"."PUSH_COMMITS"("PUSH_ID" NULLS FIRST);            
CREATE CACHED TABLE "PUBLIC"."SCM_API_ENTITIES"(
    "ID" CHARACTER VARYING(36) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "REPO_OWNER" CHARACTER VARYING(255) NOT NULL,
    "REPO_NAME" CHARACTER VARYING(255) NOT NULL,
    "KIND" CHARACTER VARYING(20) NOT NULL,
    "ENTITY_NUMBER" INTEGER NOT NULL,
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
ALTER TABLE "PUBLIC"."SCM_API_ENTITIES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F4" PRIMARY KEY("ID");             
-- 8 +/- SELECT COUNT(*) FROM PUBLIC.SCM_API_ENTITIES;         
INSERT INTO "PUBLIC"."SCM_API_ENTITIES" VALUES
('a888ba0d-3b84-4109-8687-c8f51bfba382', 'github', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://github.com/fixture-dev/fogwall-fixture/pull/1', 'PR_kwDOUYEH0M8AAAABDQ04uQ', 'Proposed via gh (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:17:43.590299', TIMESTAMP '2026-09-12 11:17:47.161495', '543b8aa4-664e-492b-9572-162a11cb8078', 'a007ab33-75bf-4d81-b9e2-b3e65f856955'),
('d11e2239-05a1-4a86-badf-81a2044914fc', 'github', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 2, 'https://github.com/fixture-dev/fogwall-fixture/issues/2', 'I_kwDOUYEH0M8AAAABQ-bhiw', 'Reported via gh', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:17:48.513728', TIMESTAMP '2026-09-12 11:17:51.886214', 'c44382a5-2525-41cb-b149-bdb27550dbac', '76eaa939-c420-4f2c-8ce6-beb1ff51a9a5'),
('2370119e-8596-40b7-a9f5-48665eff9210', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://gitlab.com/fixture-dev/fogwall-fixture/-/merge_requests/1', NULL, 'Proposed via glab (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:17:59.026569', TIMESTAMP '2026-09-12 11:18:02.082276', 'f4e50b68-954d-485c-b2a9-a1955ecfbc92', '2abac44c-fcb5-4290-b927-44c7af43305e'),
('76d8144c-db56-46cb-94cb-dc08a2ac5ccc', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 1, 'https://gitlab.com/fixture-dev/fogwall-fixture/-/work_items/1', NULL, 'Reported via glab', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:18:03.577283', TIMESTAMP '2026-09-12 11:18:06.792538', '0e15bc5e-2aa1-4dea-ade8-5a49b0bc80fe', '3d2de88d-ca4b-471b-acdb-27d2059c6985'),
('817a0f19-afdc-466d-8cbb-def466da1323', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://codeberg.org/fixture-dev/fogwall-fixture/pulls/1', NULL, 'Proposed via fj (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:18:24.139222', TIMESTAMP '2026-09-12 11:18:26.308893', 'c3dc989d-a91f-4563-8ecf-e9e7a1cb2385', '8dd8c100-c95b-43ee-9424-2171b8d2ab5d'),
('d26b25cc-5988-4720-a108-94abd2056b94', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 2, 'https://codeberg.org/fixture-dev/fogwall-fixture/issues/2', NULL, 'Reported via fj', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:18:31.512734', TIMESTAMP '2026-09-12 11:18:33.702912', 'f1cb43ad-1f58-46d5-9501-0c12cec6e273', 'd268fb77-283c-475f-9d98-b60ee3d1be12'),
('91669dc7-2d58-4828-86dc-5fbab5632ca1', 'gitea', 'fixture-dev', 'fogwall-fixture', 'PULL_REQUEST', 1, 'https://gitea.com/fixture-dev/fogwall-fixture/pulls/1', NULL, 'Proposed via tea (edited)', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:18:39.083669', TIMESTAMP '2026-09-12 11:18:41.25439', '0fdc86c0-ab15-4bdd-aac2-09aeff2bbee0', 'f38a6d8b-3592-43f3-8533-416965551806'),
('d54916b9-d26d-47ec-81a4-17f05cb440ec', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ISSUE', 2, 'https://gitea.com/fixture-dev/fogwall-fixture/issues/2', NULL, 'Reported via tea', 'CLOSED', 'dev', 'fixture-dev', TIMESTAMP '2026-09-12 11:18:42.745309', TIMESTAMP '2026-09-12 11:18:44.771281', '7bef0820-b151-4fb2-9bed-d120919dd6f1', 'dedec869-cc6b-4c6a-9050-5222b9ee4ca7');               
CREATE INDEX "PUBLIC"."IDX_SCM_API_ENTITIES_NODE_ID" ON "PUBLIC"."SCM_API_ENTITIES"("PROVIDER" NULLS FIRST, "NODE_ID" NULLS FIRST);            
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
ALTER TABLE "PUBLIC"."SCM_API_ENTITIES" ADD CONSTRAINT "PUBLIC"."UQ_SCM_API_ENTITIES_TARGET" UNIQUE NULLS DISTINCT ("PROVIDER", "REPO_OWNER", "REPO_NAME", "KIND", "ENTITY_NUMBER");           
ALTER TABLE "PUBLIC"."USER_SCM_IDENTITIES" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_50C" UNIQUE NULLS DISTINCT ("PROVIDER", "SCM_USERNAME");        
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
