- Setup database follow this: https://postgrespro.com/community/demodb
- Unzip to get file demo-xxx.sql
- Install dataset
- Copy dataset into container:
  $ docker cp ./demo-big-en-20170815.sql postgres:/demo.sql
- Access docker container
  $ docker exec -it postgres bash
- Run sql script to init database
  $ psql -U user -f demo.sql

