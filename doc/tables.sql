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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `nest_authorisation`;
CREATE TABLE `nest_authorisation` (
                                      `id` tinyint unsigned NOT NULL,
                                      `refresh_token` text NOT NULL,
                                      `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`id`),
                                      CONSTRAINT `nest_authorisation_singleton`
                                          CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `devices`;
CREATE TABLE `devices` (
                        `name` varchar(255) NOT NULL,
                        `deviceType` varchar(255) NOT NULL,
                        `data` JSON NOT NULL,
                        `expires_at` timestamp(6) NOT NULL,
                        PRIMARY KEY (`name`, `deviceType`),
                        KEY `trvs_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `sensor_schedules`;
CREATE TABLE sensor_schedules (
                                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                  topic VARCHAR(255) NOT NULL,
                                  periods JSON NOT NULL,
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uq_sensor_schedules_topic (topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `cache`;
CREATE TABLE `cache` (
                         `key` varchar(255) NOT NULL,
                         `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`data`)),
                         `expires_at` timestamp NOT NULL,
                         PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `heating_override`;
CREATE TABLE `heating_override` (
                                    `id` tinyint unsigned NOT NULL,
                                    `mode` enum('heat','idle') NOT NULL,
                                    `expires_at` datetime DEFAULT NULL,
                                    PRIMARY KEY (`id`),
                                    CONSTRAINT `heating_override_singleton`
                                        CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `thermostat_state`;
CREATE TABLE `thermostat_state` (
                                    `device` varchar(255) NOT NULL,
                                    `state` enum('heat','idle') NOT NULL,
                                    `changed_at` datetime NOT NULL,
                                    PRIMARY KEY (`device`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;