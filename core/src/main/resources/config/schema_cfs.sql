DROP DATABASE IF EXISTS cfs_task8;
CREATE DATABASE  cfs_task8;
USE cfs_task8;

CREATE TABLE IF NOT EXISTS cfs_task8.Employee (
  id           INT         NOT NULL PRIMARY KEY AUTO_INCREMENT,
  userName     VARCHAR(32) NOT NULL,
  firstName    VARCHAR(32) NOT NULL,
  lastName     VARCHAR(32) NOT NULL,
  password     VARCHAR(256)                     DEFAULT NULL,
  sessionToken VARCHAR(256)                     DEFAULT NULL,
  addressLine1 VARCHAR(256)                     DEFAULT NULL,
  addressLine2 VARCHAR(256)                     DEFAULT NULL,
  city         VARCHAR(256)                     DEFAULT NULL,
  state        VARCHAR(32)                      DEFAULT NULL,
  zipcode      VARCHAR(32)                      DEFAULT NULL,
  salt         INT                              DEFAULT 0,
  type         TINYINT                          DEFAULT 0,
  status       TINYINT                          DEFAULT 0,
  createdAt    TIMESTAMP                        DEFAULT CURRENT_TIMESTAMP,
  updatedAt    TIMESTAMP                        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY userNameUniqueKey (`userName`)
);

INSERT IGNORE
INTO cfs_task8.Employee
VALUES (NULL, 'jadmin', 'Jane', 'Admin', 'admin', '123456',
              '123 Main street', 'Suite 305', 'Pittsburgh',
              'Pa', '15143', 0, 1, 0, NULL, NULL);

INSERT IGNORE
INTO cfs_task8.Employee
VALUES (NULL, 'jeff', 'Jane', 'Admin', 'teamnine', '123456',
              '123 Main street', 'Suite 305', 'Pittsburgh',
              'Pa', '15143', 0, 1, 0, NULL, NULL);

CREATE TABLE IF NOT EXISTS cfs_task8.Customer (
  id                INT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
  userName          VARCHAR(32)  NOT NULL,
  firstName         VARCHAR(32)  NOT NULL,
  lastName          VARCHAR(32)  NOT NULL,
  addressLine1      VARCHAR(256) NOT NULL,
  city              VARCHAR(256) NOT NULL,
  state             VARCHAR(32)  NOT NULL,
  zipcode           VARCHAR(32)  NOT NULL,
  salt              INT                               DEFAULT 0,
  password          VARCHAR(256)                      DEFAULT NULL,
  sessionToken      VARCHAR(256)                      DEFAULT NULL,
  addressLine2      VARCHAR(256)                      DEFAULT NULL,
  cash              BIGINT                            DEFAULT 0,
  cashToBeDeposited BIGINT                            DEFAULT 0,
  cashToBeChecked   BIGINT                            DEFAULT 0,
  status            TINYINT                           DEFAULT 0,
  createdAt         TIMESTAMP                         DEFAULT CURRENT_TIMESTAMP,
  updatedAt         TIMESTAMP                         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY userNameUniqueKey (`userName`)
);

CREATE TABLE IF NOT EXISTS cfs_task8.Fund (
  id                INT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
  fundName          VARCHAR(256) NOT NULL,
  lastTransitionDay TIMESTAMP    NULL,
  symbol            VARCHAR(256)                      DEFAULT NULL,
  comment           VARCHAR(512)                      DEFAULT NULL,
  lastPrice         BIGINT                            DEFAULT 0,
  initialPrice      BIGINT       NOT NULL,
  status            TINYINT                           DEFAULT 0,
  createdAt         TIMESTAMP                         DEFAULT CURRENT_TIMESTAMP,
  updatedAt         TIMESTAMP                         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY fundNameUniqueKey (`fundName`)
);

CREATE TABLE IF NOT EXISTS cfs_task8.FundPriceHistory (
  id        INT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
  fundId    INT       NOT NULL,
  price     INT                            DEFAULT 0,
  priceDate TIMESTAMP NULL,
  status    TINYINT                        DEFAULT 0,
  createdAt TIMESTAMP                      DEFAULT CURRENT_TIMESTAMP,
  updatedAt TIMESTAMP                      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (fundId) REFERENCES cfs_task8.Fund (id)
);

CREATE TABLE IF NOT EXISTS cfs_task8.Position (
  id         INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  customerId INT NOT NULL,
  fundId     INT NOT NULL,
  shares     BIGINT                   DEFAULT 0,
  status     TINYINT                  DEFAULT 0,
  createdAt  TIMESTAMP                DEFAULT CURRENT_TIMESTAMP,
  updatedAt  TIMESTAMP                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (fundId) REFERENCES cfs_task8.Fund (id),
  FOREIGN KEY (customerId) REFERENCES cfs_task8.Customer (id)
);

CREATE TABLE IF NOT EXISTS cfs_task8.Transition (
  id          INT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
  customerId  INT       NOT NULL,
  fundId      INT       NULL,
  positionId  INT       NULL,
  executeDate TIMESTAMP NULL,
  shares      BIGINT                         DEFAULT 0,
  price       BIGINT                         DEFAULT 0,
  type        TINYINT                        DEFAULT 0,
  amount      BIGINT                         DEFAULT 0,
  status      TINYINT                        DEFAULT 0,
  createdAt   TIMESTAMP                      DEFAULT CURRENT_TIMESTAMP,
  updatedAt   TIMESTAMP                      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (customerId) REFERENCES cfs_task8.Customer (id)
);


CREATE OR REPLACE VIEW cfs_task8.TransitionView AS
  SELECT
    t.id,
    t.customerId,
    t.fundId,
    t.positionId,
    t.executeDate,
    t.shares,
    t.price,
    t.type,
    t.amount,
    t.status,
    t.createdAt,
    t.updatedAt,
    f.fundName,
    CONCAT(c.firstName, " ", c.lastName) AS displayName
  FROM cfs_task8.Transition AS t
    LEFT JOIN cfs_task8.Fund AS f ON f.id = t.fundId
    LEFT JOIN cfs_task8.Customer AS c ON c.id = t.customerId;

CREATE OR REPLACE VIEW cfs_task8.FundPriceHistoryView AS
  SELECT
    fph.id,
    fph.fundId,
    fph.price,
    fph.priceDate,
    f.fundName
  FROM cfs_task8.FundPriceHistory AS fph
    LEFT JOIN cfs_task8.Fund AS f ON f.id = fph.fundId;


CREATE OR REPLACE VIEW cfs_task8.PositionView AS
  SELECT
    p.id,
    p.customerId,
    p.fundId,
    p.shares,
    p.status,
    p.createdAt,
    p.updatedAt,
    f.fundName,
    f.symbol,
    f.lastPrice,
    t.amount,
    c.userName
  FROM cfs_task8.Position AS p, cfs_task8.Fund AS f, cfs_task8.Transition AS t, cfs_task8.Customer AS c
  WHERE f.id = p.fundId AND
        t.positionId = p.id AND
        c.id = p.customerId;

#There should not be any comment in this file unless it is the end of the file.
