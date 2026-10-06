DROP TABLE IF EXISTS `sessions`;
CREATE TABLE `sessions` (
                                 `sessionId` varchar(36) NOT NULL,
                                 `sessionData` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`sessionData`)),
                                 `timeStamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                                 PRIMARY KEY (`sessionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `admins`;
CREATE TABLE `admins` (
                               `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
                               `email` varchar(25) NOT NULL,
                               `password` varchar(256) NOT NULL,
                               `is_admin` tinyint(1) NOT NULL,
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS `nest_authorisation`;
CREATE TABLE `nest_authorisation` (
                                      `id` tinyint unsigned NOT NULL,
                                      `refresh_token` text NOT NULL,
                                      `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`id`),
                                      CONSTRAINT `nest_authorisation_singleton`
                                          CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
