#!/bin/bash

azkaban_dir=$(dirname $0)/..

# Specifies location of azkaban.properties, log4j.properties files
# Change if necessary
conf=$azkaban_dir/conf
azkaban_dir=$(pwd)
function SearchCLib()
{
    cd $1
    dirlist=$(ls)
    for dirname in $dirlist
    do
        if [[ -d "$dirname" ]];then
            hadoopClassPath=${hadoopClassPath}":"$(pwd)"/"${dirname}"/*"
            cd $dirname
            SearchCLib $(pwd)
            cd ..
        fi;
    done;
}

if [[ -z "$tmpdir" ]]; then
tmpdir=/tmp
fi

for file in $azkaban_dir/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

for file in $azkaban_dir/extlib/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

for file in $azkaban_dir/plugins/*/*.jar;
do
  CLASSPATH=$CLASSPATH:$file
done

if [ "$HADOOP_HOME" != "" ]; then
        echo "Using Hadoop from $HADOOP_HOME"
        beginDir=$HADOOP_HOME'/share/hadoop'
        hadoopClassPath=$beginDir'/*'
        SearchCLib $beginDir
        cd $azkaban_dir
        CLASSPATH=$CLASSPATH:$HADOOP_HOME/etc/hadoop:$hadoopClassPath
        JAVA_LIB_PATH="-Djava.library.path=$HADOOP_HOME/lib/native/Linux-amd64-64"
else
        echo "Error: HADOOP_HOME is not set. Hadoop job types will not run properly."
fi

if [ "$HIVE_HOME" != "" ]; then
        echo "Using Hive from $HIVE_HOME"
        CLASSPATH=$CLASSPATH:$HIVE_HOME/conf:$HIVE_HOME/lib/*
fi

echo $azkaban_dir;
echo $CLASSPATH;

executorport=`cat $conf/azkaban.properties | grep executor.port | cut -d = -f 2`
echo "Starting AzkabanExecutorServer on port $executorport ..."
serverpath=`pwd`

if [ -z $AZKABAN_OPTS ]; then
  AZKABAN_OPTS="-Xmx3G"
fi
# Set the log4j configuration file
if [ -f $conf/log4j.properties ]; then
  AZKABAN_OPTS="$AZKABAN_OPTS -Dlog4j.configuration=file:$conf/log4j.properties"
fi
AZKABAN_OPTS="$AZKABAN_OPTS -server -Dcom.sun.management.jmxremote -Djava.io.tmpdir=$tmpdir -Dexecutorport=$executorport -Dserverpath=$serverpath -Dlog4j.log.dir=$azkaban_dir/logs"

java $AZKABAN_OPTS $JAVA_LIB_PATH -cp $CLASSPATH azkaban.execapp.AzkabanExecutorServer -conf $conf $@ &

echo $! > $azkaban_dir/currentpid

