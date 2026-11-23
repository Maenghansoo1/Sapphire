-- 미션 틀 선택 밴딧(TODO_2/D2): 틀(팔)별 Beta 통계와 반영 기록. 선택기 연결은 D4, 주기 반영은 D3.
-- 전역(GLOBAL) 행은 group_id = 0 으로 둔다: MariaDB UNIQUE 는 NULL 을 여러 개 허용해서 NULL 이면 전역 행이 중복될 수 있다.
-- 그래서 group_id 에는 FK 를 걸지 않고, 그룹을 지울 때 서비스가 GROUP 행을 먼저 지운다(GroupService.deleteGroupCascade).
-- 전역 행에는 그룹을 알아볼 값이 없다(집계만).
CREATE TABLE `template_bandit_stats` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `scope` varchar(10) NOT NULL,
  `group_id` bigint(20) NOT NULL DEFAULT 0,
  `template` varchar(40) NOT NULL,
  `alpha_real` double NOT NULL DEFAULT 0,
  `beta_real` double NOT NULL DEFAULT 0,
  `alpha_syn` double NOT NULL DEFAULT 0,
  `beta_syn` double NOT NULL DEFAULT 0,
  `n_missions` int(11) NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_bandit_stats` (`scope`, `group_id`, `template`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 보상을 이미 반영한 미션(같은 미션을 두 번 반영하지 않게). 미션을 지우기 전에 먼저 지운다.
CREATE TABLE `template_bandit_applied` (
  `mission_id` bigint(20) NOT NULL,
  `applied_at` datetime NOT NULL,
  PRIMARY KEY (`mission_id`),
  CONSTRAINT `fk_template_bandit_applied_mission` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 틀을 어떤 정책으로 골랐는지와 그때의 선택 확률(D4 가 채움, 오프폴리시 평가용). 지금까지의 미션은 NULL.
ALTER TABLE missions ADD COLUMN template_policy VARCHAR(10) NULL, ADD COLUMN selection_prob DOUBLE NULL;
