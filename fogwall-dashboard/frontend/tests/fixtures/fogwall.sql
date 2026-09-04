-- H2 2.4.240; 
;              
CREATE USER IF NOT EXISTS "" SALT '' HASH '' ADMIN;            
CREATE CACHED TABLE "PUBLIC"."SCHEMA_MIGRATIONS"(
    "VERSION" CHARACTER VARYING(20) NOT NULL,
    "DESCRIPTION" CHARACTER VARYING(255) NOT NULL,
    "APPLIED_AT" TIMESTAMP NOT NULL
);      
ALTER TABLE "PUBLIC"."SCHEMA_MIGRATIONS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_3" PRIMARY KEY("VERSION");        
-- 14 +/- SELECT COUNT(*) FROM PUBLIC.SCHEMA_MIGRATIONS;       
INSERT INTO "PUBLIC"."SCHEMA_MIGRATIONS" VALUES
('1', 'initial schema', TIMESTAMP '2026-09-04 09:24:35.896494'),
('2', 'provider id format', TIMESTAMP '2026-09-04 09:24:35.902549'),
('3', 'email unique constraint', TIMESTAMP '2026-09-04 09:24:35.904608'),
('4', 'spring session tables', TIMESTAMP '2026-09-04 09:24:35.913898'),
('5', 'unified rule shape', TIMESTAMP '2026-09-04 09:24:35.973557'),
('6', 'repo permissions FK', TIMESTAMP '2026-09-04 09:24:35.976262'),
('7', 'rename operations to operation', TIMESTAMP '2026-09-04 09:24:35.978032'),
('8', 'user ssh keys', TIMESTAMP '2026-09-04 09:24:35.982114'),
('9', 'permission groups', TIMESTAMP '2026-09-04 09:24:35.987112'),
('10', 'scm oauth tokens', TIMESTAMP '2026-09-04 09:24:35.989693'),
('11', 'ssh key locked flag and auth source', TIMESTAMP '2026-09-04 09:24:35.997491'),
('12', 'ssh key sources', TIMESTAMP '2026-09-04 09:24:35.999965'),
('13', 'email sources', TIMESTAMP '2026-09-04 09:24:36.003223'),
('14', 'push commit co-authored-by trailers', TIMESTAMP '2026-09-04 09:24:36.009996');  
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
('b87bf599-718d-42d8-817f-e152af63a26a', TIMESTAMP '2026-09-04 09:25:15.887008', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/author-noreply-314189', '0000000000000000000000000000000000000000', '9feee63bf778d608655ceaefd44d9968dcdfc675', NULL, 'Fixture Developer', 'noreply@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('c42c0000-a867-431e-b9c3-38ba5feeb309', TIMESTAMP '2026-09-04 09:25:20.696967', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/author-domain-319315', '0000000000000000000000000000000000000000', '8a582e8a4d331ef44cb88a9d805c98c6f9da0f18', NULL, 'Fixture Developer', 'developer@internal.corp.net', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('300d9241-374e-4295-b2da-93eaf77a724b', TIMESTAMP '2026-09-04 09:25:22.648918', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/message-wip-321389', '0000000000000000000000000000000000000000', '9ee01b4d0bc14a88f87a82d646554813e36c3c02', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('9e99502e-4e3c-4a03-9249-aefca1492d6e', TIMESTAMP '2026-09-04 09:25:24.589808', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/message-pattern-323375', '0000000000000000000000000000000000000000', '4ec92f0526711ce6084e45f1234ccd707db4deef', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('0fc5e35a-02b1-46ed-9344-d6dd011a457a', TIMESTAMP '2026-09-04 09:25:27.660914', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/secret-aws-326385', '0000000000000000000000000000000000000000', '6d144236b1f51554b68b0fd0de28195418ddb958', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', TIMESTAMP '2026-09-04 09:25:29.351972', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/diff-literal-328366', '0000000000000000000000000000000000000000', 'd096de731b833e9529f74c440b5b68f3708f6efd', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '2 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('80c0c60b-3d11-457d-a291-b9d25d560aae', TIMESTAMP '2026-09-04 09:25:31.040968', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/diff-pattern-330002', '0000000000000000000000000000000000000000', '17d3a634000735264b33878f50b1ae1459cbe0b3', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('a141dd29-f637-47bb-a76b-6ed8b04fe2a4', TIMESTAMP '2026-09-04 09:25:33.903362', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/no-signoff-332748', '0000000000000000000000000000000000000000', '487ddad222208a8be01f0c5c748f0d54683939b4', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL);        
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('820d9d93-bdf8-41c4-b6a9-0239e7f3082d', TIMESTAMP '2026-09-04 09:25:35.646894', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/signoff-mismatch-334621', '0000000000000000000000000000000000000000', 'cb8eb431ef3aa447eb05c944c1fcdc98351cd0bd', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('8e4efa12-756c-48db-9f76-513ce84ff245', TIMESTAMP '2026-09-04 09:25:37.385223', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/coauthor-denied-336323', '0000000000000000000000000000000000000000', 'd325a251f66e63cef79bd247b43d4d2e45d0f0d9', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '1 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('15106dea-fb5f-4f22-a711-b3497932bc2d', TIMESTAMP '2026-09-04 09:25:40.538084', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/coauthor-ok-339411', '0000000000000000000000000000000000000000', 'f93580ff839680c57ab6451b94b9a162cd179e8e', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('cd966354-5012-4826-8f1b-8548b89d8350', TIMESTAMP '2026-09-04 09:25:43.409719', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/multi-fail-341234', '0000000000000000000000000000000000000000', '30e0d1cfebf2ab2874983046888787df0f899d48', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, '8 validation issue(s)', FALSE, FALSE, 'fixture-dev', NULL),
('470f8a1e-1133-4ba4-a4f4-08ab171eced4', TIMESTAMP '2026-09-04 09:25:46.714311', '/fixture-dev/fogwall-fixture', 'https://codeberg.org/fixture-dev/fogwall-fixture', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/unmapped-344126', '0000000000000000000000000000000000000000', '5be83e6555ca34b386b3db3d99b0641b41a7156e', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, NULL, NULL, NULL, 'PUSH', 'REJECTED', NULL, 'User not authorized', FALSE, FALSE, NULL, NULL),
('2756e624-5b3f-493f-b778-e68696e5b212', TIMESTAMP '2026-09-04 09:25:50.537871', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/pending-branch-349210', '0000000000000000000000000000000000000000', 'd4b4334e400e898eb120a9776981287b1ca8b1a8', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('2b344d35-8288-4689-9b3e-94556a446d57', TIMESTAMP '2026-09-04 09:25:53.054954', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/tags/v9.9.9-fixture-1788528352', '0000000000000000000000000000000000000000', 'd4fcffff2e3d1c06eafdc2e6dcb229a6da382059', NULL, NULL, NULL, NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', TIMESTAMP '2026-09-04 09:25:55.022387', '/fixture-dev/fogwall-fixture', 'https://gitea.com/fixture-dev/fogwall-fixture', 'gitea', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/gitea-353717', '0000000000000000000000000000000000000000', '6ea68cb981667db31b4de4dd9451fa93c720d510', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL);              
INSERT INTO "PUBLIC"."PUSH_RECORDS" VALUES
('479f6e01-8769-4394-bd4c-c02cf956ff59', TIMESTAMP '2026-09-04 09:26:00.53545', '/fixture-dev/fogwall-fixture', 'https://gitlab.com/fixture-dev/fogwall-fixture', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/gitlab-warn-357762', '0000000000000000000000000000000000000000', 'b4124376a179bdfae07e6f604442478886cddcef', NULL, 'Fixture Developer', 'unregistered@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'PENDING', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('35e887d9-3bf6-4305-aaa5-156f22a0fe2e', TIMESTAMP '2026-09-04 09:26:05.126021', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/multi-commit-363725', '0000000000000000000000000000000000000000', 'e2fc2b68beb5d01b6db0a14aedc964ad0b4232b9', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-04 09:27:42.412944'),
('d6a51e32-957f-4c16-ade2-0ced573009af', TIMESTAMP '2026-09-04 09:26:08.024325', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/tags/lightweight-fixture-1788528367', '0000000000000000000000000000000000000000', '4019c432539cf2f237ccf6e9941f34d15f07c49f', NULL, NULL, NULL, NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-04 09:27:45.171265'),
('35d4c24b-854f-415d-8f3f-4785d190e760', TIMESTAMP '2026-09-04 09:26:09.734074', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/reviewer-reject-368703', '0000000000000000000000000000000000000000', '0293bf4a616137a640846fefd3b667bab2c05a73', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'REJECTED', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('a0465faa-9794-4bd2-8b25-9fb3048ef5d4', TIMESTAMP '2026-09-04 09:26:11.691857', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/canceled-370474', '0000000000000000000000000000000000000000', '1ff950a566b5d53459e7c5e92565e1fc8070c357', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'CANCELED', NULL, NULL, FALSE, FALSE, 'fixture-dev', NULL),
('0fb4b457-a7de-4936-86a4-23d6543c365c', TIMESTAMP '2026-09-04 09:26:14.62957', '/fixture-dev/fogwall-fixture', 'https://github.com/fixture-dev/fogwall-fixture', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/self-certify-373602', '0000000000000000000000000000000000000000', 'fb630d86aa61b2351096225b5570560eb4e2642c', NULL, 'Fixture Developer', 'fixture-dev@example.com', NULL, NULL, 'dev', 'dev', NULL, 'PUSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-04 09:27:47.705862'),
('9334f10a-5dd9-486c-94e9-4ae60c1e74c2', TIMESTAMP '2026-09-04 09:26:17.22599', '/fixture-dev/fogwall-fixture', 'ssh://git@github.com/fixture-dev/fogwall-fixture.git', 'github', 'fixture-dev', 'fogwall-fixture', 'refs/heads/fixture/ssh-server-375282', '0000000000000000000000000000000000000000', '141f43cd5b84fdd2cf029ff4d50081b30ea74022', 'feat: pushed over the SSH transport', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', NULL, 'dev', NULL, 'SSH', 'FORWARDED', '', NULL, FALSE, FALSE, 'fixture-dev', TIMESTAMP '2026-09-04 09:27:04.852026');           
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
('c3b3d0a3-90bc-4eaa-b2a5-1834d5b8411b', 'b87bf599-718d-42d8-817f-e152af63a26a', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:15.89406'),
('33bfe7ef-28b9-42be-accc-58349e22a968', 'b87bf599-718d-42d8-817f-e152af63a26a', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.102869'),
('73e6bd81-df6e-4228-ab3d-82019356c7f0', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.103759'),
('70bde38f-9d6e-4642-a64e-9a6c693a1941', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.355986'),
('755493b4-89d2-46b6-ae7f-360ce738fb51', 'b87bf599-718d-42d8-817f-e152af63a26a', 'commitAttributionPolicy', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.359752'),
('3e9911e0-8c9f-48a6-8aab-adaaf5955550', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.359876'),
('0bb3b918-02c6-4d6b-8117-1e3e6efd6992', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.362105'),
('23893848-3dae-473d-8c39-2dca3c8d4b82', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkAuthorEmails', 250, 'FAIL', U&'\274c\fe0f  author email (noreply@example.com): blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)', NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.364578'),
('9d39a695-1f26-4a75-924d-dd3fa72b4327', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.366161'),
('d797b0b0-1dec-4021-8a4a-76f9436b47e4', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.366778'),
('afde54a6-2976-4c5f-9499-1408c2fa2cd5', 'b87bf599-718d-42d8-817f-e152af63a26a', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.399195'),
('a05bf677-d82c-4a7d-89f0-913e08621e83', 'b87bf599-718d-42d8-817f-e152af63a26a', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.450201'),
('0ecde8ba-097c-445c-bf7e-cf163d3adb9f', 'b87bf599-718d-42d8-817f-e152af63a26a', 'diff', 280, 'PASS', U&'diff --git a/notes/noreply.txt b/notes/noreply.txt\000anew file mode 100644\000aindex 0000000..dd8c817\000a--- /dev/null\000a+++ b/notes/noreply.txt\000a@@ -0,0 +1 @@\000a+feat: this commit has a noreply author - 2026-09-04T13:25:15Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.453698'),
('e594876c-8870-4163-b808-910895060901', 'b87bf599-718d-42d8-817f-e152af63a26a', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.456345'),
('a5ee743e-a4c1-4a32-906b-c2cb0fbacac9', 'b87bf599-718d-42d8-817f-e152af63a26a', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:18.456467'),
('05e776d1-502b-4279-914b-00dcfb4c189d', 'b87bf599-718d-42d8-817f-e152af63a26a', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:19.26648'),
('8293114f-87d2-44a6-9254-08ad130ea25b', 'b87bf599-718d-42d8-817f-e152af63a26a', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:19.278734'),
('62989088-1cdd-4099-992d-dd2bf8ae906b', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.698764'),
('cee70f8b-4537-4fa5-9466-63461127ff36', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.708545');            
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('d37db58e-cbae-4b17-b788-8aeb80f71c59', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.709216'),
('7c84d69a-6b4c-44d8-acad-5d0c182ec561', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.712616'),
('32ad171e-3c38-4ff2-8b1d-15800d678c0b', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'commitAttributionPolicy', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.713895'),
('354ad277-46c7-4f50-8ef3-f4ddfd314de8', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.713944'),
('b90f24e3-8a8a-49f8-8e05-1f7860549339', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.714849'),
('fcb63388-b551-4547-98cb-70bab286eb55', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkAuthorEmails', 250, 'FAIL', U&'\274c\fe0f  author email (developer@internal.corp.net): not in allowlist\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'not in allowlist', NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.715277'),
('e8527c08-2423-417d-ab35-ee5744313c2b', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.715353'),
('942d0394-cb88-4a11-9b1e-352b19e04e0b', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.715418'),
('f2368355-2aae-4fdf-8fe2-de4388990b7c', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.722469'),
('9b29e836-9289-4258-b6a4-fc431d38afc0', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.727186'),
('0bd53b54-42be-4ab4-9dfa-03a100c08482', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'diff', 280, 'PASS', U&'diff --git a/notes/domain.txt b/notes/domain.txt\000anew file mode 100644\000aindex 0000000..83c81d4\000a--- /dev/null\000a+++ b/notes/domain.txt\000a@@ -0,0 +1 @@\000a+feat: this commit comes from an unapproved domain - 2026-09-04T13:25:20Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.729506'),
('0e101f7d-d237-448d-85c7-b4c009754f98', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.73162'),
('b499d7a2-e413-4a4c-bd94-d81fedebb85f', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:20.731702'),
('e2d03075-95ec-41f4-8bed-5f154efc4c98', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:21.362723'),
('43e57a70-959e-4db1-8563-38fcda54290b', 'c42c0000-a867-431e-b9c3-38ba5feeb309', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:21.370742'),
('9e3c9dd1-bcbe-410b-aaa8-caed881570a8', '300d9241-374e-4295-b2da-93eaf77a724b', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.650541'),
('264dd0ce-a250-4cd2-91b2-4bda6fcff095', '300d9241-374e-4295-b2da-93eaf77a724b', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.65856'),
('73c7ddfc-b748-4435-98df-5b236d9d6b8f', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.659174'),
('d495f0da-2ab9-431c-924e-062576ef8a85', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.662163'),
('ba37c546-06be-4696-88e3-e8af11b5a25a', '300d9241-374e-4295-b2da-93eaf77a724b', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.663044');  
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('d1f0d850-1b92-49f4-9b3a-2064e9507183', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.663082'),
('058a221c-fad3-4df8-9e10-69ba69b7e9ff', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.663986'),
('51d7ba56-d66c-4d45-8381-a4acd95dccbc', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.66408'),
('fe02a2f5-f47d-452e-ba08-d5811b5915c7', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.664126'),
('40f01d76-9966-440e-9bc8-62198779c062', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkCommitMessages', 260, 'FAIL', U&'\274c\fe0f  WIP: still working on this feature: contains blocked term: "WIP"\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'contains blocked term: "WIP"', NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.664894'),
('4dc04844-f582-4a23-bea4-75c4d73a49e0', '300d9241-374e-4295-b2da-93eaf77a724b', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.672257'),
('9de78a91-d678-480c-bf95-1c466851cea9', '300d9241-374e-4295-b2da-93eaf77a724b', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.676326'),
('d8aead16-8478-48e3-b002-9b648962d4f3', '300d9241-374e-4295-b2da-93eaf77a724b', 'diff', 280, 'PASS', U&'diff --git a/notes/wip.txt b/notes/wip.txt\000anew file mode 100644\000aindex 0000000..3526027\000a--- /dev/null\000a+++ b/notes/wip.txt\000a@@ -0,0 +1 @@\000a+WIP: still working on this feature - 2026-09-04T13:25:22Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.678744'),
('7be08162-5cf1-4552-9947-62dc53d0e72e', '300d9241-374e-4295-b2da-93eaf77a724b', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.680889'),
('f080a555-7cb7-4db8-9bcd-64a99c32d4c1', '300d9241-374e-4295-b2da-93eaf77a724b', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:22.680947'),
('673eedfc-17ee-4163-bd78-68f5b4e2625b', '300d9241-374e-4295-b2da-93eaf77a724b', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:23.35222'),
('225852b4-4038-431e-8790-832696b77a9f', '300d9241-374e-4295-b2da-93eaf77a724b', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:23.359727'),
('a7569ec9-d6d5-47e7-924e-1ddba4d42e14', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:24.591424'),
('dd85cae9-cb26-4d99-a121-0a3ab7658dfd', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.693085'),
('a5d63802-6fce-429b-8c8d-9788f1cdd0c7', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.693757'),
('55ceb20c-fabd-46c4-ae1d-706eafadeb56', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.696717'),
('10d81056-e86e-4a5e-8581-f446a285f6b2', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.697582'),
('9eca5e09-a2c1-489e-b0d3-1790ff410fd2', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.697615'),
('36beefce-ba7b-428f-a357-e949363e6c19', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.698412'),
('74ad9a15-734d-49db-aea3-ee395c4a033e', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.698522'),
('82a1862a-d713-4206-a649-7bebe6388289', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.698568');          
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('5ee711ca-0c51-459c-aa84-115f194ddc43', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkCommitMessages', 260, 'FAIL', U&'\274c\fe0f  chore: rotate token=[REDACTED] in CI config: matches blocked pattern: (?i)(password|secret|token)\\s*[=:]\\s*\\S+\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'matches blocked pattern: (?i)(password|secret|token)\s*[=:]\s*\S+', NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.699157'),
('10e6d9c6-9f32-489b-8960-6e51bd15b99c', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.705687'),
('4c5a5c87-1339-4781-9615-0bf98e3eb68c', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.7098'),
('3808b4a6-7a3a-4c56-bdba-2bce242cb0ca', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'diff', 280, 'PASS', U&'diff --git a/notes/rotate.txt b/notes/rotate.txt\000anew file mode 100644\000aindex 0000000..61af17b\000a--- /dev/null\000a+++ b/notes/rotate.txt\000a@@ -0,0 +1 @@\000a+chore: rotate token=[REDACTED] in CI config - 2026-09-04T13:25:24Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.711958'),
('1e882577-2a86-438b-84c5-bdd5408bf043', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.713844'),
('766b76a4-3151-4c57-85ca-5b13ef993b98', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:25.713896'),
('43a702ad-2794-490a-a0bb-e16e8f0ec23e', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [generic-api-key]  notes/rotate.txt:1\000a  commit: 4ec92f0\000a  match:  token=[REDACTED] \000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[generic-api-key]  notes/rotate.txt:1\000a  commit: 4ec92f0\000a  match:  token=[REDACTED] ', NULL, NULL, TIMESTAMP '2026-09-04 09:25:26.357607'),
('9448dfbb-90a1-49e9-aea5-14c245b802f6', '9e99502e-4e3c-4a03-9249-aefca1492d6e', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:26.364573'),
('6133b3c5-00a6-42f6-9a34-067a0358028a', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.662362'),
('7f046319-0643-4f42-a720-dc71c02ce813', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.669402'),
('ef2753b9-05d5-4bdc-9481-75f12922803b', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.669954'),
('b4ef60c9-ff2b-43e4-9b46-b3a339ccf8c8', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.672847'),
('a6b7ca58-2b89-407f-a243-389a2446c8b8', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.673636'),
('6ad0b90e-f9ed-4b16-87bc-6b650400f03b', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.673664'),
('3edcc354-be71-4f9c-a4be-379dcbb818c3', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.674556'),
('80980fb8-10bc-4b00-9300-3a95ac410465', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.674619'),
('1e74ca4e-f866-4402-abbc-6d8ce32e2947', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.674658'),
('a842b0c4-bd7c-4618-a29e-5ea241d10c50', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.674695'),
('0b7b6ae9-99ba-43a6-887e-f728c1b2867b', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.679496');          
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('16fd5c1c-3b2a-4cf6-ad32-78255b11eae5', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.683162'),
('96690013-0121-4d92-af27-0f7784318380', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'diff', 280, 'PASS', U&'diff --git a/aws-credentials b/aws-credentials\000anew file mode 100644\000aindex 0000000..53c17b0\000a--- /dev/null\000a+++ b/aws-credentials\000a@@ -0,0 +1,3 @@\000a+[default]\000a+aws_access_key_id = [REDACTED]\000a+aws_secret_access_key = [REDACTED]\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.684921'),
('38f1bedb-6cc9-4ce2-b090-b3b86dffd81e', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.686499'),
('876fba88-f6ed-45b0-a37d-62d0117d1a39', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:27.686551'),
('b9c49be2-9ea7-47b6-94c5-c9ede8273b02', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [aws-access-token]  aws-credentials:2\000a  commit: 6d14423\000a  match:  [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[aws-access-token]  aws-credentials:2\000a  commit: 6d14423\000a  match:  [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-04 09:25:28.330985'),
('6d0dd720-0e5b-422f-b2b5-c6d0bd736950', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [generic-api-key]  aws-credentials:3\000a  commit: 6d14423\000a  match:  aws_secret_access_key = [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[generic-api-key]  aws-credentials:3\000a  commit: 6d14423\000a  match:  aws_secret_access_key = [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-04 09:25:28.331089'),
('f07dbe03-a7f1-4406-aea5-9826e666b7ce', '0fc5e35a-02b1-46ed-9344-d6dd011a457a', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:28.340988'),
('0ef15385-6097-444a-a06b-384a44556f9a', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.353831'),
('0267dac8-bd27-472b-bc20-e6982c88ae89', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.359885'),
('9d34b63f-3388-4b51-b1d0-b90255caa02c', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.360397'),
('d8a81394-cec8-4831-9049-197b3df39015', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.362644'),
('a9616526-2eea-429f-b10d-b0b7b057c252', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.363242'),
('aa6ff1a8-aed2-4a1f-b19a-c7c643f939ea', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.363263'),
('78779790-5bc6-43c9-8037-7c1f3146d87a', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.363903'),
('726ed57f-5db3-4f96-b9ce-307342740282', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.363961'),
('dd14b9b3-c01d-4cf0-a911-2e44928a8672', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.364002'),
('33897646-97b1-4c4a-a597-0aa8555e8780', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.36403'),
('2a261e38-e57e-4682-9913-ebdd77386cee', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.367374');     
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('e00e770f-517a-480e-b69e-acc5cb262ede', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.37001'),
('9655b28e-62af-4c9f-854f-01cb07cc3492', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'diff', 280, 'PASS', U&'diff --git a/config.yml b/config.yml\000anew file mode 100644\000aindex 0000000..0d0e7b4\000a--- /dev/null\000a+++ b/config.yml\000a@@ -0,0 +1,3 @@\000a+upstream:\000a+  api: https://internal.corp.example.com/api/v1\000a+  timeout: 30\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.37131'),
('dcce8b09-7562-4d5c-96c7-acaba6f0c3fb', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'scanDiff', 300, 'FAIL', U&'blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.371891'),
('8566f4b2-0924-495f-b490-1d4773b23474', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'scanDiff', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.371909'),
('4a73a1ea-72a4-42d7-b172-09a4a8af090d', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.373803'),
('25c52f4c-e358-472d-8d21-82b5828e2c40', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.980918'),
('6f1cea1a-6c88-4763-a1fe-18d688d2d9ce', '47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:29.987348'),
('4e2ca5b3-d8cc-4fdc-a4ba-69b69d4bdf09', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:31.042503'),
('8868072e-2b99-4d64-8b5f-2a47936e9259', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.065996'),
('c05e7a13-b4f9-432f-a6d2-a3e0c723bc6b', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.066657'),
('4ef2f2fa-a7f3-46fe-942f-67a5e456833c', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.069912'),
('729f4672-2d38-4e50-a7d0-7bbebf5ef9d6', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.070693'),
('c5cd9fa2-6d90-4660-a63c-1f9fd961ab15', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.070729'),
('a95cf448-978a-4098-b696-809a3c425dde', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.071551'),
('e0020f17-864b-487e-820f-233e02346bfb', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.071626'),
('ec92ffaf-7507-4fb1-8020-0d015c502378', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.071672'),
('64680644-8339-4ce9-aa11-fd8d7b1072ba', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.071708'),
('14e74e7c-5429-4cf3-998f-79401257028e', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.076005'),
('6be0a3d3-d96f-4cd5-86ef-6556c04f4124', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.080316'),
('254c1386-0c03-43b6-ac50-c3105e634ceb', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'diff', 280, 'PASS', U&'diff --git a/deploy.sh b/deploy.sh\000anew file mode 100644\000aindex 0000000..3e0ea35\000a--- /dev/null\000a+++ b/deploy.sh\000a@@ -0,0 +1,2 @@\000a+#!/bin/bash\000a+curl -X POST http://ci.corp.example.com/deploy -d ''{"version": "1.2.3"}''\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.082393');          
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('b5e89565-e177-43cb-a0ab-5113a521a1fb', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'scanDiff', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in deploy.sh\000a  curl -X POST http://ci.corp.example.com/deploy -d ''{"version": "1.2.3"}''', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in deploy.sh', NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.082831'),
('ea8bebf4-852e-4cfa-b7b8-044fed74c5ea', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.084946'),
('6630faab-dd81-4d57-b31d-f3a391908259', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.718931'),
('0e216f9c-cf90-4555-a2b4-e8a7481564db', '80c0c60b-3d11-457d-a291-b9d25d560aae', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:32.72933'),
('7e68e648-2573-4ab6-b50c-a09918b128b6', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.904282'),
('693e4d55-91e2-4e18-ae8f-a8a9b5f253b4', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.908949'),
('d8f0225d-64b0-4ecd-a2b9-84d226817ae4', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.909283'),
('988b53c9-c448-438c-8698-d51c6b25aabe', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.911144'),
('cd029c34-aa75-4745-8ece-1c92088848cf', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.911737'),
('03fe8066-3a0f-4204-baa0-b495df398694', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.911758'),
('21e92db3-f5fc-4051-8d86-d95bd51cc766', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.912714'),
('b7e57b33-b1f7-4434-9be6-ce5bccf88c3c', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.912773'),
('6849b38a-deab-4f45-897e-3dab77f2a411', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit 487ddad has no Signed-off-by trailer\000a  \2192 This repository requires the Developer Certificate of Origin (DCO) sign-off.\000a  \2192 Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase --signoff <base> for a range).', 'missing Signed-off-by (487ddad)', NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.913402'),
('577c825a-e29f-41ef-9810-754cdfd7783f', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.913448'),
('1dc921ef-ac2a-4e47-b126-64b032cf3599', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.916961'),
('8abf315c-628d-408a-a92e-7afceeca3f1b', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.920816'),
('9b8b2b47-cb20-478b-96d7-ae119d9f7049', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'diff', 280, 'PASS', U&'diff --git a/notes/unsigned.txt b/notes/unsigned.txt\000anew file mode 100644\000aindex 0000000..68107fb\000a--- /dev/null\000a+++ b/notes/unsigned.txt\000a@@ -0,0 +1 @@\000a+feat: forgot to sign off - 2026-09-04T13:25:33Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.922587'),
('d03a06a5-acf9-49f1-a1e3-0d5864c2cd03', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.924067'),
('03ea3a11-67a4-42e9-9dff-fa45f796a4c3', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:33.92413');            
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('c41b2c58-b9b1-4d74-a180-1ed796a157dc', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:34.597386'),
('68a0a75a-2b48-4399-8483-09f3132627d5', 'a141dd29-f637-47bb-a76b-6ed8b04fe2a4', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:34.602007'),
('60748fc0-5e0a-430b-9a3e-da18b05112c7', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.648333'),
('eb4229ad-a0a8-47c7-b310-45b5b89b22bc', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.654507'),
('a319fc2f-8e15-4837-a91a-81a564cdacd8', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.654959'),
('b962cee2-e82f-445d-a00c-76663ddd0b45', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.657841'),
('814f004d-4e2b-4f69-b4f9-8a523174477e', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.658402'),
('813a28ac-c365-44a6-a9fc-6f1cbc6c6b28', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.658429'),
('48443153-631b-47ad-8a95-6761affdd35f', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.659083'),
('ad515176-f470-402e-ade5-05281215fa4c', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.659151'),
('e7d5efdb-8d84-469d-b2e2-9acdf936a586', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit cb8eb43 has no Signed-off-by matching its author <fixture-dev@example.com>\000a  \2192 The DCO requires you to sign off your own work: a Signed-off-by whose email equals the commit author.\000a  \2192 Fix: git config user.email to your author email, then git commit --amend --signoff.', 'Signed-off-by does not match author (cb8eb43)', NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.659655'),
('cb88b209-e65d-4bb6-a3b6-2b68857d17d2', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.659704'),
('da26c8bc-2034-4c86-9982-e8412015ca3f', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.662247'),
('48c2e38c-61ed-46a6-8fe6-0c1c31661917', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.665179'),
('11871f14-d40c-4b7a-a6ed-f3d152ba1045', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'diff', 280, 'PASS', U&'diff --git a/notes/mismatch.txt b/notes/mismatch.txt\000anew file mode 100644\000aindex 0000000..ca8d460\000a--- /dev/null\000a+++ b/notes/mismatch.txt\000a@@ -0,0 +1 @@\000a+feat: signed off by the wrong person - 2026-09-04T13:25:35Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.666866'),
('68f108ba-6bd4-469f-91dc-0c4c074b7f64', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.668519'),
('1258d714-d7b3-48e1-a67a-13d48218e36a', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:35.668566'),
('e39dfe01-4f5b-4cbb-97c4-46f64f426e19', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:36.303132'),
('1249701e-59d1-4fb9-9fb0-6dd618fc4f40', '820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:36.308286'),
('cfd91dac-f334-4798-a6d5-9a218288f173', '8e4efa12-756c-48db-9f76-513ce84ff245', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:37.386506');        
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('7ec2fc3a-fdb1-4615-97b0-a31ad1e8c265', '8e4efa12-756c-48db-9f76-513ce84ff245', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.718722'),
('eca06090-847f-4867-bbde-c54b31708710', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.719253'),
('b61db04d-8006-49eb-a71f-e6f736641f3f', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.722039'),
('cb295fca-857a-4743-8ef5-7f149354c0b8', '8e4efa12-756c-48db-9f76-513ce84ff245', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.722795'),
('3edd300e-9319-4116-9097-9bf2ea7ec422', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.722825'),
('a298a843-94fb-484b-bccc-f7faa9fb5e58', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.723674'),
('5b64d2d2-7512-49fa-b179-4b5564cc25ec', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.723746'),
('501430db-76a2-4337-a598-5dd27ba643ed', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit d325a25 Co-authored-by (Contractor <contractor@outside.example.net>): not in allowlist\000a  \2192 Co-authors must be permitted by policy (allowed domain / not a blocked address).\000a  \2192 Fix: remove the disallowed Co-authored-by line, or use an approved co-author identity.', 'Co-authored-by not allowed (d325a25)', NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.724439'),
('d8bc94e5-b56b-46fe-b170-6a1f60a852b4', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.724516'),
('3426b62c-f9f4-4fe6-8b80-9472d6c8cfc6', '8e4efa12-756c-48db-9f76-513ce84ff245', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.727658'),
('c5362271-0c1b-460a-a4c9-0172c9ec72a5', '8e4efa12-756c-48db-9f76-513ce84ff245', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.730927'),
('6c6a32c0-d9a3-487c-9be6-6ac391d9f226', '8e4efa12-756c-48db-9f76-513ce84ff245', 'diff', 280, 'PASS', U&'diff --git a/notes/coauthor.txt b/notes/coauthor.txt\000anew file mode 100644\000aindex 0000000..cc179f9\000a--- /dev/null\000a+++ b/notes/coauthor.txt\000a@@ -0,0 +1 @@\000a+feat: paired with an outside contractor - 2026-09-04T13:25:36Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.732695'),
('03dde4e0-03b7-4cb3-a9cb-d4cfad47430b', '8e4efa12-756c-48db-9f76-513ce84ff245', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.73452'),
('cd94868f-8273-4647-9e3c-8b9100c8cee7', '8e4efa12-756c-48db-9f76-513ce84ff245', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:38.734562'),
('2ee6c8f6-21ef-4a00-8055-013ad8d8ebe4', '8e4efa12-756c-48db-9f76-513ce84ff245', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:39.386801'),
('553a3799-472d-4cca-9926-d11e1f0fd44d', '8e4efa12-756c-48db-9f76-513ce84ff245', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:39.393537'),
('6143862e-68bc-40ba-b3c8-40bc4d5bf7d7', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.539755'),
('4fa64f11-eec1-4838-8d3e-287a7a9cabaa', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.547486'),
('d157d90c-9e09-47df-89dc-eccbc044b172', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.547955'),
('7883c412-bd96-4f29-a002-34df119cf5c1', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.551392');     
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('0c435a7a-e671-40ec-9556-cd742998d477', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.552428'),
('0fcc8c97-1c22-4729-9b2e-671a64d019aa', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.552504'),
('e294bd49-6310-4b3d-9acb-c11125efc2c2', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.553806'),
('9f0f0811-7c62-4a5b-95a7-6efae1550b9f', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.55397'),
('c220167c-be04-4573-ba53-f414c4660f94', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.554091'),
('b7244085-9382-4400-b4a7-74a831f0804d', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.554177'),
('ff314bd1-ccba-4586-9fee-f2ace5a02593', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.558963'),
('688f6efb-87aa-43f1-8e1d-f1bc1f1cd8cc', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.562942'),
('5a2d92db-59ab-4937-8931-d9487e04f141', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'diff', 280, 'PASS', U&'diff --git a/notes/pair.txt b/notes/pair.txt\000anew file mode 100644\000aindex 0000000..ecb5051\000a--- /dev/null\000a+++ b/notes/pair.txt\000a@@ -0,0 +1 @@\000a+feat: pair-programmed with an allow-listed co-author - 2026-09-04T13:25:40Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.565345'),
('2901e2ab-b4c2-46b3-b3b1-162442add7cd', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.567411'),
('985af436-b6fe-4de5-a245-012b9fe1ea47', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:40.567501'),
('8516334c-5303-4410-b67f-b254102684b8', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:41.216572'),
('b0eb5069-2752-4316-a397-8a37051baba4', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:41.220231'),
('c6584cbc-8ca8-4396-b3cb-0c8b6c577bcf', '15106dea-fb5f-4f22-a711-b3497932bc2d', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:41.220288'),
('78f291a0-d0df-4203-afa9-cc7b8f5517fd', 'cd966354-5012-4826-8f1b-8548b89d8350', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.411048'),
('271b1a71-ee97-4fb1-8098-2a19865ccb86', 'cd966354-5012-4826-8f1b-8548b89d8350', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.423953'),
('0c2d1ced-4341-4ab5-bdee-6c2d9552e6c8', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.424481'),
('24a677cc-edee-4917-8fd4-bf6bba1ffa9d', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.426897'),
('4245ecb9-92d5-4dd8-b44e-f3f4e05b2f1d', 'cd966354-5012-4826-8f1b-8548b89d8350', 'commitAttributionPolicy', 160, 'WARN', U&'2 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.427794'),
('218deea9-f023-4a2b-909e-558aee2000d7', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.427829'),
('69aae55a-540f-4b27-a4f5-2afa54dce887', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.428807'),
('f1327187-d323-4a2c-a7e9-30ef1e6ecfee', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkAuthorEmails', 250, 'FAIL', U&'\274c\fe0f  author email (noreply@example.com): blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)\000a  \2192 This commit was originally authored by someone outside the allowed domain.\000a  \2192 Rebasing external commits onto this branch is not permitted by policy.\000a  \2192 Alternative: open a PR from the original author''s fork instead of rebasing.', 'blocked by policy (block local ~ ^(noreply|no-reply|bot|nobody)$)', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.429111');       
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('220bc2b8-f112-49d6-a74a-ec66e8110c3f', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkTrailers', 255, 'FAIL', U&'\274c\fe0f  commit 30e0d1c has no Signed-off-by trailer\000a  \2192 This repository requires the Developer Certificate of Origin (DCO) sign-off.\000a  \2192 Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase --signoff <base> for a range).', 'missing Signed-off-by (30e0d1c)', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.429288'),
('bf966d2d-75e2-4402-b3c9-6e5771d63d12', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkCommitMessages', 260, 'FAIL', U&'\274c\fe0f  WIP: commit 2 \2014 bad commit message: contains blocked term: "WIP"\000a  \2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE', 'contains blocked term: "WIP"', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.429815'),
('20543ca5-6ce1-4ca1-8970-a0399ae437c9', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.436881'),
('6b0bb9c0-789c-4324-9919-082e98ec8a17', 'cd966354-5012-4826-8f1b-8548b89d8350', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.446871'),
('7ed6ae64-a29e-46bc-912d-1612570f422f', 'cd966354-5012-4826-8f1b-8548b89d8350', 'diff', 280, 'PASS', U&'diff --git a/ci-config.env b/ci-config.env\000anew file mode 100644\000aindex 0000000..e2977e7\000a--- /dev/null\000a+++ b/ci-config.env\000a@@ -0,0 +1 @@\000a+GITHUB_TOKEN=[REDACTED]\000adiff --git a/config.yml b/config.yml\000anew file mode 100644\000aindex 0000000..bb7ce43\000a--- /dev/null\000a+++ b/config.yml\000a@@ -0,0 +1,2 @@\000a+upstream:\000a+  api: https://internal.corp.example.com/api/v1\000adiff --git a/multi/1.txt b/multi/1.txt\000anew file mode 100644\000aindex 0000000..344ffce\000a--- /dev/null\000a+++ b/multi/1.txt\000a@@ -0,0 +1 @@\000a+test: commit 1 \2014 noreply author email - 2026-09-04T13:25:41Z\000adiff --git a/multi/2.txt b/multi/2.txt\000anew file mode 100644\000aindex 0000000..61f5043\000a--- /dev/null\000a+++ b/multi/2.txt\000a@@ -0,0 +1 @@\000a+WIP: commit 2 \2014 bad commit message - 2026-09-04T13:25:42Z\000adiff --git a/multi/5.txt b/multi/5.txt\000anew file mode 100644\000aindex 0000000..126ee3a\000a--- /dev/null\000a+++ b/multi/5.txt\000a@@ -0,0 +1 @@\000a+test: commit 5 \2014 unregistered commit email - 2026-09-04T13:25:42Z\000adiff --git a/multi/6.txt b/multi/6.txt\000anew file mode 100644\000aindex 0000000..3317f34\000a--- /dev/null\000a+++ b/multi/6.txt\000a@@ -0,0 +1 @@\000a+test: commit 6 \2014 missing DCO sign-off - 2026-09-04T13:25:42Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.449631'),
('ba04a5dc-39dd-4a1e-b89b-b6a5b9d557fc', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanDiff', 300, 'FAIL', U&'blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.45013'),
('9e1bae9a-1b8a-4da0-a50d-490c4e7a566a', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanDiff', 300, 'FAIL', U&'blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', 'blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.450151'),
('77f81aea-510f-4a53-8e3f-50bf838616ef', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanDiff', 300, 'FAIL', U&'commit 5069c78: blocked term: "internal.corp.example.com" in config.yml\000a  api: https://internal.corp.example.com/api/v1', '5069c78: blocked term: "internal.corp.example.com" in config.yml', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.456804'),
('86c312db-bff2-4b95-bbc2-b0a81b5a36b1', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanDiff', 300, 'FAIL', U&'commit 5069c78: blocked pattern: (?i)https?://[a-z0-9.-]*\\.corp\\.example\\.com\\b in config.yml\000a  api: https://internal.corp.example.com/api/v1', '5069c78: blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml', NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.456816');     
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('b1815901-278a-4708-931a-60480446e1b9', 'cd966354-5012-4826-8f1b-8548b89d8350', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:43.458791'),
('81e34a95-9531-49e2-9155-56b761323fcc', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanSecrets', 340, 'FAIL', U&'\274c\fe0f  [github-pat]  ci-config.env:1\000a  commit: c7c4e12\000a  match:  [REDACTED]\000a\2192 Rotate any exposed credentials and remove the secret from your commit history before pushing.', U&'[github-pat]  ci-config.env:1\000a  commit: c7c4e12\000a  match:  [REDACTED]', NULL, NULL, TIMESTAMP '2026-09-04 09:25:44.098587'),
('82b20ee5-4310-4022-91ea-14bc0196b556', 'cd966354-5012-4826-8f1b-8548b89d8350', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:44.107017'),
('d3ff9434-ee09-4bd7-9fd0-692707dcfe4b', '470f8a1e-1133-4ba4-a4f4-08ab171eced4', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:46.715594'),
('2ae58715-f3aa-41b1-afdc-f415e2ae4094', '470f8a1e-1133-4ba4-a4f4-08ab171eced4', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:48.378361'),
('81d0a1ae-6c1c-49a1-bb74-1f1ce730a658', '470f8a1e-1133-4ba4-a4f4-08ab171eced4', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:48.378839'),
('3f9ffa7c-2bea-4c05-8581-ad38291f353c', '470f8a1e-1133-4ba4-a4f4-08ab171eced4', 'checkUserPermission', 150, 'FAIL', U&'\000a\26d4\fe0f  Push Blocked - Unauthorized\000a\000a\274c\fe0f  dev is not allowed to push to:\000a   \+01f517\fe0f  https://codeberg.org/fixture-dev/fogwall-fixture\000a', 'User not authorized', NULL, NULL, TIMESTAMP '2026-09-04 09:25:49.192075'),
('2fcd586e-23d6-490e-a551-db8c2dc54c45', '2756e624-5b3f-493f-b778-e68696e5b212', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:50.539169'),
('a5d33aa0-49fc-4117-aee9-0c661a0dd68a', '2756e624-5b3f-493f-b778-e68696e5b212', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.555054'),
('0824694b-11a0-4d70-95b8-ecc518f98d23', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.555522'),
('ca27a7c0-aa44-4717-b517-da93d3829511', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.557771'),
('6e664fc9-1f8e-4eb9-8ce7-d2e7f2b73573', '2756e624-5b3f-493f-b778-e68696e5b212', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.558244'),
('1b206b3c-3a7b-463e-a031-6c52a9171153', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.558268'),
('14e6ae73-a711-4edf-9206-277bf2cee394', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.558907'),
('f835dc23-1168-423b-a2dc-bf0d09ffc0fd', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.558965'),
('d72ecf12-463b-4e78-bfda-aeae719fc0c3', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.55901'),
('bbcf3c3c-507b-4b87-b189-ccf298b71721', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.559044'),
('bee2838e-1296-4bdf-b839-97e71bdf82bc', '2756e624-5b3f-493f-b778-e68696e5b212', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.561257'),
('9ba81c6b-1edc-43fc-8121-1f97ba696c5f', '2756e624-5b3f-493f-b778-e68696e5b212', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.564118'),
('bc2bd8fa-2874-412e-84b5-51123d3b04bd', '2756e624-5b3f-493f-b778-e68696e5b212', 'diff', 280, 'PASS', U&'diff --git a/docs/release-notes.md b/docs/release-notes.md\000anew file mode 100644\000aindex 0000000..3ccd14d\000a--- /dev/null\000a+++ b/docs/release-notes.md\000a@@ -0,0 +1 @@\000a+docs: add release notes stub - 2026-09-04T13:25:49Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.56565');             
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('fb00a9c7-f19e-4d91-8b39-259a43649c37', '2756e624-5b3f-493f-b778-e68696e5b212', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.56717'),
('e444beaf-3291-4e28-89ab-8a0e24a74465', '2756e624-5b3f-493f-b778-e68696e5b212', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:51.567201'),
('45668591-9f4f-4237-b06f-eb30baec43be', '2756e624-5b3f-493f-b778-e68696e5b212', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:52.216769'),
('0a1cf1fa-e199-401d-96ea-7d2f23a7dcc5', '2756e624-5b3f-493f-b778-e68696e5b212', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:52.220277'),
('a28341a2-b823-430c-8d0a-91f0ba605c80', '2756e624-5b3f-493f-b778-e68696e5b212', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:52.22032'),
('a290c042-44e7-46a8-bd93-815a3f6e6b2e', '2b344d35-8288-4689-9b3e-94556a446d57', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.056793'),
('e7937268-8200-4689-b26e-173c873119fd', '2b344d35-8288-4689-9b3e-94556a446d57', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.065188'),
('b2db0dcf-f3f3-4db0-b6dc-2d28a49f3352', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.065774'),
('4b5cc950-2553-4410-8199-ee96608f6d25', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.069106'),
('d4cb5ecf-9469-44ca-8b5d-42ad0bb9e25f', '2b344d35-8288-4689-9b3e-94556a446d57', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.069172'),
('51123529-03d9-4a53-9fe1-663dbdee8fc5', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.069207'),
('1b418837-0750-4ef9-99f1-66900bd2f649', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.070045'),
('8a0cc994-22d6-4614-804d-3f82005725ee', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.070087'),
('314efd96-0b3a-4c62-b3a9-040450e8105b', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.070108'),
('b938baae-a178-4adf-a400-f80c61c5f372', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.070193'),
('68df2938-9d7b-4835-94c9-65dfa783dcf2', '2b344d35-8288-4689-9b3e-94556a446d57', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.073676'),
('2b8f8c9f-fc5d-4f3e-8ffd-6312468d74de', '2b344d35-8288-4689-9b3e-94556a446d57', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.07373'),
('96121387-7561-43e5-8827-0fc152fa46ec', '2b344d35-8288-4689-9b3e-94556a446d57', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.073747'),
('88b898af-6f41-4202-8945-7316ea258f3f', '2b344d35-8288-4689-9b3e-94556a446d57', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.073766'),
('57b6fa70-222f-4f58-b391-f4243316b887', '2b344d35-8288-4689-9b3e-94556a446d57', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.699646'),
('5c171b54-88e6-4c32-9ad0-09a9b2b5878c', '2b344d35-8288-4689-9b3e-94556a446d57', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.699731'),
('435171cc-a470-4947-a26e-bb157efd48ee', '2b344d35-8288-4689-9b3e-94556a446d57', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:53.699786'),
('e0ceae45-9d47-4137-ab39-425f71a71182', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:55.023713');    
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('1f4fead6-65e6-4e4a-b233-9e915655e798', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:56.543585'),
('c0e9f1a8-fc23-43f0-940f-34d3908bad6e', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:56.544107'),
('76eaca69-9202-4305-87a2-bc352714027d', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.092796'),
('2f257eb8-6d87-4b76-9a19-fa2f08068756', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.093564'),
('bf595009-f3d8-41bc-b0b5-64b11922640a', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.093592'),
('5be815a4-343c-4d1a-8852-58a6dd224970', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.094581'),
('b2a343ad-8437-4c49-bccd-cff62ede868a', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.094654'),
('b2a9127d-b784-41e4-a088-bf2460af32fa', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.09469'),
('51d61810-6204-4a19-9c23-f77ced7567e8', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.094735'),
('85ec4202-6f8d-4b1c-a2a7-3818b6bde42c', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.096776'),
('ad06d696-9e9b-4ef3-aeeb-d3db7b28b2a4', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.100411'),
('5efe5e6c-214f-4cf4-b786-11c8e37e1381', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'diff', 280, 'PASS', U&'diff --git a/README.md b/README.md\000aindex 4329f89..6607f0e 100644\000a--- a/README.md\000a+++ b/README.md\000a@@ -1,3 +1,3 @@\000a # fogwall-fixture\000a \000a-fogwall UI fixture capture \2014 safe to delete\000a\\ No newline at end of file\000a+fogwall UI fixture capture \2014 safe to deletedocs: touch readme via fogwall - 2026-09-04T13:25:54Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.102769'),
('75b7ab5e-1c3d-44af-97bc-ad6be6ee728f', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.104421'),
('2423d5ff-4265-4d46-b1e4-cfe999b3f058', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.104476'),
('deb84a0c-2366-4f33-b34a-02a717055dae', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.73884'),
('bc107d1a-fb40-4eb4-845a-fcb3ac43a38e', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.745307'),
('fd29509c-aa70-43f5-8533-a76f8afcb316', '2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:25:57.745336'),
('abddee25-de4b-41f4-9c7d-1d042a1dcbb3', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:00.536934'),
('f3087bdd-e0c9-4c57-9f42-2418dcbfbbab', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:02.598733'),
('281d3960-abd9-445f-adac-4f212a9e3a00', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:02.599168'),
('10cff7ca-7dc5-4eec-85aa-d9810cacf6df', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.045887');       
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('747c3273-9e65-4c32-8607-8a877062e5d3', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'commitAttributionPolicy', 160, 'WARN', U&'1 unrecognised commit email(s) \2014 not in proxy user registry', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.047596'),
('e70beb1d-6a3d-4d20-9460-de2b42058e97', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.047645'),
('cf92f9ad-0049-4ff7-b420-3e3e04c4f84f', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.048532'),
('c0b45779-066b-4b40-8479-59ca1326e653', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.0486'),
('4d25be61-f2fd-4f1c-83f1-0c1f8aceb448', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.048636'),
('ad30f607-2c18-4ee0-a956-6ca7f93ea7aa', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.048745'),
('7549ec68-9134-4bb8-85a6-48a68d09dace', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.050986'),
('a9232900-9f11-4266-b8e0-8ab89d02057b', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.054221'),
('710d02a2-9492-4880-a687-f000d53e6630', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'diff', 280, 'PASS', U&'diff --git a/notes/gitlab.txt b/notes/gitlab.txt\000anew file mode 100644\000aindex 0000000..7509dcf\000a--- /dev/null\000a+++ b/notes/gitlab.txt\000a@@ -0,0 +1 @@\000a+test: identity resolution \2014 gitlab resolved, email unregistered - 2026-09-04T13:25:59Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.056093'),
('a6de87ea-c42a-4157-9306-fdfd74e38abd', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.057501'),
('f8be7ffb-fb00-4aa2-a027-2ccf59987a22', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.05754'),
('5e2a9f5f-e44d-4726-a5e3-2ced9055bf17', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.69822'),
('0945c15a-7d02-42e2-9897-598d75ac39a6', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.704674'),
('e9382233-ece7-4f33-acbc-2806cb3fec17', '479f6e01-8769-4394-bd4c-c02cf956ff59', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:03.70472'),
('16c3fed8-da4d-4bc9-9e5a-fa8884c003b9', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:05.127004'),
('b2ae8545-b81e-41ca-ac25-1f6a6bb9f06e', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.384839'),
('0ab1134b-fb0c-4738-95fb-718c360350d6', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.385496'),
('3081315b-bfa2-4bcb-b91e-419307b51ba2', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.389192'),
('1df099ba-3699-402d-862a-e4db9b6271d0', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.390234'),
('5056ec95-6b8a-486c-8049-56b3ab10e492', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.390275'),
('3756ca80-b4e1-40a0-88ba-228f524c6725', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.3914');               
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('1d56e4ac-330e-42bd-8a11-96dd6c1d24fb', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.391553'),
('de6dd613-d2c1-4d7e-8cfe-ae4d04dc3459', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.391644'),
('b9823249-8a83-4f72-8b9f-765808da1ad4', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.391718'),
('9f422d2c-ab0c-408a-a320-0b3b17ea521b', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.396022'),
('cc4405af-0a42-4edb-bcb6-352add1812fd', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.401944'),
('dc2e64e8-b206-4717-8ea3-4526f4c41300', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'diff', 280, 'PASS', U&'diff --git a/src/alpha.txt b/src/alpha.txt\000anew file mode 100644\000aindex 0000000..1dc9cfc\000a--- /dev/null\000a+++ b/src/alpha.txt\000a@@ -0,0 +1 @@\000a+feat(alpha): first of three - 2026-09-04T13:26:04Z\000adiff --git a/src/beta.txt b/src/beta.txt\000anew file mode 100644\000aindex 0000000..e0a0dea\000a--- /dev/null\000a+++ b/src/beta.txt\000a@@ -0,0 +1 @@\000a+feat(beta): second of three - 2026-09-04T13:26:04Z\000adiff --git a/src/gamma.txt b/src/gamma.txt\000anew file mode 100644\000aindex 0000000..7de6702\000a--- /dev/null\000a+++ b/src/gamma.txt\000a@@ -0,0 +1 @@\000a+fix(gamma): third of three - 2026-09-04T13:26:04Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.403993'),
('ba175ccf-3580-42bc-96ff-a63f750016bc', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.408645'),
('b587a30b-388b-44e3-9e64-eab4cecdc70b', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:06.408681'),
('a8d3daa2-05e9-4f41-80c0-2c1f04c4cc58', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:07.042071'),
('b64de553-9638-4ad1-978a-30683786d87a', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:07.046424'),
('65baa986-09ff-4b85-817b-1762ec39fd51', '35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:07.046474'),
('1952a994-d64d-4814-ae7a-14e7b7e9182f', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.025505'),
('94f02899-52f6-4137-a17d-21d57ed0bc44', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.029883'),
('b236bd17-f786-4471-b8eb-7f7f6a5abed2', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.030235'),
('5a66e44e-6b44-4b14-ad5c-1a6c1d64b5ea', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032103'),
('63b3a892-f6cd-4149-970a-1a6a6551badd', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032135'),
('570d0f74-eae0-468a-858e-685a3fb85cb4', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032146'),
('974c047e-ddc0-475f-8686-ad8c4f2ec267', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032702'),
('e7d22abf-6f7a-4967-ba1c-a700c675c0ed', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032732'),
('3e0a95ad-3665-489f-8ede-b8a43dbfc2c5', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032739');            
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('1ffe4c1b-488b-4274-9c58-b0603f6751a1', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.03275'),
('f50f49f6-130e-4754-89dc-2e97c02905d6', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032778'),
('c6fb7875-b11e-4b63-8db0-4240f9e5f287', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.03279'),
('8f745f32-c5cd-412b-a31d-ca5127de41dd', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032796'),
('680bba14-0c18-4e90-bee9-52a90e66d1a1', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.032804'),
('2b5535e4-b048-42ac-8621-9f8da7c3382b', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.690608'),
('8d03a82c-ff5e-41b3-b01b-214295990922', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.69066'),
('5244798e-59ce-4976-9526-078a68743c59', 'd6a51e32-957f-4c16-ade2-0ced573009af', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:08.690673'),
('22fb02cd-2d68-4a82-a8a8-c31f76ddf8b6', '35d4c24b-854f-415d-8f3f-4785d190e760', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.735394'),
('748c9ba3-783e-45f6-8291-c85cdf1d3994', '35d4c24b-854f-415d-8f3f-4785d190e760', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.741447'),
('a4ff79fb-cfae-485d-9bc5-9d9ddad4f570', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.741905'),
('495aad5f-07ed-4ec8-b5c7-12ea5a8e5ba6', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.744628'),
('5154cdd2-0b01-44db-b859-2e6c33a5eab1', '35d4c24b-854f-415d-8f3f-4785d190e760', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.745222'),
('055b24a1-4007-4c7c-84b7-5869b4f04b3d', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.74525'),
('8c808947-4488-428c-834a-6243f4b56686', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.746078'),
('d9121048-6077-4eca-abc5-f8319f706992', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.746144'),
('d06e22ee-261c-4fbe-84c8-880a72520f85', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.746194'),
('5e7d9c85-449c-47e1-b07f-6d9c0b420d7a', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.746225'),
('b192f145-10f9-4fc0-8f4b-f68bbdb7e85a', '35d4c24b-854f-415d-8f3f-4785d190e760', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.748448'),
('7a3d4675-d2f1-4a17-9eed-36fb3d800584', '35d4c24b-854f-415d-8f3f-4785d190e760', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.751315'),
('dd2d821a-749d-46ef-94f9-7005a55895d9', '35d4c24b-854f-415d-8f3f-4785d190e760', 'diff', 280, 'PASS', U&'diff --git a/config/feature-flags.yml b/config/feature-flags.yml\000anew file mode 100644\000aindex 0000000..061469b\000a--- /dev/null\000a+++ b/config/feature-flags.yml\000a@@ -0,0 +1 @@\000a+feat: enable experimental flag - 2026-09-04T13:26:09Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.752678'),
('91eb74ae-30d6-4146-ae23-a0cb604aa724', '35d4c24b-854f-415d-8f3f-4785d190e760', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.754062');  
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('86d230f8-18ee-469e-acaf-dc38d7c117a5', '35d4c24b-854f-415d-8f3f-4785d190e760', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:09.754107'),
('dbef546c-adff-4712-96ec-0c27b274257b', '35d4c24b-854f-415d-8f3f-4785d190e760', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:10.451002'),
('37963862-2d30-4d08-841b-ba11a5184ae0', '35d4c24b-854f-415d-8f3f-4785d190e760', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:10.456372'),
('69097eaf-2df5-484c-b3ba-62ce568316e8', '35d4c24b-854f-415d-8f3f-4785d190e760', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:10.456414'),
('c1cc632c-5998-40b3-b32b-ecb9ce065ab3', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:11.693047'),
('071e6391-15f2-4946-89aa-410d7b3978d1', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.921038'),
('48b577b5-d323-49cf-a4ca-69c7edadd5c1', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.921578'),
('162f31c1-24ed-45d9-879c-9b94fb096408', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.923624'),
('46b1c735-5b92-4222-a94d-25330d48e990', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.924326'),
('947b67b4-2163-4247-ba78-9a0f86b808b6', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.924352'),
('1764e66e-4b51-41b8-aa63-05eedaece6a3', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.925485'),
('8a3f010e-e733-4b92-b3e1-ec76e1faa6f5', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.925581'),
('3ab4a734-9b20-4284-941a-ff9c12acb376', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.925655'),
('560e6538-811a-4cc1-bff2-b156be0da3d5', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.925701'),
('f7f63ecf-5c28-4668-9961-37e7968a3cdc', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.928514'),
('18a6cb29-7b8f-4d3a-8bdb-2000f3e62f9d', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.931616'),
('5ed14a86-9b04-4711-93e3-923e5a9f41c4', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'diff', 280, 'PASS', U&'diff --git a/scratch.txt b/scratch.txt\000anew file mode 100644\000aindex 0000000..a4fc78d\000a--- /dev/null\000a+++ b/scratch.txt\000a@@ -0,0 +1 @@\000a+chore: exploratory change, withdrawn - 2026-09-04T13:26:11Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.933146'),
('371d138d-7ba3-49a0-be98-48cf63636d54', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.934703'),
('5d635d2a-60c3-4517-a89b-31f3a152f30a', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:12.93474'),
('9ba7105a-812c-4db6-a916-254f06abff40', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:13.576187'),
('5886cca1-5c70-4c5a-b738-0c1853aa3f27', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:13.58103'),
('d66d03b4-7d7c-446c-854d-6195dfda3046', 'a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:13.581086');  
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('7c782c19-b67e-4438-9594-53659d30c849', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'AllowApprovedPushFilter', 50, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.630648'),
('592a0f5a-44a6-46da-a3b5-e8f6b80b896c', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'EnrichPushCommitsFilter', 60, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.636163'),
('130ce6a5-a17e-486f-9c67-20655c1eff35', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkUrlRules', 100, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.63652'),
('5a3129eb-c96f-4218-8f3c-584389cb2602', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkUserPermission', 150, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.638407'),
('9ef66425-34cb-4a16-adc9-308ce91d13ae', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'commitAttributionPolicy', 160, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.638948'),
('42e9ef6e-2683-4d22-b3fc-c97b403d4214', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkEmptyBranch', 210, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.638968'),
('fe7cc078-be64-4365-8391-abb9ff5023ec', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkHiddenCommits', 220, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.639546'),
('eef11a5c-fb09-4bb7-9946-08b8eb5d2bb0', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkAuthorEmails', 250, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.639614'),
('ab17f065-b30c-40bb-85d0-5609fad4dbd6', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkTrailers', 255, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.639667'),
('a1d1518d-42d9-46a9-9590-0c4362b352bc', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkCommitMessages', 260, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.639705'),
('160c22c2-9cc0-45e1-94ff-683ae7f86222', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'scanContentPatternsMessages', 265, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.641617'),
('d028a293-b326-4105-a78b-b4ae614f615f', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'binaryBlob', 290, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.644307'),
('37bb8fec-49a1-4b02-962f-0c73148adf21', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'diff', 280, 'PASS', U&'diff --git a/docs/faq.md b/docs/faq.md\000anew file mode 100644\000aindex 0000000..412376e\000a--- /dev/null\000a+++ b/docs/faq.md\000a@@ -0,0 +1 @@\000a+docs: answer the most common question - 2026-09-04T13:26:14Z\000a', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.645725'),
('55e7f2ff-9d40-402d-8b32-f154e8d4f2ec', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'scanDiff', 300, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.647018'),
('737b8c35-add9-49b4-8433-e6c3c233bba3', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'checkSignatures', 320, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:14.647075'),
('ca2cea2a-82fa-4863-95f4-0f3c111fa599', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'scanSecrets', 340, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:15.264814'),
('618ad642-8228-49e9-a2a8-bd3f867a2afc', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'scanContentPatternsDiff', 345, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:15.267952'),
('69156bde-db24-46aa-8a6e-382428cb5ac3', '0fb4b457-a7de-4936-86a4-23d6543c365c', 'ValidationSummaryFilter', 2147483644, 'PASS', '', NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:15.267979'),
('43cb5ffe-69aa-4883-ae79-c88a7b71a512', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkUrlRules', 100, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.260493'),
('660f4b95-5657-47ff-b9f9-95ce35105b5d', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkUserPermission', 150, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.770392'),
('aba526af-9e2a-485a-9f46-7a0a424566a0', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'commitAttributionPolicy', 160, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.771097'),
('7efeb86a-3d64-4025-86c0-28f4b60ecbdd', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'commitEnrichment', 195, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.771157');
INSERT INTO "PUBLIC"."PUSH_STEPS" VALUES
('86f2fbd2-1336-40a9-8188-3385b8065533', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkEmptyBranch', 210, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.772918'),
('b2f7c695-1a6b-44ef-a232-3092a59d380c', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkHiddenCommits', 220, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.774953'),
('ce90f9ee-4a3d-42e3-b436-a25ba7236208', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkAuthorEmails', 250, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.775582'),
('84080a22-fc83-4a20-a712-732b82339796', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkTrailers', 255, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.775788'),
('8df7ac50-2c32-43e3-b95f-eb34d354890d', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkCommitMessages', 260, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.776153'),
('d7ac9a21-108d-475c-bcbd-e8a6664caa8f', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'scanContentPatternsMessages', 265, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.77851'),
('49cd5860-2fa1-425f-b139-a05f0293f133', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'inspection', 270, 'PASS', NULL, NULL, NULL, U&'CREATE refs/heads/fixture/ssh-server-375282 0000000 -> 141f43c\000a---LOG---\000aNew branch - tip commit by Fixture Developer <fixture-dev@example.com>', TIMESTAMP '2026-09-04 09:26:17.778841'),
('06e2f4c9-d1ca-48e6-996d-ad54deda579a', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'diff', 280, 'PASS', U&'diff --git a/docs/ssh-transport.md b/docs/ssh-transport.md\000anew file mode 100644\000aindex 0000000..8e647f7\000a--- /dev/null\000a+++ b/docs/ssh-transport.md\000a@@ -0,0 +1 @@\000a+feat: pushed over the SSH transport - 2026-09-04T13:26:16Z\000a', NULL, NULL, U&'ref: refs/heads/fixture/ssh-server-375282\000a---LOG---\000arange: 0000000000000000000000000000000000000000..141f43cd5b84fdd2cf029ff4d50081b30ea74022\000a---LOG---\000alines: 7\000a---LOG---\000atype: auto', TIMESTAMP '2026-09-04 09:26:17.780576'),
('7c66ae7b-2183-4b1a-b06e-facd046403d0', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'binaryBlob', 290, 'PASS', NULL, NULL, NULL, 'PASS: aggregate: refs/heads/fixture/ssh-server-375282', TIMESTAMP '2026-09-04 09:26:17.782939'),
('d75bf638-c2d1-4a99-8470-8c3a741ed1d9', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'scanDiff', 300, 'PASS', NULL, NULL, NULL, 'PASS: aggregate diff', TIMESTAMP '2026-09-04 09:26:17.784394'),
('eaa8859a-c2a4-4e7a-9c7c-d21429c39b9b', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'checkSignatures', 320, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:17.784559'),
('2f942c8a-956f-473c-9b52-e786bc159609', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'scanSecrets', 340, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:18.416724'),
('8c2a33b0-193c-4ad3-988d-576993cb9d2a', '9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'scanContentPatternsDiff', 345, 'PASS', NULL, NULL, NULL, NULL, TIMESTAMP '2026-09-04 09:26:18.420015');        
CREATE INDEX "PUBLIC"."IDX_PUSH_STEPS_PUSH_ID" ON "PUBLIC"."PUSH_STEPS"("PUSH_ID" NULLS FIRST);
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
('35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'go', FALSE, FALSE, TIMESTAMP '2026-09-04 09:26:32.996186', '{"reviewed-content":"true","policy-compliance":"true"}'),
('d6a51e32-957f-4c16-ade2-0ced573009af', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'go', FALSE, FALSE, TIMESTAMP '2026-09-04 09:26:43.549442', '{"reviewed-content":"true","policy-compliance":"true"}'),
('35d4c24b-854f-415d-8f3f-4785d190e760', 'REJECTION', 'reviewer', 'reviewer@example.com', 'stop', FALSE, FALSE, TIMESTAMP '2026-09-04 09:26:53.035718', NULL),
('9334f10a-5dd9-486c-94e9-4ae60c1e74c2', 'APPROVAL', 'reviewer', 'reviewer@example.com', 'yes', FALSE, FALSE, TIMESTAMP '2026-09-04 09:27:00.432354', '{"reviewed-content":"true","policy-compliance":"true"}'),
('a0465faa-9794-4bd2-8b25-9fb3048ef5d4', 'CANCELLATION', 'dev', NULL, NULL, FALSE, FALSE, TIMESTAMP '2026-09-04 09:27:27.912496', NULL),
('0fb4b457-a7de-4936-86a4-23d6543c365c', 'APPROVAL', 'dev', 'fixture-dev@example.com', 'self certifying', FALSE, FALSE, TIMESTAMP '2026-09-04 09:27:39.346988', '{"reviewed-content":"true","policy-compliance":"true"}');              
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
-- 3 +/- SELECT COUNT(*) FROM PUBLIC.USER_SCM_IDENTITIES;      
INSERT INTO "PUBLIC"."USER_SCM_IDENTITIES" VALUES
('reviewer', 'github', 'fixture-reviewer', FALSE),
('dev', 'github', 'fixture-dev', TRUE),
('dev', 'gitlab', 'fixture-dev', TRUE);           
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
('10fa0431-23f5-4ec8-84e3-4144638c24b0', TIMESTAMP '2026-09-04 09:25:14.837925', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('0207eea1-e9fc-4ed0-9b5d-175eeec68d13', TIMESTAMP '2026-09-04 09:25:15.019684', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('4dfc0dd8-298b-4871-8df2-3c49e17b9d22', TIMESTAMP '2026-09-04 09:25:19.575449', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('34291f44-cd35-41eb-96a8-c1eb1df39d00', TIMESTAMP '2026-09-04 09:25:19.97519', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('368c0293-aa62-4d3e-a531-df2b1e80d564', TIMESTAMP '2026-09-04 09:25:21.61564', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('f49b69f6-6628-4b61-9cd5-904757176ef1', TIMESTAMP '2026-09-04 09:25:21.803792', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c85aff15-488b-46c8-bfe8-d174a2538a11', TIMESTAMP '2026-09-04 09:25:23.559116', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('707cc3a1-a2d3-4ff0-8922-2f9f6a588d8e', TIMESTAMP '2026-09-04 09:25:23.862321', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c5bc18bb-1ab2-4cec-a4e8-2d7f589bbb17', TIMESTAMP '2026-09-04 09:25:26.60394', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('e124512c-b09a-4c3d-a8ac-29b30cca4109', TIMESTAMP '2026-09-04 09:25:26.776976', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('35856a28-3c39-411b-8309-04acacd61b7b', TIMESTAMP '2026-09-04 09:25:28.547216', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d7ffee5f-fb3c-4630-b281-3da79feab7e7', TIMESTAMP '2026-09-04 09:25:28.6936', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('c4708426-0b93-4866-a31c-b006f614bdb9', TIMESTAMP '2026-09-04 09:25:30.18612', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('1f0620b4-5773-414b-9984-530486be8ddb', TIMESTAMP '2026-09-04 09:25:30.32609', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('5fc4575f-271e-463a-8e7d-e7767f14b944', TIMESTAMP '2026-09-04 09:25:32.939353', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('ae937c03-5e98-4b8f-ab74-47afe738c04e', TIMESTAMP '2026-09-04 09:25:33.082615', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('76ffda91-2c7a-4882-9d78-7b0d000f2ff6', TIMESTAMP '2026-09-04 09:25:34.795203', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('514e99f7-0c67-4fb2-a762-87e55f8665cc', TIMESTAMP '2026-09-04 09:25:34.947975', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('8049716f-3e3b-4869-9d58-e7122c1e9667', TIMESTAMP '2026-09-04 09:25:36.499597', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('39a71027-a8ce-4666-80c8-7c975787c2e2', TIMESTAMP '2026-09-04 09:25:36.648342', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('176f6787-fff2-4f8d-b9c1-0aab203e23f3', TIMESTAMP '2026-09-04 09:25:39.735169', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('62f9206a-5f89-4924-bc59-b48fe5156da0', TIMESTAMP '2026-09-04 09:25:39.876883', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('cfa99926-7867-404f-8827-859bf9743b36', TIMESTAMP '2026-09-04 09:25:41.419251', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('f5732803-5d00-4c37-9784-dc67e02ce1a8', TIMESTAMP '2026-09-04 09:25:41.784224', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('43cf3a99-7d9a-42e7-a2f2-8ae0cddc1089', TIMESTAMP '2026-09-04 09:25:45.153442', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('82b9d702-1c1b-45b6-9829-f1c220b661f6', TIMESTAMP '2026-09-04 09:25:45.326293', 'codeberg', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL);   
INSERT INTO "PUBLIC"."FETCH_RECORDS" VALUES
('867fac30-0607-4139-9c0c-8764dbf49233', TIMESTAMP '2026-09-04 09:25:49.395061', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('f9bf766f-8e9f-4df6-aff7-c9dc55a7ddc8', TIMESTAMP '2026-09-04 09:25:49.760037', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('5c61b63d-d07d-42ba-b012-fe8a262334cb', TIMESTAMP '2026-09-04 09:25:52.428137', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('57f6436d-6d53-475b-9bd7-9508b39ac7a2', TIMESTAMP '2026-09-04 09:25:52.58515', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('d97a5c37-a682-4646-9dc9-807f9941fa73', TIMESTAMP '2026-09-04 09:25:54.422279', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('2f14b0c0-f51e-4af9-b3e3-4f9183db6a61', TIMESTAMP '2026-09-04 09:25:54.517898', 'gitea', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('fb7a6af1-f0f9-480a-8871-3785dfe12626', TIMESTAMP '2026-09-04 09:25:58.884833', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('3313a92f-5fd7-4c49-a32e-2b974fa51521', TIMESTAMP '2026-09-04 09:25:59.243501', 'gitlab', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('bc0d2321-4a56-41d5-a17b-d7d13df11a8c', TIMESTAMP '2026-09-04 09:26:03.906482', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('8554b771-c507-4aef-b1e3-8e64f902301b', TIMESTAMP '2026-09-04 09:26:04.058133', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('dc2b2b38-3161-4c9e-8a13-bb6a726b5691', TIMESTAMP '2026-09-04 09:26:07.244596', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('4bc245f0-6d90-4e50-a5ec-1ef193424ecf', TIMESTAMP '2026-09-04 09:26:07.392614', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('e86b173c-375a-455a-8f9f-1fc7f84c21e9', TIMESTAMP '2026-09-04 09:26:08.878218', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('63fcd6fd-96f5-422c-8537-902ffea8b31a', TIMESTAMP '2026-09-04 09:26:09.026032', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('2c12fd65-c957-490e-8877-e211b29fb121', TIMESTAMP '2026-09-04 09:26:10.679649', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('b2917f9b-2a4f-470f-b86c-75f1c17874e0', TIMESTAMP '2026-09-04 09:26:11.00717', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('7a5f5fcf-2c09-48ba-b2b6-88399f8d0dd2', TIMESTAMP '2026-09-04 09:26:13.77981', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL),
('4c1b84ea-6cea-4932-847a-162e94262272', TIMESTAMP '2026-09-04 09:26:13.919258', 'github', 'fixture-dev', 'fogwall-fixture', 'ALLOWED', 'me', NULL);     
CREATE INDEX "PUBLIC"."IDX_FETCH_RECORDS_TIMESTAMP" ON "PUBLIC"."FETCH_RECORDS"("TIMESTAMP" NULLS FIRST);      
CREATE INDEX "PUBLIC"."IDX_FETCH_RECORDS_PROVIDER_REPO" ON "PUBLIC"."FETCH_RECORDS"("PROVIDER" NULLS FIRST, "OWNER" NULLS FIRST, "REPO_NAME" NULLS FIRST);     
CREATE CACHED TABLE "PUBLIC"."SCM_TOKEN_CACHE"(
    "TOKEN_HASH" CHARACTER VARYING(128) NOT NULL,
    "PROVIDER" CHARACTER VARYING(100) NOT NULL,
    "PROXY_USERNAME" CHARACTER VARYING(255) NOT NULL,
    "CACHED_AT" TIMESTAMP NOT NULL
);  
ALTER TABLE "PUBLIC"."SCM_TOKEN_CACHE" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_49" PRIMARY KEY("TOKEN_HASH", "PROVIDER");          
-- 0 +/- SELECT COUNT(*) FROM PUBLIC.SCM_TOKEN_CACHE;          
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
-- 5 +/- SELECT COUNT(*) FROM PUBLIC.REPO_PERMISSIONS;         
INSERT INTO "PUBLIC"."REPO_PERMISSIONS" VALUES
('5b2c7714-1e84-41c3-9046-009a6dcb8480', 'dev', 'github', 'PUSH', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('a75120e5-8292-4cc4-ac2e-1e4ff418785c', 'dev', 'github', 'SELF_CERTIFY', 'CONFIG', 'SLUG', '/fixture-dev/fogwall-fixture', 'LITERAL'),
('4a0836aa-8d1d-4771-bcac-856f07eff816', 'dev', 'gitea', 'PUSH', 'CONFIG', 'SLUG', '/fixture-dev/.*', 'REGEX'),
('76e6121b-e48b-4d0e-8250-398c2be8edeb', 'reviewer', 'github', 'REVIEW', 'CONFIG', 'SLUG', '/fixture-dev/*', 'GLOB'),
('bd59c764-38a4-45bc-bdb5-124e5ab3ba02', 'reviewer', 'gitlab', 'PUSH_AND_REVIEW', 'CONFIG', 'SLUG', '/fixture-dev/fogwall-fixture', 'LITERAL');          
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
('e006c749-08d5-418f-9b6f-9abf8f0634af', 'platform-reviewers', 'Reviewers for the fixture-dev org', 'CONFIG'),
('7aa22fda-d99b-4480-a524-bdcedef261da', 'gitlab-contributors', 'Push access to GitLab test repos', 'CONFIG');  
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
('6b6a8b3f-235a-4408-ba2d-c46a71e5b053', 'e006c749-08d5-418f-9b6f-9abf8f0634af', 'github', 'SLUG', '/fixture-dev/.*', 'REGEX', 'REVIEW'),
('1b25c118-39d5-438f-a270-40ab7c9f30a5', '7aa22fda-d99b-4480-a524-bdcedef261da', 'gitlab', 'SLUG', '/fixture-dev/*', 'GLOB', 'PUSH');
CREATE INDEX "PUBLIC"."IDX_GROUP_PERMISSIONS_GROUP_ID" ON "PUBLIC"."GROUP_PERMISSIONS"("GROUP_ID" NULLS FIRST);
CREATE INDEX "PUBLIC"."IDX_GROUP_PERMISSIONS_PROVIDER" ON "PUBLIC"."GROUP_PERMISSIONS"("PROVIDER" NULLS FIRST);
CREATE CACHED TABLE "PUBLIC"."GROUP_MEMBERS"(
    "GROUP_ID" CHARACTER VARYING(36) NOT NULL,
    "USERNAME" CHARACTER VARYING(255) NOT NULL
); 
ALTER TABLE "PUBLIC"."GROUP_MEMBERS" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_F180" PRIMARY KEY("GROUP_ID", "USERNAME");            
-- 3 +/- SELECT COUNT(*) FROM PUBLIC.GROUP_MEMBERS;            
INSERT INTO "PUBLIC"."GROUP_MEMBERS" VALUES
('e006c749-08d5-418f-9b6f-9abf8f0634af', 'reviewer'),
('e006c749-08d5-418f-9b6f-9abf8f0634af', 'admin'),
('7aa22fda-d99b-4480-a524-bdcedef261da', 'dev');          
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
('b87bf599-718d-42d8-817f-e152af63a26a', '9feee63bf778d608655ceaefd44d9968dcdfc675', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'noreply@example.com', 'Fixture Developer', 'noreply@example.com', U&'feat: this commit has a noreply author\000a\000aSigned-off-by: Fixture Developer <noreply@example.com>\000a', TIMESTAMP '2026-09-04 09:25:15', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGuwAKCRA3V+o4iAJ3\000a51BEAP9EDP0KWkKgBvMzFwrmPpiea2hzqs8K+O1kjogpDcXO+QD/Vpd+YT1RiZpC\000aowQyXexo/2j+4oJtAatpMKZmqqOVwwY=\000a=QHxP\000a-----END PGP SIGNATURE-----', 'Fixture Developer <noreply@example.com>', NULL),
('c42c0000-a867-431e-b9c3-38ba5feeb309', '8a582e8a4d331ef44cb88a9d805c98c6f9da0f18', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'developer@internal.corp.net', 'Fixture Developer', 'developer@internal.corp.net', U&'feat: this commit comes from an unapproved domain\000a\000aSigned-off-by: Fixture Developer <developer@internal.corp.net>\000a', TIMESTAMP '2026-09-04 09:25:20', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGwAAKCRA3V+o4iAJ3\000a59JeAQDVNzhe60NH7X9l6zg12tZc9uAqbcgRLo4ahiLCGwz0swEAnGHmIiELUmJp\000aNItAQRIJtv1p8SQS+3h3ysVSZDqmaQ8=\000a=jqbB\000a-----END PGP SIGNATURE-----', 'Fixture Developer <developer@internal.corp.net>', NULL),
('300d9241-374e-4295-b2da-93eaf77a724b', '9ee01b4d0bc14a88f87a82d646554813e36c3c02', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'WIP: still working on this feature\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:22', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGwgAKCRA3V+o4iAJ3\000a5zXnAP4gUE4oQT9/tX4yaPcqB9TzCwIzt6ng4veVnJpQ5aWbRAD8CnfpBwGM7pZl\000apws+AlJRaH+Hi7zgkrMFLiHnFq+5KA4=\000a=KR2K\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('9e99502e-4e3c-4a03-9249-aefca1492d6e', '4ec92f0526711ce6084e45f1234ccd707db4deef', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: rotate token=[REDACTED] in CI config\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:24', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGxAAKCRA3V+o4iAJ3\000a50VoAQDihwko2f/jLDSBKsjDvts2+ZFp5h92+sYAW6fgni/zVgD9Gomqp69GePPi\000ar/2gXoI7gdMKL1SI4k2q+/5M2OuCRQI=\000a=17wH\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('0fc5e35a-02b1-46ed-9344-d6dd011a457a', '6d144236b1f51554b68b0fd0de28195418ddb958', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add deployment credentials\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:27', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGxwAKCRA3V+o4iAJ3\000a55weAP45+GHdmOXau0MqnfBFEcfTTYYgljZr5oN/02Af7oypFgEA41VdBrMFbAf+\000aQkgg8H0FE288xTKoSnLp1CsB8fueQgU=\000a=DpVb\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('47cf6c3e-cff8-4d91-aa3e-4287bf720dcb', 'd096de731b833e9529f74c440b5b68f3708f6efd', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add upstream config\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:28', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHQEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGyAAKCRA3V+o4iAJ3\000a5zR+APjbiFGma3Q1TI57vwURt39CiYwsgfobeljp0l+LH0/JAQC7ckW+rB777k4n\000arr35Y5gCk6LKst8A2zv/Hv2rwTYlDQ==\000a=D7R3\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('80c0c60b-3d11-457d-a291-b9d25d560aae', '17d3a634000735264b33878f50b1ae1459cbe0b3', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: add deployment script\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:30', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGygAKCRA3V+o4iAJ3\000a52U4AQCePGjc4SThm+m3xDy5J2JBbQEpn69VXp36lWHUnKxruAD8DM5/j99toExx\000aYR/xsPGISvp2ePtP6YX/ccis0K9KIQk=\000a=rzFG\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);          
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('a141dd29-f637-47bb-a76b-6ed8b04fe2a4', '487ddad222208a8be01f0c5c748f0d54683939b4', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: forgot to sign off\000a', TIMESTAMP '2026-09-04 09:25:33', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGzQAKCRA3V+o4iAJ3\000a5xSfAP9TrLYI7t9QzWqD/90MQMKNgxsqSzMO6/GhNhzpoHn9dwD/W33V2B2PEgf/\000aKvWdAaxd5WmPJyADCC+x8znXQ4b0tQE=\000a=WfHN\000a-----END PGP SIGNATURE-----', NULL, NULL),
('820d9d93-bdf8-41c4-b6a9-0239e7f3082d', 'cb8eb431ef3aa447eb05c944c1fcdc98351cd0bd', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: signed off by the wrong person\000a\000aSigned-off-by: Someone Else <someone.else@example.com>\000a', TIMESTAMP '2026-09-04 09:25:35', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprGzwAKCRA3V+o4iAJ3\000a5zF8AP0WVqYNrIdBTk9j/qCTBY98P5kT5BbHoVOkiZa/+mvsIwEAoZe7xxjvalnf\000aNvLEYO8v6M1inpPevQUZL1trCwHeEwM=\000a=1v2c\000a-----END PGP SIGNATURE-----', 'Someone Else <someone.else@example.com>', NULL),
('8e4efa12-756c-48db-9f76-513ce84ff245', 'd325a251f66e63cef79bd247b43d4d2e45d0f0d9', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: paired with an outside contractor\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000aCo-authored-by: Contractor <contractor@outside.example.net>\000a', TIMESTAMP '2026-09-04 09:25:36', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG0AAKCRA3V+o4iAJ3\000a55r9AQCrUh5h9Ohaeg3BajxCcU8c67w2U7ILCxPR8J8+TlbN/wD+K2IlIPDPlmiM\000aCZXV4RGqusmGdkRnt6t4/htT4W0Xhwc=\000a=USz2\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', 'Contractor <contractor@outside.example.net>'),
('15106dea-fb5f-4f22-a711-b3497932bc2d', 'f93580ff839680c57ab6451b94b9a162cd179e8e', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: pair-programmed with an allow-listed co-author\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000aCo-authored-by: Claude <noreply@anthropic.com>\000aCo-authored-by: Pair Partner <pair@example.com>\000a', TIMESTAMP '2026-09-04 09:25:40', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1AAKCRA3V+o4iAJ3\000a53oTAQCCbLuskyme5sOQgBwiorH9qAx5JrHsibz+axcYdyxwNwD/VekeuDwvhXie\000aZM3NNby6XQRE3QCe7BLVtWerQvn6OQk=\000a=V01v\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', U&'Claude <noreply@anthropic.com>\000aPair Partner <pair@example.com>'),
('cd966354-5012-4826-8f1b-8548b89d8350', '30e0d1cfebf2ab2874983046888787df0f899d48', '559ccfca7949a7c7d5560e11d330bc4b8bb034df', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 6 \2014 missing DCO sign-off\000a', TIMESTAMP '2026-09-04 09:25:42', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1gAKCRA3V+o4iAJ3\000a5xFtAQDggTT95FOKDYkpTCR/XDk7sY7OiBwijaJecHHhk0tiuQD/YZ3KE6GFHkIo\000aiInFggJter8yZVapOugWlah0ln5z/gU=\000a=3b8v\000a-----END PGP SIGNATURE-----', NULL, NULL),
('cd966354-5012-4826-8f1b-8548b89d8350', '559ccfca7949a7c7d5560e11d330bc4b8bb034df', '5069c7861b41e6499723805156b144fe9f1ca631', 'Fixture Developer', 'unregistered@example.com', 'Fixture Developer', 'unregistered@example.com', U&'test: commit 5 \2014 unregistered commit email\000a\000aSigned-off-by: Fixture Developer <unregistered@example.com>\000a', TIMESTAMP '2026-09-04 09:25:42', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1gAKCRA3V+o4iAJ3\000a59eUAQD0LKMhKodw2fJra26Oq0QH22mFFEi14Co1WFcYhf8VcgEA8NafGXqPpds8\000aCI3WtFqSvSjEqzbHB9a4a0VxA9QkSgw=\000a=UUU9\000a-----END PGP SIGNATURE-----', 'Fixture Developer <unregistered@example.com>', NULL);       
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('cd966354-5012-4826-8f1b-8548b89d8350', '5069c7861b41e6499723805156b144fe9f1ca631', 'c7c4e126e8ea51355ee9f01405fc73899b18f226', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 4 \2014 blocked hostname in diff\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:42', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1gAKCRA3V+o4iAJ3\000a51yyAP0VeW0pr1NId0z/BkTVfSihwSivD5LZOQmPJ/IRsl5PxAD6Al1qb3OCj8xW\000aC6qX6yE/Rr7XTNppjc3RxyynN91b5Qk=\000a=Fsy8\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('cd966354-5012-4826-8f1b-8548b89d8350', 'c7c4e126e8ea51355ee9f01405fc73899b18f226', 'ab62b227886e753bceec3e5c404e0c4e60d8efc3', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: commit 3 \2014 github pat in diff\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:42', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1gAKCRA3V+o4iAJ3\000a5+f9AQDrtkhlZtXODAVibzNIhU9vutZ8C2glHhRCA35hG+G/UAD/VrrdU4Uy+plZ\000aaXCJ2Mcgt7OBI7UHm3KzqCu5bZ//FAc=\000a=fDas\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('cd966354-5012-4826-8f1b-8548b89d8350', 'ab62b227886e753bceec3e5c404e0c4e60d8efc3', 'b8b93be2cda9b9ae2646b7a37fdd9d82eefbb88f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'WIP: commit 2 \2014 bad commit message\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:42', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1gAKCRA3V+o4iAJ3\000a52pWAQDoC2rb4ULPJGaY7lDUB1Smuz4mZP1N94YxEFU4ycOT2AD/UTS84sWXl+UT\000aA3Z04CglihvZp8cLRPLtBTerKIrROQM=\000a=5zVU\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('cd966354-5012-4826-8f1b-8548b89d8350', 'b8b93be2cda9b9ae2646b7a37fdd9d82eefbb88f', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'noreply@example.com', 'Fixture Developer', 'noreply@example.com', U&'test: commit 1 \2014 noreply author email\000a\000aSigned-off-by: Fixture Developer <noreply@example.com>\000a', TIMESTAMP '2026-09-04 09:25:41', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG1QAKCRA3V+o4iAJ3\000a56KPAQCr0hDRPkdn2l9bys8noupj00wdqi9IE9RXnOS8yN4/JAEArfpaKzF4O8jC\000aZ+UHOfeywsYKU3yf8OhBGpnuYz/oMQU=\000a=UM8D\000a-----END PGP SIGNATURE-----', 'Fixture Developer <noreply@example.com>', NULL),
('470f8a1e-1133-4ba4-a4f4-08ab171eced4', '5be83e6555ca34b386b3db3d99b0641b41a7156e', '406ceb6519b8ecfb398059b9306d1ea0a51e82ff', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'test: identity resolution \2014 codeberg unresolved\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:45', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG2QAKCRA3V+o4iAJ3\000a51awAP0Q84eQFjIFXNN1OAJT0Pz+rGXtNOdFFiSGmjm+mtxNSgD/dmohKHXoc+O9\000aZLU6B2BnTK3T5//TyNAi9fBZoO3JigA=\000a=AGzu\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('2756e624-5b3f-493f-b778-e68696e5b212', 'd4b4334e400e898eb120a9776981287b1ca8b1a8', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: add release notes stub\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:50', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG3gAKCRA3V+o4iAJ3\000a52tBAP4gn5icb6WZwFdOHUjairGQP3qpVbaxKYFEcnFBniOqNAD/QVT5eueYWxHI\000aR3e445bzd5o8gup5JVOJmlPSmnKcjAY=\000a=hTxB\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('2b19a4e7-4f4f-455c-beeb-f1b8f7f8d5bc', '6ea68cb981667db31b4de4dd9451fa93c720d510', '720212ce6d4790c648445bc2145fec4cef6c163e', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: touch readme via fogwall\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:25:54', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG4gAKCRA3V+o4iAJ3\000a5698AQCWD2GBBDzXMxon4bVVRdUVDF2LZakhQmw+nRbkFi+RRQEA8vZtuqfDYa68\000aYPaNyetBsYbZ749EEXJs/fmdNxtAmAo=\000a=COKv\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);             
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('479f6e01-8769-4394-bd4c-c02cf956ff59', 'b4124376a179bdfae07e6f604442478886cddcef', '0f9518e3c16c57924343f4c7fb5ff3cbd9c2e58c', 'Fixture Developer', 'unregistered@example.com', 'Fixture Developer', 'unregistered@example.com', U&'test: identity resolution \2014 gitlab resolved, email unregistered\000a\000aSigned-off-by: Fixture Developer <unregistered@example.com>\000a', TIMESTAMP '2026-09-04 09:25:59', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG5wAKCRA3V+o4iAJ3\000a57BFAP9rT4232ckbZbamsub1z8mmMdAI8U8glQLw42udYJf2kwEA8F8p146ouKpJ\000afOmfZDPpLYsM/JgVz1EZhHmQpt9XlQQ=\000a=qT9u\000a-----END PGP SIGNATURE-----', 'Fixture Developer <unregistered@example.com>', NULL),
('35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'e2fc2b68beb5d01b6db0a14aedc964ad0b4232b9', '0e5626f41961f38b36dd58dfdebb1f17380edf2c', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'fix(gamma): third of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:04', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG7AAKCRA3V+o4iAJ3\000a5wHTAQDv0bJ/2bqOoBG/tTs2Vs/J+4BXrgs+fF+mKR7EVK+2vAEApPgGIAtXyk3N\000a0ofePaaSrCjtpyj08aaeNtdGe+9PlQw=\000a=cFIh\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('35e887d9-3bf6-4305-aaa5-156f22a0fe2e', '0e5626f41961f38b36dd58dfdebb1f17380edf2c', 'dadf6f0c48d9244c938b1d1612cd38275bb9231f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat(beta): second of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:04', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG7AAKCRA3V+o4iAJ3\000a54FEAP9C0IO7OzyBA9O6EWhc+1B8t6bQLjUmavhYPEPZzV2tHAEAm/hDFyY7hfPm\000aFbW0eMOus7zM9spFpGdNYQ7ABSYRkwk=\000a=faBk\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('35e887d9-3bf6-4305-aaa5-156f22a0fe2e', 'dadf6f0c48d9244c938b1d1612cd38275bb9231f', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat(alpha): first of three\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:04', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG7AAKCRA3V+o4iAJ3\000a52NpAP0d4QrFcdzpIXysz4gATLekNInNLqZthEfduIAjMfjejQD9HyxZuRAqcFe6\000apXRo+s6xO/8hhHDCjmORvyk5fdR7QQI=\000a=wo/u\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('35d4c24b-854f-415d-8f3f-4785d190e760', '0293bf4a616137a640846fefd3b667bab2c05a73', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: enable experimental flag\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:09', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG8QAKCRA3V+o4iAJ3\000a51DDAQC+/c+ieI8EbRKwy6irLWPte0ao27zniKilngKp+S9QGQEAjKXLUNkCFrWe\000ajmvZx6f6HCBiLWI1oCXQ79p30DdNqQw=\000a=CALY\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('a0465faa-9794-4bd2-8b25-9fb3048ef5d4', '1ff950a566b5d53459e7c5e92565e1fc8070c357', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'chore: exploratory change, withdrawn\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:11', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG8wAKCRA3V+o4iAJ3\000a598tAP967dgH9FSoJdULGgjqtCioFXua0smIRm7h93yaF/hLDQEAhoBfkPuHHWHp\000a6ojjVHAqbImjiG/ogaBZICWkcNA/VAU=\000a=jt1n\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL),
('0fb4b457-a7de-4936-86a4-23d6543c365c', 'fb630d86aa61b2351096225b5570560eb4e2642c', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'docs: answer the most common question\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:14', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG9gAKCRA3V+o4iAJ3\000a5wyWAP97hz6s70RRvmail7xo6JVsK5QxYCdHrulNF5Lna6dKxwEAk4ZhzzXfdWao\000aVo8eomob7dNUysGLJYN+hnzSlDY1Vgk=\000a=fxB2\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);               
INSERT INTO "PUBLIC"."PUSH_COMMITS" VALUES
('9334f10a-5dd9-486c-94e9-4ae60c1e74c2', '141f43cd5b84fdd2cf029ff4d50081b30ea74022', '4019c432539cf2f237ccf6e9941f34d15f07c49f', 'Fixture Developer', 'fixture-dev@example.com', 'Fixture Developer', 'fixture-dev@example.com', U&'feat: pushed over the SSH transport\000a\000aSigned-off-by: Fixture Developer <fixture-dev@example.com>\000a', TIMESTAMP '2026-09-04 09:26:16', U&'-----BEGIN PGP SIGNATURE-----\000a\000aiHUEABYKAB0WIQQ/ZY47ZgZl37WYCyU3V+o4iAJ35wUCaprG+AAKCRA3V+o4iAJ3\000a5ywEAQDRwUDod1mTO+osKVSQ5hmT9lQuizQDmDG66ORw/DWzRwEAhKyV9CTg7b7B\000axzcov3l9xuSim056Y8OcpZsHraYtCA8=\000a=SigL\000a-----END PGP SIGNATURE-----', 'Fixture Developer <fixture-dev@example.com>', NULL);  
CREATE INDEX "PUBLIC"."IDX_PUSH_COMMITS_PUSH_ID" ON "PUBLIC"."PUSH_COMMITS"("PUSH_ID" NULLS FIRST);            
ALTER TABLE "PUBLIC"."USER_EMAILS" ADD CONSTRAINT "PUBLIC"."UQ_USER_EMAILS_EMAIL" UNIQUE NULLS DISTINCT ("EMAIL");             
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
