#! /bin/bash

source pidlist.sh

function index()
{
type=$1
user=$2
password=$3
for i in `pidlist $type localhost`
do
curl -u ${user}:${password} -XPOST http://localhost/utils/index/$i
done
}


index $1 $2 $3