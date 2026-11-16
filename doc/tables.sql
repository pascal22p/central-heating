-- Adminer 6.0.1 MariaDB 12.3.3-MariaDB-ubu2404-log dump

SET NAMES utf8;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;
SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `admins`;
CREATE TABLE `admins` (
                          `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
                          `email` varchar(25) NOT NULL,
                          `password` varchar(256) NOT NULL,
                          `is_admin` tinyint(1) NOT NULL,
                          `nest_refresh_token` text DEFAULT NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


DROP TABLE IF EXISTS `cache`;
CREATE TABLE `cache` (
                         `key` varchar(255) NOT NULL,
                         `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`data`)),
                         `expires_at` timestamp NOT NULL,
                         PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


DROP TABLE IF EXISTS `devices`;
CREATE TABLE `devices` (
                           `topic` int(11) NOT NULL,
                           `deviceType` varchar(255) NOT NULL,
                           `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`data`)),
                           `expires_at` timestamp(6) NOT NULL,
                           PRIMARY KEY (`topic`),
                           KEY `trvs_expires_at` (`expires_at`),
                           CONSTRAINT `fk_devices_topic_id` FOREIGN KEY (`topic`) REFERENCES `topics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `heating_override`;
CREATE TABLE `heating_override` (
                                    `id` tinyint(3) unsigned NOT NULL,
                                    `mode` enum('heat','idle') NOT NULL,
                                    `expires_at` datetime DEFAULT NULL,
                                    PRIMARY KEY (`id`),
                                    CONSTRAINT `heating_override_singleton` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `nest_authorisation`;
CREATE TABLE `nest_authorisation` (
                                      `id` tinyint(3) unsigned NOT NULL,
                                      `refresh_token` text NOT NULL,
                                      `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
                                      `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                                      PRIMARY KEY (`id`),
                                      CONSTRAINT `nest_authorisation_singleton` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


DROP TABLE IF EXISTS `schedules`;
CREATE TABLE `schedules` (
                             `id` int(11) NOT NULL AUTO_INCREMENT,
                             `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                             `schedule` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


DROP TABLE IF EXISTS `sessions`;
CREATE TABLE `sessions` (
                            `sessionId` varchar(36) NOT NULL,
                            `sessionData` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`sessionData`)),
                            `timeStamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
                            PRIMARY KEY (`sessionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `thermostat_state`;
CREATE TABLE `thermostat_state` (
                                    `device` varchar(255) NOT NULL,
                                    `state` enum('heat','idle') NOT NULL,
                                    `changed_at` datetime NOT NULL,
                                    PRIMARY KEY (`device`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `topics`;
CREATE TABLE `topics` (
                          `id` int(11) NOT NULL AUTO_INCREMENT,
                          `name` varchar(255) NOT NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `topic_schedule`;
CREATE TABLE `topic_schedule` (
                                  `id` int(11) NOT NULL AUTO_INCREMENT,
                                  `topic` int(11) NOT NULL,
                                  `schedule` int(11) NOT NULL,
                                  `schedule_number` tinyint(3) unsigned NOT NULL DEFAULT 1,
                                  PRIMARY KEY (`id`),
                                  KEY `topic` (`topic`),
                                  KEY `schedule` (`schedule`),
                                  CONSTRAINT `1` FOREIGN KEY (`topic`) REFERENCES `topics` (`id`) ON DELETE NO ACTION,
                                  CONSTRAINT `2` FOREIGN KEY (`schedule`) REFERENCES `schedules` (`id`) ON DELETE NO ACTION,
                                  CONSTRAINT `chk_topic_schedule_number` CHECK (`schedule_number` between 1 and 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;


-- 2026-11-16 18:07:14 UTC