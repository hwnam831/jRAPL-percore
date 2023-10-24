#Usage: clusterexp.sh [config]
PERNODECAP=100
python3 ClusterController.py --policy sin --duration 120 > ${1}_sin.csv&
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 90 "$PERNODECAP" "$1"_sin" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 90 "$PERNODECAP" "$1"_sin" &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 90 "$PERNODECAP" "$1"_sin" &
cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c $1.json &
sleep 180