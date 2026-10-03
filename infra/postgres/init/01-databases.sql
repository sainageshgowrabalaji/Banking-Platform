-- One database per service. A service reads and writes only its own database, and learns about the
-- others through their APIs and events. This runs once, when the Postgres volume is first created.
CREATE DATABASE ledger;
CREATE DATABASE payments;
CREATE DATABASE notification;
CREATE DATABASE compliance;
CREATE DATABASE trading;
CREATE DATABASE positions;
CREATE DATABASE posttrade;
CREATE DATABASE wealth;
