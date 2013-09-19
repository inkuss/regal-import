#! /bin/bash
type=$1
server=$2
pidlist=`./list.sh $type $server`
for i in $pidlist;do TT=`curl -s http://${server}/fedora/objects/$i/datastreams/DC/content|grep -o "HT[^<]*\|TT[^<]*"`; echo $i,$TT;done >tmp

sort tmp |uniq
rm tmp
