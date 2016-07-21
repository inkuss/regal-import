#!/bin/bash

cp .oaitimestamp .oaitimestamp.`date +"%Y%m%d"`

####
# UPDT, SYNC, DWNL, PIDL
##

# man braucht nicht alle Parameter; die Liste wird nicht benutzt, geht über den Zeitstempel
java -jar -Xms512m -Xmx512m target/edowebsync.jar --mode DWNL -list ~/local/opt/regal/sync/pidlist.txt --user edoweb-admin --password ***** --dtl http://klio.hbz-nrw.de:1801 --cache ~/local/opt/regal/edowebbase --oai http://klio.hbz-nrw.de:1801/edowebOAI/ --set doc-type:website --timestamp .oaitimestamp --fedoraBase http://localhost.de:8080/fedora --host localhost:9000 --namespace edoweb >> ~/local/opt/regal/regal-import/logs/edosync.`date +"%Y%m%d"`.log 2>&1
