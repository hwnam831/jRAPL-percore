#Usage: clusterexp.sh [policy] [pernodelimit]
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_localcontrol.sh "$1" 120 "$2"" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_localcontrol.sh "$1" 120 "$2"" &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_localcontrol.sh "$1" 120 "$2"" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_localcontrol.sh "$1" 120 "$2"" &
cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c clusterexp5.json &
sleep 180