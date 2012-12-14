#!/bin/sh
PID_FILE=/var/run/yamcs-server.pid
YAMCS_DIR="/opt/yamcs/"

function is_running {
    pid=$1
    return $((1-`ps ww -p $pid -o args= | grep -c "yamcs-server.sh\|java.*yamcs.YamcsServer"`))
}
if [ -s $PID_FILE ] ; then
    pid=`cat $PID_FILE`
    if is_running $pid ; then
       echo "Yamcs server is running pid=$pid, sending signal 3 to dump java stack trace"
       kill -3 $pid
       sleep 3
    else
       echo  "Yamcs server not running"
    fi
else
    echo "Yamcs server not running ($PID_FILE doesn't exist)"
fi

f=/tmp/yamcs-logs-`date +%FT%H%M%S`.tgz
echo "collecting all files from $YAMCS_DIR/log newer than 7 days into $f" 
cd $YAMCS_DIR
tar czvf $f `find log -mtime -7`

echo "logs collected in $f"
