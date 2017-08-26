# --- !Ups
CREATE TABLE `Users` (
  `id` CHAR(48) NOT NULL PRIMARY KEY,
  `user_name` CHAR(124) NOT NULL UNIQUE,
  `full_name` CHAR(124),
  `photo_url` TEXT,
  `email_address` CHAR(48),
  `reputation` FLOAT NOT NULL DEFAULT '1.0',
  `password` CHAR(48) NOT NULL,
  `is_invisible` BOOLEAN NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE `UserProviderAccounts` (
  `firebase_id` CHAR(48) NOT NULL,
  `user_id` CHAR(48) NOT NULL,
  `provider` CHAR(48) NOT NULL,
  `user_name` CHAR(124) NOT NULL,
  `provider_id` CHAR(48),
  `access_token` TEXT,
  `firebase_token` TEXT,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES Users(id),
  PRIMARY KEY (firebase_id)
);

CREATE TABLE `Repositories` (
  `id` CHAR(36) NOT NULL PRIMARY KEY,
  `name` TEXT NOT NULL,
  `is_private` BOOLEAN NOT NULL,
  `is_push_vote` BOOLEAN NOT NULL,
  `owner_id` CHAR(48) NOT NULL,
  `is_review_stash` BOOLEAN DEFAULT 1,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_id) REFERENCES Users(id)
);

CREATE TABLE `Projects` (
  `id` CHAR(36) NOT NULL PRIMARY KEY,
  `name` TEXT NOT NULL,
  `owner_id` CHAR(36) NOT NULL,
  `repository_id` CHAR(36) NOT NULL,
  `goals` TEXT DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (repository_id) REFERENCES Repositories(id),
  FOREIGN KEY (owner_id) REFERENCES Users(id)
);

CREATE TABLE `Topics` (
  `id` CHAR(255) NOT NULL PRIMARY KEY,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE `ProjectTopics` (
  `id` CHAR(36) NOT NULL PRIMARY KEY,
  `project_id` CHAR(36) NOT NULL,
  `topic_id` CHAR(36) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES Projects(id),
  FOREIGN KEY (topic_id) REFERENCES Topics(id)
);

CREATE TABLE `Stashes` (
  `id` CHAR(36) NOT NULL PRIMARY KEY,
  `owner_id` CHAR(48) NOT NULL,
  `repository_id` CHAR(36) NOT NULL,
  `stash_num` INT NOT NULL,
  `base_oid` CHAR(48) NOT NULL,
  `current_oid` CHAR(48) NOT NULL,
  `is_merged` BOOLEAN NOT NULL,
  `vote_treshold` FLOAT NOT NULL,
  `is_online` BOOLEAN NOT NULL,
  `description` TEXT,
  `title` TEXT,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (repository_id) REFERENCES Repositories(id)
);

CREATE TABLE `StashComments` (
  `id` CHAR(36) NOT NULL PRIMARY KEY,
  `content` TEXT NOT NULL,
  `owner_id` CHAR(48) NOT NULL,
  `stash_id` CHAR(36) NOT NULL,
  `parent_id` CHAR(36),
  `total_up_vote_points` float NOT NULL DEFAULT '0',
  `total_down_vote_points` float NOT NULL DEFAULT '0',
  `total_all_vote_points` float NOT NULL DEFAULT '0',
  `up_vote_count` int(11) NOT NULL DEFAULT '0',
  `down_vote_count` int(11) NOT NULL DEFAULT '0',
  `all_vote_count` int(11) NOT NULL DEFAULT '0',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (stash_id) REFERENCES Stashes(id),
  FOREIGN KEY (owner_id) REFERENCES Users(id),
  FOREIGN KEY (parent_id) REFERENCES StashComments(id)
 );

CREATE TABLE `StashVotes` (
  `owner_id` CHAR(48) NOT NULL,
  `stash_id` CHAR(36) NOT NULL,
  `vote_point` FLOAT NOT NULL,
  `is_vote_up` BOOLEAN NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (stash_id) REFERENCES Stashes(id),
  FOREIGN KEY (owner_id) REFERENCES Users(id),
  PRIMARY KEY (owner_id, stash_id)
);

CREATE TABLE `StashCommentVotes` (
  `owner_id` CHAR(48) NOT NULL,
  `stash_comment_id` CHAR(36) NOT NULL,
  `vote_point` FLOAT NOT NULL,
  `is_vote_up` BOOLEAN NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (stash_comment_id) REFERENCES StashComments(id),
  FOREIGN KEY (owner_id) REFERENCES Users(id),
  PRIMARY KEY (owner_id, stash_comment_id)
);

# --- !Downs
DROP TABLE `ProjectTopics`;
DROP TABLE `Topics`;
DROP TABLE `Projects`;
DROP TABLE `StashCommentVotes`;
DROP TABLE `StashVotes`;
DROP TABLE `StashComments`;
DROP TABLE `Stashes`;
DROP TABLE `Repositories`;
DROP TABLE `UserProviderAccounts`;
DROP TABLE `Users`;
