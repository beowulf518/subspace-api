Terrella API
=================================

### Quick start:

1. Install sbt
```
  brew install sbt@1 // macOS
  sudo apt-get install sbt // debian / ubuntu
```

2. Download gitblit from http://gitblit.com/

3. Run MySQL via docker and wait for it to start:
```
docker run --rm --name terrella -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_USER=subspace -e MYSQL_PASSWORD=subspace -e MYSQL_DATABASE=terrella -p 3306:3306 mysql:latest
```

Wait for it to start

4. Start api server
```
DB_URL=jdbc:mysql://localhost:3306/terrella?useSSL=false GITBLIT_PATH=[path to gitblit] sbt run
```

### Alternative start:

Set Up:

1. Create MySQL database: `terrella` and allow grant to database user

1. Initialize environment variables for database name, username and user password: `DB_NAME`, `DB_USER_NAME` and `DB_USER_PASS`


Run Project:

1. `$ activator`

1. Update Dependencies: `[terrella-api]$ update`

1. Run: `[terrella-api]$ run`

1. Browse: `http://localhost:9000`


Gitblit Installation:

1. Install Gitblit at http://gitblit.com
2. Change the configuration file at /gitblitInstall/data/default.properties from conf file at /terrella-api/gitblit/default.properties
3. Copy /terrella-api/gitblit/{post-commit-stash.groovy, pre-commit-stash.groovy} into /gitblitInstall/data/groovy
4. Change application.conf gitblit configuration
5. Run gitblit, open http://localhost:8443
