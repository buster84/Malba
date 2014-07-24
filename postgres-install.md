rpm -ivh http://yum.postgresql.org/9.3/redhat/rhel-6.4-x86_64/pgdg-centos93-9.3-1.noarch.rpm

sudo yum groupinstall "PostgreSQL Database Server 9.3 PGDG"

sudo service postgresql-9.3 initdb

sudo service postgresql-9.3

sudo su - postgres

/usr/pgsql-9.3/bin/createdb akka-persistant-db

/usr/pgsql-9.3/bin/psql akka-persistant-db

`````
CREATE TABLE IF NOT EXISTS public.journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  marker VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  created TIMESTAMP NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  snapshot TEXT NOT NULL,
  created BIGINT NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);

CREATE USER shanon WITH PASSWORD 'shanon';
GRANT ALL ON journal TO shanon;
GRANT ALL ON snapshot TO shanon;
`````

sudo vim /var/lib/pgsql/9.3/data/pg_hba.conf
`````
# "local" is for Unix domain socket connections only
local   all             all                                     trust
# IPv4 local connections:
host    all             all             127.0.0.1/32            trust
# IPv6 local connections:
host    all             all             ::1/128                 trust
`````

